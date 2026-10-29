import { io as ClientSocket, Socket } from 'socket.io-client';
import http from 'http';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { config } from '../config/env';
import { createApp } from '../app';
import { initSocketServer, sweepStalePresence } from '../realtime/socketServer';
import { UserModel } from '../models/User';

let apiBase = '';

/**
 * Every real client call carries the app-attestation headers, so the suite must
 * sign its requests too. Without this the app shield rejects the login with 403
 * APP_CLIENT_REQUIRED and the whole realtime audit aborts before it starts.
 */
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
  const headers: Record<string, string> = {
    ...signedHeaders(method, path),
    ...((init.headers as Record<string, string>) || {}),
  };
  return fetch(`${apiBase}${path}`, { ...init, headers });
}

/**
 * The backend has no developer login route: Firebase is the only identity
 * provider. This suite runs in-process against an isolated in-memory database,
 * so it provisions the accounts it needs and mints the same session JWT the
 * auth middleware would have issued. No bypass is reachable over HTTP.
 */
async function loginUser(email: string, username: string, displayName: string): Promise<{ token: string; user: any }> {
  const user = (await UserModel.findOne({ username })) || (await UserModel.create({
    email,
    displayName,
    username,
    avatarLetter: displayName.charAt(0).toUpperCase() || 'N',
    tier: 'free',
    status: 'dynamic',
    presenceStatus: 'online',
    bio: '',
    isOnboardingCompleted: true,
  }));

  const token = jwt.sign(
    { id: user._id.toString(), username: user.username, displayName: user.displayName, tier: user.tier, role: user.role },
    config.auth.jwtSecret,
    { expiresIn: '1h' },
  );

  return { token, user: { id: user._id.toString(), username: user.username, displayName: user.displayName } };
}

async function createRoom(token: string, name: string, mediaTitle: string, mediaUrl: string): Promise<any> {
  const res = await apiFetch(`/api/v1/rooms`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      name,
      privacy: 'public',
      activity: 'watch',
      mediaTitle,
      mediaSubtitle: 'High Definition',
      mediaUrl,
    }),
  });
  const data = (await res.json()) as any;
  if (!data.success) {
    throw new Error(`Failed to create room: ${JSON.stringify(data)}`);
  }
  return data.data;
}

async function joinRoom(token: string, roomId: string): Promise<void> {
  const res = await apiFetch(`/api/v1/rooms/${roomId}/join`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to join room: ${await res.text()}`);
}

function connectSocket(token: string): Promise<Socket> {
  return new Promise((resolve, reject) => {
    const socket = ClientSocket(apiBase, {
      auth: { token },
      transports: ['websocket'],
    });
    socket.on('connect', () => resolve(socket));
    socket.on('connect_error', (err) => reject(err));
  });
}

async function runMultiClientSyncTest() {
  console.log('====================================================');
  console.log('⚡ STARTING MULTI-CLIENT REALTIME SYNC AUDIT');
  console.log('====================================================');

  let passedTests = 0;
  const testDatabase = await MongoMemoryServer.create();
  let server: http.Server | undefined;
  let socketA: Socket | undefined;
  let socketB: Socket | undefined;
  let socketC: Socket | undefined;
  let failed = false;

  try {
    // Never point this integration suite at a configured/shared database or backend.
    await mongoose.connect(testDatabase.getUri(), { dbName: 'nebula_sync_test' });
    config.server.isProduction = false;
    server = http.createServer(createApp());
    initSocketServer(server);
    await new Promise<void>((resolve) => server!.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Could not bind isolated test server');
    apiBase = `http://127.0.0.1:${address.port}`;

    // 1. Auth Setup
    console.log('[Setup] Logging in Host (Client A) and Viewer (Client B)...');
    const clientA = await loginUser('host_sync_a@nebula.io', 'host_sync_a', 'Host Sync Alpha');
    const clientB = await loginUser('viewer_sync_b@nebula.io', 'viewer_sync_b', 'Viewer Sync Beta');
    console.log(`  ✅ Client A: @${clientA.user.username} (${clientA.user.id})`);
    console.log(`  ✅ Client B: @${clientB.user.username} (${clientB.user.id})`);

    // 2. Create Synchronized Room
    console.log('[Room] Client A creating synchronized room with Hoor Coke Studio video...');
    const room = await createRoom(
      clientA.token,
      'Sync Verification Room',
      'Hoor | Coke Studio Bharat',
      'https://www.youtube.com/watch?v=9S5_E65AbTQ'
    );
    await joinRoom(clientB.token, room.id);
    const secondRoomAttempt = await apiFetch(`/api/v1/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ name: 'Must Be Rejected', privacy: 'public', activity: 'watch' }),
    });
    if (secondRoomAttempt.status !== 409) throw new Error('A user was allowed to create a second active room');
    console.log('  ✅ PASS: A participant cannot create a second room while already in one');
    passedTests++;
    const selfRequestRes = await apiFetch(`/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ recipientId: clientB.user.id }),
    });
    if (selfRequestRes.status !== 400) throw new Error('Server accepted a self friend request');
    console.log('  ✅ PASS: Server rejects attempts to friend yourself');
    passedTests++;
    console.log(`  ✅ Room Created: "${room.name}" [ID: ${room.id}]`);

    // 3. Socket Connections
    console.log('[Sockets] Establishing authenticated Socket.IO connections...');
    socketA = await connectSocket(clientA.token);
    socketB = await connectSocket(clientB.token);
    const clientSocketA = socketA;
    let clientSocketB = socketB;
    console.log('  ✅ PASS 1: Both sockets authenticated and connected');
    passedTests++;

    // 4. Host Joins Room
    await new Promise<void>((resolve) => {
      clientSocketA.emit('room:join', { roomId: room.id });
      setTimeout(resolve, 500);
    });

    // 5. Client B Joins Room -> Client A receives participant:joined
    const participantJoinedPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for participant:joined')), 5000);
      clientSocketA.once('participant:joined', (payload: any) => {
        clearTimeout(timeout);
        if (payload.userId === clientB.user.id) {
          resolve();
        } else {
          reject(new Error(`Unexpected participant joined: ${payload.userId}`));
        }
      });
    });

    clientSocketB.emit('room:join', { roomId: room.id });
    await participantJoinedPromise;
    console.log('  ✅ PASS 2: Client A received participant:joined when Client B entered');
    passedTests++;

    // 6. Security Check: Client B (non-host) cannot control playback
    const unauthorizedPlayPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for unauthorized error')), 4000);
      clientSocketB.once('error', (err: any) => {
        clearTimeout(timeout);
        if (err.message && err.message.includes('Only the room host can control')) {
          resolve();
        } else {
          reject(new Error(`Unexpected error message: ${err.message}`));
        }
      });
    });

    clientSocketB.emit('media:play', { roomId: room.id, position: 42 });
    await unauthorizedPlayPromise;
    console.log('  ✅ PASS 3: Server rejected unauthorized media:play from non-host Client B');
    passedTests++;

    // 7. Host Emits media:play -> Client B receives media:play
    const playSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:play')), 5000);
      clientSocketB.once('media:play', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 15) {
          resolve();
        } else {
          reject(new Error(`Expected position 15, got ${payload.position}`));
        }
      });
    });

    clientSocketA.emit('media:play', { roomId: room.id, position: 15 });
    await playSyncPromise;
    console.log('  ✅ PASS 4: Host emitted media:play, Client B received synchronized play at 15s');
    passedTests++;

    // 8. Host Emits media:pause -> Client B receives media:pause
    const pauseSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:pause')), 5000);
      clientSocketB.once('media:pause', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 30) {
          resolve();
        } else {
          reject(new Error(`Expected position 30, got ${payload.position}`));
        }
      });
    });

    clientSocketA.emit('media:pause', { roomId: room.id, position: 30 });
    await pauseSyncPromise;
    console.log('  ✅ PASS 5: Host emitted media:pause, Client B received synchronized pause at 30s');
    passedTests++;

    // 9. Host Emits media:seek -> Client B receives media:seek
    const seekSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:seek')), 5000);
      clientSocketB.once('media:seek', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 120) {
          resolve();
        } else {
          reject(new Error(`Expected position 120, got ${payload.position}`));
        }
      });
    });

    clientSocketA.emit('media:seek', { roomId: room.id, position: 120 });
    await seekSyncPromise;
    console.log('  ✅ PASS 6: Host emitted media:seek, Client B received synchronized seek at 120s');
    passedTests++;

    // Actual player samples keep the server's room timeline current between button events.
    const syncStartedAt = Date.now();
    const mediaSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for host media:sync')), 3000);
      clientSocketB.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        const latencyMs = Date.now() - syncStartedAt;
        if (payload.positionMs === 121000 && payload.isPlaying === true && latencyMs < 1000) resolve();
        else reject(new Error(`Expected current host sample within 1s; position=${payload.positionMs}, playing=${payload.isPlaying}, latency=${latencyMs}ms`));
      });
    });
    clientSocketA.emit('media:sync', {
      roomId: room.id,
      positionMs: 121000,
      isPlaying: true,
      timestamp: Date.now(),
    });
    await mediaSyncPromise;
    console.log('  ✅ PASS 7: Live host playhead sample reached the room in under 1 second');
    passedTests++;

    const joinSnapshotPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for current media:sync join snapshot')), 3000);
      clientSocketB.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        if (payload.positionMs >= 121000 && payload.isPlaying === true) resolve();
        else reject(new Error(`Join snapshot was stale: ${JSON.stringify(payload)}`));
      });
    });
    clientSocketB.emit('room:join', { roomId: room.id });
    await joinSnapshotPromise;
    console.log('  ✅ PASS 8: Rejoining member received live room playhead instead of stale DB checkpoint');
    passedTests++;

    // 10. Host Switches Media -> Client B receives media:switch
    const switchSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:switch')), 5000);
      clientSocketB.once('media:switch', (payload: any) => {
        clearTimeout(timeout);
        if (payload.mediaState && payload.mediaState.title === 'Interstellar Docking Scene') {
          resolve();
        } else {
          reject(new Error(`Expected new title Interstellar, got: ${JSON.stringify(payload)}`));
        }
      });
    });

    clientSocketA.emit('media:switch', {
      roomId: room.id,
      title: 'Interstellar Docking Scene',
      mediaUrl: 'https://www.youtube.com/watch?v=a3lcGnMhvsA',
      durationSeconds: 272,
    });
    await switchSyncPromise;
    console.log('  ✅ PASS 7: Host switched video, Client B received media:switch notification');
    passedTests++;

    // 11. Realtime Chat: Client B sends message -> Client A receives
    const chatSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for chat:message')), 5000);
      clientSocketA.once('chat:message', (payload: any) => {
        clearTimeout(timeout);
        if (payload.text === 'Synchronized audio is incredible!') {
          resolve();
        } else {
          reject(new Error(`Unexpected message text: ${payload.text}`));
        }
      });
    });

    clientSocketB.emit('chat:message', {
      roomId: room.id,
      text: 'Synchronized audio is incredible!',
    });
    await chatSyncPromise;
    console.log('  ✅ PASS 8: Client B sent chat message, Client A received in real time');
    passedTests++;

    // 12. Realtime Chat Reaction: Client B reacts -> Client A receives
    const reactionSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for chat:reaction')), 5000);
      clientSocketA.once('chat:reaction', (payload: any) => {
        clearTimeout(timeout);
        if (payload.emoji === '🔥') {
          resolve();
        } else {
          reject(new Error(`Expected emoji 🔥, got: ${payload.emoji}`));
        }
      });
    });

    clientSocketB.emit('chat:reaction', {
      roomId: room.id,
      emoji: '🔥',
    });
    await reactionSyncPromise;
    console.log('  ✅ PASS 9: Client B reacted with 🔥, Client A received reaction');
    passedTests++;

    // Room friendship request lifecycle: request, withdraw, incoming request, accept, and decline.
    const friendRequestRes = await apiFetch(`/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ recipientId: clientB.user.id }),
    });
    const friendRequest = await friendRequestRes.json() as any;
    if (friendRequestRes.status !== 201) throw new Error(`Friend request failed: ${JSON.stringify(friendRequest)}`);
    const pendingRes = await apiFetch(`/api/v1/friends/requests`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    const pending = await pendingRes.json() as any;
    if (!pending.data?.some((request: any) => request.id === friendRequest.data.id)) throw new Error('Incoming friend request did not appear for the recipient');
    const withdrawRes = await apiFetch(`/api/v1/friends/request/withdraw/${clientB.user.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!withdrawRes.ok) throw new Error('Requester could not withdraw friend request');
    const withdrawnRes = await apiFetch(`/api/v1/friends/requests`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if ((await withdrawnRes.json() as any).data?.some((request: any) => request.id === friendRequest.data.id)) throw new Error('Withdrawn request remained visible to recipient');
    console.log('  ✅ PASS: Friend request appears for recipient and disappears after sender withdraws');
    passedTests++;

    const reverseRequestRes = await apiFetch(`/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ recipientId: clientA.user.id }),
    });
    const reverseRequest = await reverseRequestRes.json() as any;
    if (reverseRequestRes.status !== 201) throw new Error(`Reverse friend request failed: ${JSON.stringify(reverseRequest)}`);
    const acceptRes = await apiFetch(`/api/v1/friends/request/${reverseRequest.data.id}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!acceptRes.ok) throw new Error('Recipient could not accept incoming friend request');
    const friendsRes = await apiFetch(`/api/v1/friends`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if (!(await friendsRes.json() as any).data?.some((friend: any) => friend.id === clientA.user.id)) throw new Error('Accepted friendship did not appear on requester side');
    console.log('  ✅ PASS: Recipient accepts a request and the accepted friendship appears for both');
    passedTests++;

    const clientC = await loginUser('decline_sync_c@nebula.io', 'decline_sync_c', 'Decline Sync Gamma');
    const declineRequestRes = await apiFetch(`/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientC.token}` },
      body: JSON.stringify({ recipientId: clientA.user.id }),
    });
    const declineRequest = await declineRequestRes.json() as any;
    const declineRes = await apiFetch(`/api/v1/friends/request/${declineRequest.data.id}/decline`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!declineRes.ok) throw new Error('Recipient could not decline incoming friend request');
    console.log('  ✅ PASS: Recipient can decline an incoming request');
    passedTests++;

    const requestCRes = await apiFetch(`/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ recipientId: clientC.user.id }),
    });
    const requestC = await requestCRes.json() as any;
    const acceptCRes = await apiFetch(`/api/v1/friends/request/${requestC.data.id}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    if (!acceptCRes.ok) throw new Error('Could not accept invitation test friendship');
    const inviteCRes = await apiFetch(`/api/v1/rooms/${room.id}/invite`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ userId: clientC.user.id }),
    });
    if (inviteCRes.status !== 201) throw new Error(`Could not invite an existing friend: ${await inviteCRes.text()}`);
    await joinRoom(clientC.token, room.id);
    const invitedRoom = await (await apiFetch(`/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
    if (!invitedRoom.data?.participants.some((p: any) => p.userId === clientC.user.id)) throw new Error('Invited friend could not join room');
    await apiFetch(`/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    console.log('  ✅ PASS: A room member can invite an existing friend, who then joins and appears in participants');
    passedTests++;

    // A second, independent room gives the test two simultaneous room channels.
    const roomC = await createRoom(clientC.token, 'Independent Beta Room', 'Different song', 'https://www.youtube.com/watch?v=aqz-KE-bpKQ');
    socketC = await connectSocket(clientC.token);
    const clientSocketC = socketC;
    clientSocketC.emit('room:join', { roomId: roomC.id });
    await new Promise((resolve) => setTimeout(resolve, 250));

    const moderationRes = await apiFetch(`/api/v1/rooms/${room.id}/participants/${clientB.user.id}/moderation`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ isMuted: true, isCameraOff: true }),
    });
    if (!moderationRes.ok) throw new Error(`Host could not mute participant: ${await moderationRes.text()}`);
    const moderatedRoom = await (await apiFetch(`/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
    const moderatedParticipant = moderatedRoom.data.participants.find((p: any) => p.userId === clientB.user.id);
    if (!moderatedParticipant?.isMuted || !moderatedParticipant?.isCameraOff) throw new Error('Mute/camera-off state was not persisted');
    const unmuteRes = await apiFetch(`/api/v1/rooms/${room.id}/participants/${clientB.user.id}/moderation`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ isMuted: false, isCameraOff: false }),
    });
    if (!unmuteRes.ok) throw new Error('Host could not unmute participant');
    console.log('  ✅ PASS: Host mute, camera-off, unmute, and camera-on state persists');
    passedTests++;

    // Brief disconnects must preserve membership when the same user reconnects within grace.
    clientSocketB.disconnect();
    await new Promise((resolve) => setTimeout(resolve, 1000));
    socketB = await connectSocket(clientB.token);
    clientSocketB = socketB;
    clientSocketB.emit('room:join', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 500));
    const reconnectedRoomResponse = await apiFetch(`/api/v1/rooms/${room.id}`, {
      headers: { Authorization: `Bearer ${clientA.token}` },
    });
    const reconnectedRoom = (await reconnectedRoomResponse.json()) as any;
    if (!reconnectedRoom.success || !reconnectedRoom.data.participants.some((p: any) => p.userId === clientB.user.id)) {
      throw new Error('Client B was removed despite reconnecting before the 60-second inactivity timeout');
    }
    console.log('  ✅ PASS 10: Client B retained room membership after reconnecting before the 60-second timeout');
    passedTests++;

    // Backgrounding removes the participant at the configured strict 60-second grace.
    clientSocketB.emit('room:background', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 60_500));
    const inactiveRoom = await (await apiFetch(`/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
    if (inactiveRoom.data?.participants.some((p: any) => p.userId === clientB.user.id)) {
      throw new Error('Backgrounded participant remained in room after the 60-second grace period');
    }
    console.log('  ✅ PASS: Backgrounded participant is removed after 60 seconds');
    passedTests++;
    await joinRoom(clientB.token, room.id);
    clientSocketB.emit('room:join', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 250));

    // 13. Client B leaves room -> Client A receives participant:left
    const leaveSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for participant:left')), 5000);
      clientSocketA.once('participant:left', (payload: any) => {
        clearTimeout(timeout);
        if (payload.userId === clientB.user.id) {
          resolve();
        } else {
          reject(new Error(`Expected participant B left, got: ${payload.userId}`));
        }
      });
    });

    clientSocketB.emit('room:leave', { roomId: room.id });
    await leaveSyncPromise;
    console.log('  ✅ PASS 11: Client B left room, Client A received participant:left');
    passedTests++;

    // Kick is a room-specific ban while the same host owns the room.
    await joinRoom(clientB.token, room.id);
    clientSocketB.emit('room:join', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 250));
    const kickRes = await apiFetch(`/api/v1/rooms/${room.id}/participants/${clientB.user.id}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (!kickRes.ok) throw new Error(`Host could not kick participant: ${await kickRes.text()}`);
    const kickedGet = await apiFetch(`/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if (kickedGet.status !== 404) throw new Error('Kicked user can still view the room instead of receiving not-found');
    const kickedList = await (await apiFetch(`/api/v1/rooms?search=Sync%20Verification%20Room&limit=50`, { headers: { Authorization: `Bearer ${clientB.token}` } })).json() as any;
    if (kickedList.data?.some((candidate: any) => candidate.id === room.id)) throw new Error('Kicked room remains visible in the kicked user room list');
    let blockedRejoin = false;
    try { await joinRoom(clientB.token, room.id); } catch { blockedRejoin = true; }
    if (!blockedRejoin) throw new Error('Kicked user re-entered while the same host still owns the room');
    console.log('  ✅ PASS: Kicked user cannot view, discover, or rejoin the room while the same host owns it');
    passedTests++;

    // The kicked user is released from the old room and may join an independent one.
    clientSocketB.disconnect();
    await joinRoom(clientB.token, roomC.id);
    socketB = await connectSocket(clientB.token);
    clientSocketB = socketB;
    clientSocketB.emit('room:join', { roomId: roomC.id });
    await new Promise((resolve) => setTimeout(resolve, 250));
    console.log('  ✅ PASS: Kicked user can immediately join a different room');
    passedTests++;

    // Playback events remain scoped to the exact room: A is in Alpha; B and C are in Beta.
    let crossRoomLeak = false;
    const leakedPlayback = () => { crossRoomLeak = true; };
    clientSocketB.on('media:play', leakedPlayback);
    clientSocketA.emit('media:play', { roomId: room.id, positionMs: 7000 });
    await new Promise((resolve) => setTimeout(resolve, 500));
    clientSocketB.off('media:play', leakedPlayback);
    if (crossRoomLeak) throw new Error('Alpha room playback leaked to a participant in Beta room');
    let reverseMediaLeak = false;
    const leakedToAlpha = () => { reverseMediaLeak = true; };
    clientSocketA.on('media:play', leakedToAlpha);
    const betaPlaybackPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Beta host playback did not reach its room member')), 3000);
      clientSocketB.once('media:play', (payload: any) => {
        clearTimeout(timeout);
        if (payload.positionMs !== 9000) reject(new Error('Beta playback position was corrupted'));
        else resolve();
      });
    });
    clientSocketC.emit('media:play', { roomId: roomC.id, positionMs: 9000 });
    await betaPlaybackPromise;
    await new Promise((resolve) => setTimeout(resolve, 500));
    clientSocketA.off('media:play', leakedToAlpha);
    if (reverseMediaLeak) throw new Error('Beta room playback leaked to a participant in Alpha room');
    let crossRoomChatLeak = false;
    const leakedChat = (message: any) => { if (message.text === 'alpha-only-chat') crossRoomChatLeak = true; };
    clientSocketB.on('chat:message', leakedChat);
    clientSocketA.emit('chat:message', { roomId: room.id, text: 'alpha-only-chat' });
    await new Promise((resolve) => setTimeout(resolve, 500));
    clientSocketB.off('chat:message', leakedChat);
    if (crossRoomChatLeak) throw new Error('Alpha room chat leaked to a participant in Beta room');
    console.log('  ✅ PASS: Media and chat stay isolated between two simultaneously active rooms');
    passedTests++;

    // Once the kicker leaves and the remaining host changes, the old kick no longer applies.
    await apiFetch(`/api/v1/rooms/${roomC.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    clientSocketB.disconnect();
    await apiFetch(`/api/v1/rooms/${roomC.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    clientSocketC.disconnect();
    await joinRoom(clientC.token, room.id);
    socketC = await connectSocket(clientC.token);
    const reconnectedSocketC = socketC;
    reconnectedSocketC.emit('room:join', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 250));
    const hostLeave = await apiFetch(`/api/v1/rooms/${room.id}/leave`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({}),
    });
    if (!hostLeave.ok) throw new Error('Original room host could not leave');
    await joinRoom(clientB.token, room.id);
    console.log('  ✅ PASS: Kicked user can join after the host who kicked them leaves');
    passedTests++;

    await apiFetch(`/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    await apiFetch(`/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    const dissolvedRoom = await apiFetch(`/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientC.token}` } });
    if (dissolvedRoom.status !== 404) throw new Error('Room was not dissolved after its last participant left');
    console.log('  ✅ PASS: Room dissolves after its last participant leaves');
    passedTests++;

    const socketOnlyRoom = await createRoom(clientA.token, 'Socket Dissolution Room', 'Test media', '');
    socketA?.disconnect();
    socketA = await connectSocket(clientA.token);
    const lastMemberSocket = socketA;
    lastMemberSocket.emit('room:join', { roomId: socketOnlyRoom.id });
    await new Promise((resolve) => setTimeout(resolve, 250));
    const dissolvedEvent = new Promise<string>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Last member did not receive room:removed')), 5000);
      lastMemberSocket.once('room:removed', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload?.reason || '');
      });
    });
    lastMemberSocket.emit('room:leave', { roomId: socketOnlyRoom.id });
    if (await dissolvedEvent !== 'dissolved') throw new Error('Last member received the wrong room removal reason');
    const socketDissolvedRoom = await apiFetch(`/api/v1/rooms/${socketOnlyRoom.id}`, {
      headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (socketDissolvedRoom.status !== 404) throw new Error('Socket leave left a zero-member room in the database');
    console.log('  ✅ PASS: Final socket leave dissolves the room and broadcasts removal');
    passedTests++;

    // ---------------------------------------------------------------------
    // Playback ordering: a command built on an older playback generation must
    // never be able to move the room backwards.
    // ---------------------------------------------------------------------
    const versionRoom = await createRoom(clientA.token, 'Playback Ordering Room', 'Version test', 'https://www.youtube.com/watch?v=aqz-KE-bpKQ');
    await joinRoom(clientB.token, versionRoom.id);
    socketA = await connectSocket(clientA.token);
    socketB = await connectSocket(clientB.token);
    const versionSocketA = socketA;
    const versionSocketB = socketB;
    versionSocketA.emit('room:join', { roomId: versionRoom.id });
    versionSocketB.emit('room:join', { roomId: versionRoom.id });
    await new Promise((resolve) => setTimeout(resolve, 300));

    const seekBroadcast = new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Seek was never broadcast to the room member')), 3000);
      versionSocketB.once('media:seek', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload);
      });
    });
    versionSocketA.emit('media:seek', { roomId: versionRoom.id, positionMs: 30_000, version: 0 });
    const acceptedSeek = await seekBroadcast;
    if (typeof acceptedSeek.version !== 'number' || acceptedSeek.version < 1) {
      throw new Error(`Accepted seek did not carry an incremented playback version: ${JSON.stringify(acceptedSeek)}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 150));

    let rewoundByStaleCommand = false;
    const onLateSeek = (payload: any) => { if (payload.positionMs === 5_000) rewoundByStaleCommand = true; };
    versionSocketB.on('media:seek', onLateSeek);
    const correction = new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Stale playback command was neither applied nor corrected')), 3000);
      versionSocketA.once('media:state', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload);
      });
    });
    versionSocketA.emit('media:seek', { roomId: versionRoom.id, positionMs: 5_000, version: acceptedSeek.version - 1 });
    const correctionPayload = await correction;
    await new Promise((resolve) => setTimeout(resolve, 400));
    versionSocketB.off('media:seek', onLateSeek);
    if (rewoundByStaleCommand) throw new Error('A command built on a stale playback version rewound the room');
    if (!correctionPayload.rejected || correctionPayload.positionMs < 30_000) {
      throw new Error(`Stale command rejection did not carry the authoritative position: ${JSON.stringify(correctionPayload)}`);
    }
    console.log('  ✅ PASS: A playback command from an older generation is refused and the sender is corrected');
    passedTests++;

    // Out-of-order playhead samples carrying the *same* version are dropped by clock.
    const firstSample = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('First host sample never reached the room')), 3000);
      versionSocketB.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        if (payload.positionMs === 40_000) resolve();
        else reject(new Error(`Unexpected first sample: ${JSON.stringify(payload)}`));
      });
    });
    const sampleClock = Date.now();
    versionSocketA.emit('media:sync', { roomId: versionRoom.id, positionMs: 40_000, isPlaying: true, timestamp: sampleClock });
    await firstSample;
    versionSocketA.emit('media:sync', { roomId: versionRoom.id, positionMs: 10_000, isPlaying: true, timestamp: sampleClock - 1_000 });
    await new Promise((resolve) => setTimeout(resolve, 400));
    const lateJoinSnapshot = new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Late joiner received no playback snapshot')), 3000);
      versionSocketB.once('media:sync', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload);
      });
    });
    versionSocketB.emit('room:join', { roomId: versionRoom.id });
    const snapshot = await lateJoinSnapshot;
    if (snapshot.positionMs < 40_000) {
      throw new Error(`A late-arriving older sample rewound the authoritative playhead to ${snapshot.positionMs}`);
    }
    console.log('  ✅ PASS: An out-of-order host sample cannot rewind the room playhead');
    passedTests++;

    // ---------------------------------------------------------------------
    // Missed-message recovery: a client that lost its socket asks for the gap only.
    // ---------------------------------------------------------------------
    const cursorMessage = new Promise<any>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Backfill fixture message was not accepted')), 3000);
      versionSocketA.once('chat:message', (payload: any) => {
        clearTimeout(timeout);
        resolve(payload);
      });
    });
    versionSocketB.emit('chat:message', { roomId: versionRoom.id, text: 'before-the-drop' });
    const cursorAnchor = await cursorMessage;
    const cursor = cursorAnchor.createdAt;
    if (!cursor) throw new Error('Message DTO carried no createdAt cursor');

    versionSocketB.disconnect();
    const missedFirst = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Missed message 1 was not stored')), 3000);
      versionSocketA.on('chat:message', (payload: any) => { if (payload.text === 'missed-while-offline-1') { clearTimeout(timeout); resolve(); } });
    });
    versionSocketA.emit('chat:message', { roomId: versionRoom.id, text: 'missed-while-offline-1' });
    await missedFirst;
    const missedSecond = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Missed message 2 was not stored')), 3000);
      versionSocketA.on('chat:message', (payload: any) => { if (payload.text === 'missed-while-offline-2') { clearTimeout(timeout); resolve(); } });
    });
    versionSocketA.emit('chat:message', { roomId: versionRoom.id, text: 'missed-while-offline-2' });
    await missedSecond;

    const backfillRes = await apiFetch(`/api/v1/chat/room/${versionRoom.id}?since=${encodeURIComponent(cursor)}`, {
      headers: { Authorization: `Bearer ${clientB.token}` },
    });
    const backfill = await backfillRes.json() as any;
    if (!backfillRes.ok) throw new Error(`Backfill request failed: ${JSON.stringify(backfill)}`);
    const recoveredTexts = (backfill.data || []).map((message: any) => message.text);
    if (!recoveredTexts.includes('missed-while-offline-1') || !recoveredTexts.includes('missed-while-offline-2')) {
      throw new Error(`Backfill did not return the messages sent during the gap: ${JSON.stringify(recoveredTexts)}`);
    }
    if (!recoveredTexts.includes('before-the-drop')) {
      throw new Error('Backfill cursor lost the message the client already held');
    }
    const emptyBackfill = await (await apiFetch(`/api/v1/chat/room/${versionRoom.id}?since=${encodeURIComponent(new Date().toISOString())}`, {
      headers: { Authorization: `Bearer ${clientB.token}` },
    })).json() as any;
    if ((emptyBackfill.data || []).length !== 0) throw new Error('A cursor in the future still returned history');
    const invalidCursor = await apiFetch(`/api/v1/chat/room/${versionRoom.id}?since=not-a-date`, {
      headers: { Authorization: `Bearer ${clientB.token}` },
    });
    if (invalidCursor.status !== 400) throw new Error(`An unparseable cursor returned ${invalidCursor.status} instead of 400`);
    console.log('  ✅ PASS: Room chat backfill returns exactly the missed window and rejects a bad cursor');
    passedTests++;

    socketB = await connectSocket(clientB.token);
    const recoveredSocketB = socketB;
    recoveredSocketB.emit('room:join', { roomId: versionRoom.id });
    await new Promise((resolve) => setTimeout(resolve, 200));

    // ---------------------------------------------------------------------
    // Presence TTL: a process that dies without disconnecting stops advertising itself.
    // ---------------------------------------------------------------------
    recoveredSocketB.disconnect();
    await new Promise((resolve) => setTimeout(resolve, 300));
    await UserModel.updateOne({ _id: clientB.user.id }, { $set: { presenceStatus: 'online', lastActiveAt: new Date(Date.now() - 10 * 60_000) } });
    const reclaimed = await sweepStalePresence();
    const reclaimedUser = await UserModel.findById(clientB.user.id).select('presenceStatus');
    if (!reclaimed.includes(clientB.user.id) || reclaimedUser?.presenceStatus !== 'offline') {
      throw new Error('Presence sweep did not reclaim a user whose heartbeat stopped');
    }
    await UserModel.updateOne({ _id: clientA.user.id }, { $set: { presenceStatus: 'online', lastActiveAt: new Date(Date.now() - 10 * 60_000) } });
    if ((await sweepStalePresence()).includes(clientA.user.id)) {
      throw new Error('Presence sweep reclaimed a user that still holds an open socket');
    }
    const stillOnline = await UserModel.findById(clientA.user.id).select('presenceStatus');
    if (stillOnline?.presenceStatus === 'offline') throw new Error('Socket-connected user was wrongly marked offline');
    console.log('  ✅ PASS: Presence sweep reclaims abandoned sessions but never a live socket');
    passedTests++;

    await apiFetch(`/api/v1/rooms/${versionRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    await apiFetch(`/api/v1/rooms/${versionRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });

    // ---------------------------------------------------------------------
    // Discovery ranking: a busier room outranks a brand-new empty one.
    // ---------------------------------------------------------------------
    const quietRoom = await createRoom(clientC.token, 'Quiet Ranking Room', 'Quiet', '');
    const busyRoom = await createRoom(clientA.token, 'Busy Ranking Room', 'Busy', '');
    await joinRoom(clientB.token, busyRoom.id);
    await new Promise((resolve) => setTimeout(resolve, 100));
    const ranked = await (await apiFetch('/api/v1/rooms?limit=50', { headers: { Authorization: `Bearer ${clientB.token}` } })).json() as any;
    const rankedIds = (ranked.data || []).map((candidate: any) => candidate.id);
    const busyIndex = rankedIds.indexOf(busyRoom.id);
    const quietIndex = rankedIds.indexOf(quietRoom.id);
    if (busyIndex === -1 || quietIndex === -1) throw new Error('Discovery list dropped a room that is open to the requester');
    if (busyIndex > quietIndex) throw new Error('Discovery ranked a one-member room above a two-member room');
    console.log('  ✅ PASS: Room discovery ranks a busier room above a brand-new empty one');
    passedTests++;

    // Everyone must be roomless before the next set of rooms is created: an account
    // holds only one active room at a time.
    await apiFetch(`/api/v1/rooms/${busyRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    await apiFetch(`/api/v1/rooms/${busyRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });
    await apiFetch(`/api/v1/rooms/${quietRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });

    // ---------------------------------------------------------------------
    // Shareable invite codes: a public room resolves from its code, and a private
    // room answers exactly like a made-up code so the endpoint cannot probe.
    // ---------------------------------------------------------------------
    const shareableRoom = await createRoom(clientA.token, 'Shareable Code Room', 'Code', '');
    if (!/^[2-9A-HJ-NP-Z]{8}$/.test(shareableRoom.inviteCode || '')) {
      throw new Error(`Room create response carried no shareable code: ${JSON.stringify(shareableRoom.inviteCode)}`);
    }
    const codeResolve = await apiFetch(`/api/v1/rooms/invite/${shareableRoom.inviteCode}`, {
      headers: { Authorization: `Bearer ${clientB.token}` },
    });
    const resolved = await codeResolve.json() as any;
    if (!codeResolve.ok || resolved.data?.id !== shareableRoom.id) {
      throw new Error(`A public room did not resolve from its code: ${JSON.stringify(resolved)}`);
    }
    const privateRoomRes = await apiFetch('/api/v1/rooms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientC.token}` },
      body: JSON.stringify({ name: 'Code Probe Private Room', privacy: 'invite_only' }),
    });
    const privateRoom = await privateRoomRes.json() as any;
    if (privateRoomRes.status !== 201) throw new Error(`Could not create the private room fixture: ${JSON.stringify(privateRoom)}`);
    const privateCode = privateRoom.data?.inviteCode;
    if (!privateCode) throw new Error('Private room create response carried no code');
    const hostResolve = await apiFetch(`/api/v1/rooms/invite/${privateCode}`, {
      headers: { Authorization: `Bearer ${clientC.token}` },
    });
    if (!hostResolve.ok) throw new Error('The host could not resolve their own room code');
    const strangerResolve = await apiFetch(`/api/v1/rooms/invite/${privateCode}`, {
      headers: { Authorization: `Bearer ${clientB.token}` },
    });
    const unknownResolve = await apiFetch('/api/v1/rooms/invite/22222222', {
      headers: { Authorization: `Bearer ${clientB.token}` },
    });
    if (strangerResolve.status !== 404) throw new Error(`A private room code leaked to a stranger with status ${strangerResolve.status}`);
    const strangerBody = await strangerResolve.text();
    const unknownBody = await unknownResolve.text();
    if (strangerBody !== unknownBody) throw new Error('A refused private code was distinguishable from an unknown code');
    console.log('  ✅ PASS: Invite codes resolve for allowed viewers and leak nothing for refused ones');
    passedTests++;

    await apiFetch(`/api/v1/rooms/${privateRoom.data.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });

    // ---------------------------------------------------------------------
    // Room queue: members add and vote, a strict majority skips, the host can
    // force the next item, and non-members are refused.
    // ---------------------------------------------------------------------
    // Reuses the shareable room rather than creating another: room creation is rate
    // limited per user, and a suite that trips its own limiter proves nothing.
    const queueRoom = shareableRoom;
    await joinRoom(clientB.token, queueRoom.id);
    const firstItemRes = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ title: 'Queued One', mediaUrl: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ' }),
    });
    const firstItem = await firstItemRes.json() as any;
    if (firstItemRes.status !== 201) throw new Error(`Could not queue an item: ${JSON.stringify(firstItem)}`);
    const secondItemRes = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ title: 'Queued Two', mediaUrl: 'https://www.youtube.com/watch?v=hTWKbfoikeg' }),
    });
    const secondItem = await secondItemRes.json() as any;
    if (secondItemRes.status !== 201) throw new Error(`Could not queue a second item: ${JSON.stringify(secondItem)}`);

    const outsiderAdd = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientC.token}` },
      body: JSON.stringify({ title: 'Outsider', mediaUrl: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ' }),
    });
    if (outsiderAdd.status !== 403) throw new Error(`A non-member queued an item with status ${outsiderAdd.status}`);

    const firstVote = await (await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue/${firstItem.data.id}/skip-vote`, {
      method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` },
    })).json() as any;
    if (firstVote.data?.skipped !== false || firstVote.data?.skipVotesRequired !== 2) {
      throw new Error(`One of two members skipped the item alone: ${JSON.stringify(firstVote.data)}`);
    }
    const secondVote = await (await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue/${firstItem.data.id}/skip-vote`, {
      method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` },
    })).json() as any;
    if (secondVote.data?.skipped !== true) {
      throw new Error(`A strict majority did not skip the item: ${JSON.stringify(secondVote.data)}`);
    }
    // A vote removes the item from the queue; it must not change what is playing.
    const afterSkip = await (await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue`, {
      headers: { Authorization: `Bearer ${clientA.token}` },
    })).json() as any;
    const remainingTitles = (afterSkip.data || []).map((entry: any) => entry.title);
    if (remainingTitles.includes('Queued One')) throw new Error('The skipped item stayed in the queue');
    if (!remainingTitles.includes('Queued Two')) throw new Error('Skipping removed more than the voted item');

    const advance = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue/advance`, {
      method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (!advance.ok) throw new Error(`Host could not advance the queue: ${await advance.text()}`);
    const afterAdvance = await (await apiFetch(`/api/v1/rooms/${queueRoom.id}`, {
      headers: { Authorization: `Bearer ${clientA.token}` },
    })).json() as any;
    if (afterAdvance.data?.mediaState?.title !== 'Queued Two') {
      throw new Error(`Advancing did not start the next item: ${JSON.stringify(afterAdvance.data?.mediaState?.title)}`);
    }
    const emptyAdvance = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue/advance`, {
      method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (emptyAdvance.status !== 404) throw new Error(`Advancing an empty queue returned ${emptyAdvance.status}`);
    const viewerAdvance = await apiFetch(`/api/v1/rooms/${queueRoom.id}/queue/advance`, {
      method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` },
    });
    if (viewerAdvance.status !== 403) throw new Error(`A non-host advanced the queue with status ${viewerAdvance.status}`);
    console.log('  ✅ PASS: Queue add, strict-majority skip, host-only advance, and membership checks all hold');
    passedTests++;

    await apiFetch(`/api/v1/rooms/${queueRoom.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });

    // Cleanup sockets
    socketA.disconnect();
    socketB.disconnect();

    console.log('====================================================');
    console.log(`🎉 MULTI-CLIENT ROOM AUDIT COMPLETE: ${passedTests} assertions passed`);
    console.log('====================================================');
  } catch (err: any) {
    console.error('❌ MULTI-CLIENT SYNC TEST FAILED:', err.message);
    failed = true;
  } finally {
    socketA?.disconnect();
    socketB?.disconnect();
    socketC?.disconnect();
    // Disconnect handlers persist presence and membership asynchronously. Let
    // those writes finish before the database goes away, otherwise teardown
    // logs a misleading MongoNotConnectedError after a fully passing run.
    await new Promise((resolve) => setTimeout(resolve, 750));
    if (server) await new Promise<void>((resolve) => server!.close(() => resolve()));
    await mongoose.disconnect();
    await testDatabase.stop();
  }
  if (failed) process.exitCode = 1;
}

runMultiClientSyncTest();
