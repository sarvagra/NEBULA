import { io as ClientSocket, Socket } from 'socket.io-client';
import http from 'http';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { config } from '../config/env';
import { createApp } from '../app';
import { initSocketServer } from '../realtime/socketServer';
import { UserModel } from '../models/User';

/**
 * Playback resilience audit.
 *
 * This harness measures what the UI is supposed to guarantee instead of asserting
 * that the code "looks right": the error a late joiner lands with, the error after
 * a dropped and restored socket, and who owns the room once the host disappears.
 *
 * The drift figures are printed so a regression shows up as a number moving, not
 * only as a pass turning into a fail.
 */

let apiBase = '';

/** The inactivity grace is shortened for this run so host hand-off can be observed. */
const HANDOFF_GRACE_MS = 1_500;

function signedHeaders(method: string, path: string): Record<string, string> {
  const now = Date.now();
  const normalizedPath = path.replace(/^\/api\/v1/, '').split('?')[0] || '/';
  const signature = crypto
    .createHmac('sha256', config.clientAttestation.secret)
    .update(`${now}:${method.toUpperCase()}:${normalizedPath}`)
    .digest('hex');
  return {
    'X-Nebula-App-Signature': signature,
    'X-Nebula-Timestamp': now.toString(),
    'X-Nebula-Client': 'com.nebula.app',
  };
}

async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method || 'GET').toUpperCase();
  return fetch(`${apiBase}${path}`, {
    ...init,
    headers: { ...signedHeaders(method, path), ...((init.headers as Record<string, string>) || {}) },
  });
}

async function loginUser(email: string, username: string, displayName: string) {
  const user = (await UserModel.findOne({ username })) || await UserModel.create({
    email,
    displayName,
    username,
    avatarLetter: displayName.charAt(0).toUpperCase() || 'N',
    tier: 'free',
    status: 'dynamic',
    presenceStatus: 'online',
    bio: '',
    isOnboardingCompleted: true,
  });
  const token = jwt.sign(
    { id: user._id.toString(), username: user.username, displayName: user.displayName, tier: user.tier, role: user.role },
    config.auth.jwtSecret,
    { expiresIn: '1h' },
  );
  return { token, user: { id: user._id.toString(), username: user.username } };
}

async function createRoom(token: string, name: string): Promise<any> {
  const res = await apiFetch('/api/v1/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      name,
      privacy: 'public',
      activity: 'watch',
      mediaTitle: 'Drift Measurement Clip',
      mediaSubtitle: 'Synthetic',
      mediaUrl: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ',
    }),
  });
  const data = await res.json() as any;
  if (!data.success) throw new Error(`Room creation failed: ${JSON.stringify(data)}`);
  return data.data;
}

async function joinRoom(token: string, roomId: string): Promise<void> {
  const res = await apiFetch(`/api/v1/rooms/${roomId}/join`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Join failed: ${await res.text()}`);
}

function connectSocket(token: string): Promise<Socket> {
  return new Promise((resolve, reject) => {
    const socket = ClientSocket(apiBase, { auth: { token }, transports: ['websocket'] });
    socket.on('connect', () => resolve(socket));
    socket.on('connect_error', reject);
  });
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Drift budget from the WebView shim: correction is not applied inside this band. */
const DRIFT_DEAD_BAND_MS = 650;

async function runPlaybackResilienceTest() {
  console.log('====================================================');
  console.log('📐 PLAYBACK RESILIENCE AUDIT (drift + host hand-off)');
  console.log('====================================================');

  let passedTests = 0;
  const testDatabase = await MongoMemoryServer.create();
  let server: http.Server | undefined;
  let hostSocket: Socket | undefined;
  let viewerSocket: Socket | undefined;

  try {
    process.env.MONGODB_URI = testDatabase.getUri();
    process.env.MONGODB_DATABASE = 'nebula_resilience';
    await mongoose.connect(testDatabase.getUri(), { dbName: 'nebula_resilience' });

    server = http.createServer(createApp());
    initSocketServer(server);
    await new Promise<void>((resolve) => server!.listen(0, '127.0.0.1', () => resolve()));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Server did not bind a port');
    apiBase = `http://127.0.0.1:${address.port}`;

    const host = await loginUser('drift_host@nebula.io', 'drift_host', 'Drift Host');
    const viewer = await loginUser('drift_viewer@nebula.io', 'drift_viewer', 'Drift Viewer');

    const room = await createRoom(host.token, 'Drift Measurement Room');
    await joinRoom(viewer.token, room.id);

    hostSocket = await connectSocket(host.token);
    viewerSocket = await connectSocket(viewer.token);
    hostSocket.emit('room:join', { roomId: room.id });
    await sleep(200);

    /** Latest position the host has actually reached, in host-local milliseconds. */
    let hostPlayheadMs = 0;
    let hostPlaying = true;
    const sampleTimer = setInterval(() => {
      hostPlayheadMs += 200;
      hostSocket?.emit('media:sync', {
        roomId: room.id,
        positionMs: hostPlayheadMs,
        isPlaying: hostPlaying,
        timestamp: Date.now(),
      });
    }, 200);
    sampleTimer.unref();

    // Let the room play for a moment before anyone joins late.
    await sleep(1_400);

    const lateJoin = await new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Late joiner received no playback snapshot')), 4000);
      viewerSocket!.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        resolve({ payload, receivedAt: Date.now() });
      });
      viewerSocket!.emit('room:join', { roomId: room.id });
    });

    // A listener projects the playhead from the snapshot plus elapsed local time.
    // Comparing that with where the host has actually got to is the drift a viewer sees.
    const projected = lateJoin.payload.positionMs + (lateJoin.receivedAt - lateJoin.payload.timestamp);
    const observedHostPlayhead = hostPlayheadMs + (Date.now() - lateJoin.receivedAt);
    const lateJoinDrift = Math.abs(observedHostPlayhead - projected);
    console.log(`  ℹ️  late-join drift: ${lateJoinDrift.toFixed(0)} ms (budget ${DRIFT_DEAD_BAND_MS} ms)`);
    if (lateJoin.payload.mediaState?.title !== 'Drift Measurement Clip') {
      throw new Error(`Late joiner did not receive the current media: ${JSON.stringify(lateJoin.payload.mediaState)}`);
    }
    if (lateJoinDrift > DRIFT_DEAD_BAND_MS) {
      throw new Error(`Late join landed outside the drift dead-band: ${lateJoinDrift.toFixed(0)} ms`);
    }
    console.log('  ✅ PASS: A late joiner lands on the host playhead inside the drift dead-band');
    passedTests++;

    // Drop the viewer's socket while the room keeps playing, then restore it.
    viewerSocket.disconnect();
    await sleep(1_200);
    viewerSocket = await connectSocket(viewer.token);
    const reconnect = await new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Reconnected viewer received no playback snapshot')), 4000);
      viewerSocket!.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        resolve({ payload, receivedAt: Date.now() });
      });
      viewerSocket!.emit('room:join', { roomId: room.id });
    });
    const reconnectProjected = reconnect.payload.positionMs + (reconnect.receivedAt - reconnect.payload.timestamp);
    const reconnectDrift = Math.abs(hostPlayheadMs + (Date.now() - reconnect.receivedAt) - reconnectProjected);
    console.log(`  ℹ️  reconnect drift: ${reconnectDrift.toFixed(0)} ms (budget ${DRIFT_DEAD_BAND_MS} ms)`);
    if (reconnectDrift > DRIFT_DEAD_BAND_MS) {
      throw new Error(`Reconnect resynchronisation was outside the dead-band: ${reconnectDrift.toFixed(0)} ms`);
    }
    console.log('  ✅ PASS: A reconnecting viewer resynchronises inside the drift dead-band');
    passedTests++;

    // A viewer must not be able to move the room, before or after reconnecting.
    let viewerMovedRoom = false;
    const viewerMove = (payload: any) => { if (payload?.positionMs === 999_000) viewerMovedRoom = true; };
    viewerSocket.on('media:seek', viewerMove);
    viewerSocket.emit('media:seek', { roomId: room.id, positionMs: 999_000, version: 0 });
    await sleep(500);
    viewerSocket.off('media:seek', viewerMove);
    if (viewerMovedRoom) throw new Error('A non-host listener moved the room playhead');
    console.log('  ✅ PASS: A listener still cannot move the room after reconnecting');
    passedTests++;

    clearInterval(sampleTimer);

    // ---------------------------------------------------------------------
    // Host hand-off: the host's socket disappears without leaving the room.
    // ---------------------------------------------------------------------
    await sleep(HANDOFF_GRACE_MS + 500);
    hostSocket.disconnect();
    const handoff = new Promise<string>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('No host change after the host socket dropped')), 8000);
      viewerSocket!.once('room:host_changed', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload?.newHostId || '');
      });
    });
    const newHostId = await handoff;
    if (newHostId !== viewer.user.id) {
      throw new Error(`Host hand-off went to ${newHostId} instead of the remaining member`);
    }
    const afterHandoff = await (await apiFetch(`/api/v1/rooms/${room.id}`, {
      headers: { Authorization: `Bearer ${viewer.token}` },
    })).json() as any;
    if (afterHandoff.data?.hostId !== viewer.user.id) {
      throw new Error('Persisted room host did not match the hand-off broadcast');
    }
    if (!afterHandoff.data?.participants?.some((p: any) => p.userId === viewer.user.id)) {
      throw new Error('The remaining member was dropped from the room during hand-off');
    }
    console.log('  ✅ PASS: The room and its remaining member survive the host disappearing, and ownership transfers');
    passedTests++;

    // The new host can actually control playback.
    const newHostControl = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('New host could not control playback')), 4000);
      const check = async () => {
        const state = await (await apiFetch(`/api/v1/rooms/${room.id}`, {
          headers: { Authorization: `Bearer ${viewer.token}` },
        })).json() as any;
        if (state.data?.mediaState?.isPlaying === false) { clearTimeout(timeout); resolve(); }
      };
      viewerSocket!.on('media:pause', check);
      viewerSocket!.emit('media:pause', { roomId: room.id, positionMs: 5_000 });
      setTimeout(check, 400);
    });
    await newHostControl;
    console.log('  ✅ PASS: The promoted member can control playback without a reconnect');
    passedTests++;

    await apiFetch(`/api/v1/rooms/${room.id}/leave`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${viewer.token}` },
    });

    console.log('====================================================');
    console.log(`🎉 PLAYBACK RESILIENCE AUDIT COMPLETE: ${passedTests} assertions passed`);
    console.log('====================================================');
  } catch (err: any) {
    console.error('❌ PLAYBACK RESILIENCE AUDIT FAILED:', err.message);
    process.exitCode = 1;
  } finally {
    hostSocket?.disconnect();
    viewerSocket?.disconnect();
    await sleep(500);
    if (server) await new Promise<void>((resolve) => server!.close(() => resolve()));
    await mongoose.disconnect();
    await testDatabase.stop();
  }
}

runPlaybackResilienceTest();
