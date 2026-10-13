import { io as ClientSocket, Socket } from 'socket.io-client';
import http from 'http';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { config } from '../config/env';
import { createApp } from '../app';
import { initSocketServer } from '../realtime/socketServer';

let apiBase = '';

interface AuthResponse {
  success: boolean;
  data: {
    user: { id: string; username: string; displayName: string };
    tokens: { token: string };
  };
}

async function loginUser(email: string, username: string, displayName: string): Promise<{ token: string; user: any }> {
  const res = await fetch(`${apiBase}/api/v1/auth/dev-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, username, displayName }),
  });
  const data = (await res.json()) as AuthResponse;
  if (!data.success) {
    throw new Error(`Failed to log in ${email}: ${JSON.stringify(data)}`);
  }
  return { token: data.data.tokens.token, user: data.data.user };
}

async function createRoom(token: string, name: string, mediaTitle: string, mediaUrl: string): Promise<any> {
  const res = await fetch(`${apiBase}/api/v1/rooms`, {
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
  const res = await fetch(`${apiBase}/api/v1/rooms/${roomId}/join`, {
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
    const secondRoomAttempt = await fetch(`${apiBase}/api/v1/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ name: 'Must Be Rejected', privacy: 'public', activity: 'watch' }),
    });
    if (secondRoomAttempt.status !== 409) throw new Error('A user was allowed to create a second active room');
    console.log('  ✅ PASS: A participant cannot create a second room while already in one');
    passedTests++;
    const selfRequestRes = await fetch(`${apiBase}/api/v1/friends/request`, {
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
    const friendRequestRes = await fetch(`${apiBase}/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ recipientId: clientB.user.id }),
    });
    const friendRequest = await friendRequestRes.json() as any;
    if (friendRequestRes.status !== 201) throw new Error(`Friend request failed: ${JSON.stringify(friendRequest)}`);
    const pendingRes = await fetch(`${apiBase}/api/v1/friends/requests`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    const pending = await pendingRes.json() as any;
    if (!pending.data?.some((request: any) => request.id === friendRequest.data.id)) throw new Error('Incoming friend request did not appear for the recipient');
    const withdrawRes = await fetch(`${apiBase}/api/v1/friends/request/withdraw/${clientB.user.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!withdrawRes.ok) throw new Error('Requester could not withdraw friend request');
    const withdrawnRes = await fetch(`${apiBase}/api/v1/friends/requests`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if ((await withdrawnRes.json() as any).data?.some((request: any) => request.id === friendRequest.data.id)) throw new Error('Withdrawn request remained visible to recipient');
    console.log('  ✅ PASS: Friend request appears for recipient and disappears after sender withdraws');
    passedTests++;

    const reverseRequestRes = await fetch(`${apiBase}/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientB.token}` },
      body: JSON.stringify({ recipientId: clientA.user.id }),
    });
    const reverseRequest = await reverseRequestRes.json() as any;
    if (reverseRequestRes.status !== 201) throw new Error(`Reverse friend request failed: ${JSON.stringify(reverseRequest)}`);
    const acceptRes = await fetch(`${apiBase}/api/v1/friends/request/${reverseRequest.data.id}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!acceptRes.ok) throw new Error('Recipient could not accept incoming friend request');
    const friendsRes = await fetch(`${apiBase}/api/v1/friends`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if (!(await friendsRes.json() as any).data?.some((friend: any) => friend.id === clientA.user.id)) throw new Error('Accepted friendship did not appear on requester side');
    console.log('  ✅ PASS: Recipient accepts a request and the accepted friendship appears for both');
    passedTests++;

    const clientC = await loginUser('decline_sync_c@nebula.io', 'decline_sync_c', 'Decline Sync Gamma');
    const declineRequestRes = await fetch(`${apiBase}/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientC.token}` },
      body: JSON.stringify({ recipientId: clientA.user.id }),
    });
    const declineRequest = await declineRequestRes.json() as any;
    const declineRes = await fetch(`${apiBase}/api/v1/friends/request/${declineRequest.data.id}/decline`, { method: 'POST', headers: { Authorization: `Bearer ${clientA.token}` } });
    if (!declineRes.ok) throw new Error('Recipient could not decline incoming friend request');
    console.log('  ✅ PASS: Recipient can decline an incoming request');
    passedTests++;

    const requestCRes = await fetch(`${apiBase}/api/v1/friends/request`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ recipientId: clientC.user.id }),
    });
    const requestC = await requestCRes.json() as any;
    const acceptCRes = await fetch(`${apiBase}/api/v1/friends/request/${requestC.data.id}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    if (!acceptCRes.ok) throw new Error('Could not accept invitation test friendship');
    const inviteCRes = await fetch(`${apiBase}/api/v1/rooms/${room.id}/invite`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ userId: clientC.user.id }),
    });
    if (inviteCRes.status !== 201) throw new Error(`Could not invite an existing friend: ${await inviteCRes.text()}`);
    await joinRoom(clientC.token, room.id);
    const invitedRoom = await (await fetch(`${apiBase}/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
    if (!invitedRoom.data?.participants.some((p: any) => p.userId === clientC.user.id)) throw new Error('Invited friend could not join room');
    await fetch(`${apiBase}/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    console.log('  ✅ PASS: A room member can invite an existing friend, who then joins and appears in participants');
    passedTests++;

    // A second, independent room gives the test two simultaneous room channels.
    const roomC = await createRoom(clientC.token, 'Independent Beta Room', 'Different song', 'https://www.youtube.com/watch?v=aqz-KE-bpKQ');
    socketC = await connectSocket(clientC.token);
    const clientSocketC = socketC;
    clientSocketC.emit('room:join', { roomId: roomC.id });
    await new Promise((resolve) => setTimeout(resolve, 250));

    const moderationRes = await fetch(`${apiBase}/api/v1/rooms/${room.id}/participants/${clientB.user.id}/moderation`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({ isMuted: true, isCameraOff: true }),
    });
    if (!moderationRes.ok) throw new Error(`Host could not mute participant: ${await moderationRes.text()}`);
    const moderatedRoom = await (await fetch(`${apiBase}/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
    const moderatedParticipant = moderatedRoom.data.participants.find((p: any) => p.userId === clientB.user.id);
    if (!moderatedParticipant?.isMuted || !moderatedParticipant?.isCameraOff) throw new Error('Mute/camera-off state was not persisted');
    const unmuteRes = await fetch(`${apiBase}/api/v1/rooms/${room.id}/participants/${clientB.user.id}/moderation`, {
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
    const reconnectedRoomResponse = await fetch(`${apiBase}/api/v1/rooms/${room.id}`, {
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
    const inactiveRoom = await (await fetch(`${apiBase}/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientA.token}` } })).json() as any;
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
    const kickRes = await fetch(`${apiBase}/api/v1/rooms/${room.id}/participants/${clientB.user.id}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (!kickRes.ok) throw new Error(`Host could not kick participant: ${await kickRes.text()}`);
    const kickedGet = await fetch(`${apiBase}/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientB.token}` } });
    if (kickedGet.status !== 404) throw new Error('Kicked user can still view the room instead of receiving not-found');
    const kickedList = await (await fetch(`${apiBase}/api/v1/rooms?search=Sync%20Verification%20Room&limit=50`, { headers: { Authorization: `Bearer ${clientB.token}` } })).json() as any;
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
    await fetch(`${apiBase}/api/v1/rooms/${roomC.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    clientSocketB.disconnect();
    await fetch(`${apiBase}/api/v1/rooms/${roomC.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    clientSocketC.disconnect();
    await joinRoom(clientC.token, room.id);
    socketC = await connectSocket(clientC.token);
    const reconnectedSocketC = socketC;
    reconnectedSocketC.emit('room:join', { roomId: room.id });
    await new Promise((resolve) => setTimeout(resolve, 250));
    const hostLeave = await fetch(`${apiBase}/api/v1/rooms/${room.id}/leave`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${clientA.token}` },
      body: JSON.stringify({}),
    });
    if (!hostLeave.ok) throw new Error('Original room host could not leave');
    await joinRoom(clientB.token, room.id);
    console.log('  ✅ PASS: Kicked user can join after the host who kicked them leaves');
    passedTests++;

    await fetch(`${apiBase}/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientB.token}` } });
    await fetch(`${apiBase}/api/v1/rooms/${room.id}/leave`, { method: 'POST', headers: { Authorization: `Bearer ${clientC.token}` } });
    const dissolvedRoom = await fetch(`${apiBase}/api/v1/rooms/${room.id}`, { headers: { Authorization: `Bearer ${clientC.token}` } });
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
    const socketDissolvedRoom = await fetch(`${apiBase}/api/v1/rooms/${socketOnlyRoom.id}`, {
      headers: { Authorization: `Bearer ${clientA.token}` },
    });
    if (socketDissolvedRoom.status !== 404) throw new Error('Socket leave left a zero-member room in the database');
    console.log('  ✅ PASS: Final socket leave dissolves the room and broadcasts removal');
    passedTests++;

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
    if (server) await new Promise<void>((resolve) => server!.close(() => resolve()));
    await mongoose.disconnect();
    await testDatabase.stop();
  }
  if (failed) process.exitCode = 1;
}

runMultiClientSyncTest();
