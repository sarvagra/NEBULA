import { io as ClientSocket, Socket } from 'socket.io-client';

const API_BASE = process.env.TEST_API_BASE || 'http://localhost:5001';

interface AuthResponse {
  success: boolean;
  data: {
    user: { id: string; username: string; displayName: string };
    tokens: { token: string };
  };
}

async function loginUser(email: string, username: string, displayName: string): Promise<{ token: string; user: any }> {
  const res = await fetch(`${API_BASE}/api/v1/auth/dev-login`, {
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
  const res = await fetch(`${API_BASE}/api/v1/rooms`, {
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

function connectSocket(token: string): Promise<Socket> {
  return new Promise((resolve, reject) => {
    const socket = ClientSocket(API_BASE, {
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
  const totalTests = 11;

  try {
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
    console.log(`  ✅ Room Created: "${room.name}" [ID: ${room.id}]`);

    // 3. Socket Connections
    console.log('[Sockets] Establishing authenticated Socket.IO connections...');
    const socketA = await connectSocket(clientA.token);
    const socketB = await connectSocket(clientB.token);
    console.log('  ✅ PASS 1: Both sockets authenticated and connected');
    passedTests++;

    // 4. Host Joins Room
    await new Promise<void>((resolve) => {
      socketA.emit('room:join', { roomId: room.id });
      setTimeout(resolve, 500);
    });

    // 5. Client B Joins Room -> Client A receives participant:joined
    const participantJoinedPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for participant:joined')), 5000);
      socketA.once('participant:joined', (payload: any) => {
        clearTimeout(timeout);
        if (payload.userId === clientB.user.id) {
          resolve();
        } else {
          reject(new Error(`Unexpected participant joined: ${payload.userId}`));
        }
      });
    });

    socketB.emit('room:join', { roomId: room.id });
    await participantJoinedPromise;
    console.log('  ✅ PASS 2: Client A received participant:joined when Client B entered');
    passedTests++;

    // 6. Security Check: Client B (non-host) cannot control playback
    const unauthorizedPlayPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for unauthorized error')), 4000);
      socketB.once('error', (err: any) => {
        clearTimeout(timeout);
        if (err.message && err.message.includes('Only the room host can control')) {
          resolve();
        } else {
          reject(new Error(`Unexpected error message: ${err.message}`));
        }
      });
    });

    socketB.emit('media:play', { roomId: room.id, position: 42 });
    await unauthorizedPlayPromise;
    console.log('  ✅ PASS 3: Server rejected unauthorized media:play from non-host Client B');
    passedTests++;

    // 7. Host Emits media:play -> Client B receives media:play
    const playSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:play')), 5000);
      socketB.once('media:play', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 15) {
          resolve();
        } else {
          reject(new Error(`Expected position 15, got ${payload.position}`));
        }
      });
    });

    socketA.emit('media:play', { roomId: room.id, position: 15 });
    await playSyncPromise;
    console.log('  ✅ PASS 4: Host emitted media:play, Client B received synchronized play at 15s');
    passedTests++;

    // 8. Host Emits media:pause -> Client B receives media:pause
    const pauseSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:pause')), 5000);
      socketB.once('media:pause', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 30) {
          resolve();
        } else {
          reject(new Error(`Expected position 30, got ${payload.position}`));
        }
      });
    });

    socketA.emit('media:pause', { roomId: room.id, position: 30 });
    await pauseSyncPromise;
    console.log('  ✅ PASS 5: Host emitted media:pause, Client B received synchronized pause at 30s');
    passedTests++;

    // 9. Host Emits media:seek -> Client B receives media:seek
    const seekSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:seek')), 5000);
      socketB.once('media:seek', (payload: any) => {
        clearTimeout(timeout);
        if (payload.position === 120) {
          resolve();
        } else {
          reject(new Error(`Expected position 120, got ${payload.position}`));
        }
      });
    });

    socketA.emit('media:seek', { roomId: room.id, position: 120 });
    await seekSyncPromise;
    console.log('  ✅ PASS 6: Host emitted media:seek, Client B received synchronized seek at 120s');
    passedTests++;

    // 10. Host Switches Media -> Client B receives media:switch
    const switchSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for media:switch')), 5000);
      socketB.once('media:switch', (payload: any) => {
        clearTimeout(timeout);
        if (payload.mediaState && payload.mediaState.title === 'Interstellar Docking Scene') {
          resolve();
        } else {
          reject(new Error(`Expected new title Interstellar, got: ${JSON.stringify(payload)}`));
        }
      });
    });

    socketA.emit('media:switch', {
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
      socketA.once('chat:message', (payload: any) => {
        clearTimeout(timeout);
        if (payload.text === 'Synchronized audio is incredible!') {
          resolve();
        } else {
          reject(new Error(`Unexpected message text: ${payload.text}`));
        }
      });
    });

    socketB.emit('chat:message', {
      roomId: room.id,
      text: 'Synchronized audio is incredible!',
    });
    await chatSyncPromise;
    console.log('  ✅ PASS 8: Client B sent chat message, Client A received in real time');
    passedTests++;

    // 12. Realtime Chat Reaction: Client B reacts -> Client A receives
    const reactionSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for chat:reaction')), 5000);
      socketA.once('chat:reaction', (payload: any) => {
        clearTimeout(timeout);
        if (payload.emoji === '🔥') {
          resolve();
        } else {
          reject(new Error(`Expected emoji 🔥, got: ${payload.emoji}`));
        }
      });
    });

    socketB.emit('chat:reaction', {
      roomId: room.id,
      emoji: '🔥',
    });
    await reactionSyncPromise;
    console.log('  ✅ PASS 9: Client B reacted with 🔥, Client A received reaction');
    passedTests++;

    // 13. Client B leaves room -> Client A receives participant:left
    const leaveSyncPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for participant:left')), 5000);
      socketA.once('participant:left', (payload: any) => {
        clearTimeout(timeout);
        if (payload.userId === clientB.user.id) {
          resolve();
        } else {
          reject(new Error(`Expected participant B left, got: ${payload.userId}`));
        }
      });
    });

    socketB.emit('room:leave', { roomId: room.id });
    await leaveSyncPromise;
    console.log('  ✅ PASS 10: Client B left room, Client A received participant:left');
    passedTests++;

    // 14. Host leaves room -> room automatically ended (isEnded: true)
    const roomEndedPromise = new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout waiting for room:ended')), 5000);
      socketA.once('room:ended', (payload: any) => {
        clearTimeout(timeout);
        if (payload.roomId === room.id) {
          resolve();
        } else {
          reject(new Error(`Expected room ended for ${room.id}`));
        }
      });
    });

    socketA.emit('room:leave', { roomId: room.id });
    await roomEndedPromise;
    console.log('  ✅ PASS 11: Host left last, room marked ended and emitted room:ended');
    passedTests++;

    // Cleanup sockets
    socketA.disconnect();
    socketB.disconnect();

    console.log('====================================================');
    console.log(`🎉 MULTI-CLIENT SYNC AUDIT COMPLETE: ${passedTests}/${totalTests} PASSED`);
    console.log('====================================================');
    process.exit(0);
  } catch (err: any) {
    console.error('❌ MULTI-CLIENT SYNC TEST FAILED:', err.message);
    process.exit(1);
  }
}

runMultiClientSyncTest();
