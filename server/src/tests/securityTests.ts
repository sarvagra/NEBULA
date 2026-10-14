import http from 'http';
import jwt from 'jsonwebtoken';
import { io as ClientSocket } from 'socket.io-client';
import { createApp } from '../app';
import { initSocketServer } from '../realtime/socketServer';
import { config } from '../config/env';
import { UserModel } from '../models/User';
import { RoomModel } from '../models/Room';
import { FriendshipModel } from '../models/Friendship';
import { NotificationModel } from '../models/Notification';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { resolveStoredObjectUrl } from '../services/objectStorage';

// Standalone security test runner
async function runSecurityTests() {
  console.log('====================================================');
  console.log('🔒 RUNNING NEBULA BACKEND SECURITY AUDIT TEST SUITE');
  console.log('====================================================');

  // Security tests seed and delete records. Keep them isolated from every configured
  // shared or production database, regardless of the caller's environment variables.
  const testDatabase = await MongoMemoryServer.create();
  await mongoose.connect(testDatabase.getUri(), { dbName: 'nebula_security_test' });

  const app = createApp();
  const server = http.createServer(app);
  const io = initSocketServer(server);

  const TEST_PORT = 5099;
  await new Promise<void>((resolve) => server.listen(TEST_PORT, resolve));
  const baseUrl = `http://localhost:${TEST_PORT}`;

  let passed = 0;
  let failed = 0;

  async function assertTest(name: string, fn: () => Promise<void>) {
    try {
      await fn();
      console.log(`  ✅ PASS: ${name}`);
      passed++;
    } catch (err: any) {
      console.error(`  ❌ FAIL: ${name} -> ${err.message}`);
      failed++;
    }
  }

  // Generate tokens for testing
  const validUserPayload = {
    id: '507f1f77bcf86cd799439011',
    username: 'test_alice',
    displayName: 'Alice Test',
    tier: 'free',
    role: 'user',
  };

  const bobUserPayload = {
    id: '507f1f77bcf86cd799439022',
    username: 'test_bob',
    displayName: 'Bob Test',
    tier: 'free',
    role: 'user',
  };

  const validToken = jwt.sign(validUserPayload, config.auth.jwtSecret, { expiresIn: '1h' });
  const bobToken = jwt.sign(bobUserPayload, config.auth.jwtSecret, { expiresIn: '1h' });
  const expiredToken = jwt.sign(validUserPayload, config.auth.jwtSecret, { expiresIn: '-10s' });
  const invalidToken = 'ey.invalid.jwt.signature';

  // Seed mock records into Mongoose in-memory cache if needed
  try {
    await UserModel.deleteMany({ username: { $in: ['test_alice', 'test_bob'] } });
    await UserModel.create({
      _id: validUserPayload.id,
      username: validUserPayload.username,
      displayName: validUserPayload.displayName,
      tier: 'free',
      status: 'online',
    });
    await UserModel.create({
      _id: bobUserPayload.id,
      username: bobUserPayload.username,
      displayName: bobUserPayload.displayName,
      tier: 'free',
      status: 'online',
    });
  } catch {
    // If Mongo is offline, middleware will check in-memory/mock state
  }

  // Helper fetch
  async function makeRequest(path: string, options: RequestInit = {}) {
    const url = `${baseUrl}${path}`;
    return fetch(url, options);
  }

  // 1. UNAUTHENTICATED REQUEST -> 401
  await assertTest('Unauthenticated request to protected endpoint rejected with 401', async () => {
    const res = await makeRequest('/api/v1/auth/me');
    if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
    const json = (await res.json()) as any;
    if (json.success !== false) throw new Error('Expected success: false');
  });

  // 2. INVALID TOKEN -> 401
  await assertTest('Invalid token rejected with 401', async () => {
    const res = await makeRequest('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${invalidToken}` },
    });
    if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
  });

  // 3. EXPIRED TOKEN -> 401
  await assertTest('Expired token rejected with 401', async () => {
    const res = await makeRequest('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${expiredToken}` },
    });
    if (res.status !== 401) throw new Error(`Expected 401, got ${res.status}`);
  });

  // 4. MALFORMED MONGODB / NOSQL INJECTION -> 400
  await assertTest('NoSQL operator injection ($gt) blocked with 400', async () => {
    const res = await makeRequest('/api/v1/auth/otp/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: { $gt: '' } }),
    });
    if (res.status !== 400) throw new Error(`Expected 400, got ${res.status}`);
  });

  // 5. OVERSIZED PAYLOAD -> 413
  await assertTest('Oversized payload (>100KB) rejected with 413', async () => {
    const largeText = 'A'.repeat(120 * 1024); // 120 KB
    const res = await makeRequest('/api/v1/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({ text: largeText }),
    });
    if (res.status !== 413) throw new Error(`Expected 413, got ${res.status}`);
  });

  // 6. MALFORMED OBJECTID IN PARAMETERS -> 400
  await assertTest('Malformed ObjectId in URL path rejected with 400', async () => {
    const res = await makeRequest('/api/v1/rooms/invalid-non-hex-id');
    if (res.status !== 400) throw new Error(`Expected 400, got ${res.status}`);
  });

  // 7. FORGED PREMIUM BILLING VERIFICATION -> REJECTED
  await assertTest('Forged premium purchase token rejected', async () => {
    const res = await makeRequest('/api/v1/billing/verify', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({
        purchaseToken: 'forged_fake_token_12345',
        planTier: 'premium',
      }),
    });
    if (res.status !== 400 && res.status !== 503) {
      throw new Error(`Expected 400 or 503, got ${res.status}`);
    }
  });

  // 8. SERVER-AUTHORITATIVE ENTITLEMENT (CANNOT SPOOF TIER IN PROFILE)
  await assertTest('Profile update ignores client tier spoofing', async () => {
    const res = await makeRequest('/api/v1/users/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({
        displayName: 'Alice Updated',
        tier: 'premium_plus', // Attempted client spoofing
      }),
    });
    // Either succeeds with tier untouched as 'free', or validates
    if (res.status === 200) {
      const json = (await res.json()) as any;
      if (json.data && json.data.tier === 'premium_plus') {
        throw new Error('Security flaw: Client was able to escalate tier via profile update!');
      }
    }
  });

  // 8b. USERNAME COLLISION & VALIDATION SECURITY
  await assertTest('Username collision rejected with 409', async () => {
    const res = await makeRequest('/api/v1/users/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${bobToken}`,
      },
      body: JSON.stringify({ username: 'test_alice' }),
    });
    if (res.status !== 409) throw new Error(`Expected 409 for duplicate username, got ${res.status}`);
  });

  await assertTest('Malformed username (<3 chars or invalid symbols) rejected with 400', async () => {
    const res = await makeRequest('/api/v1/users/profile', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({ username: 'ab!' }),
    });
    if (res.status !== 400) throw new Error(`Expected 400 for malformed username, got ${res.status}`);
  });

  await assertTest('Username rename is recorded and locked for six months across requests', async () => {
    const userId = '507f1f77bcf86cd799439033';
    const originalUsername = 'test_rename';
    const renameToken = jwt.sign({ ...validUserPayload, id: userId, username: originalUsername }, config.auth.jwtSecret, { expiresIn: '1h' });
    await UserModel.create({ _id: userId, username: originalUsername, displayName: 'Rename Test', tier: 'free' });
    try {
      const first = await makeRequest('/api/v1/users/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${renameToken}` },
        body: JSON.stringify({ username: 'test_renamed' }),
      });
      if (first.status !== 200) throw new Error(`Expected first rename to succeed, got ${first.status}`);
      const renamed = await UserModel.findById(userId);
      if (renamed?.usernameHistory?.length !== 1 || renamed.usernameHistory[0].previousUsername !== originalUsername || renamed.usernameHistory[0].newUsername !== 'test_renamed' || !renamed.usernameHistory[0].eventId) {
        throw new Error('First rename did not create a complete server-side ownership event');
      }
      const second = await makeRequest('/api/v1/users/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${renameToken}` },
        body: JSON.stringify({ username: 'renamed_again' }),
      });
      if (second.status !== 403) throw new Error(`Expected six-month cooldown to return 403, got ${second.status}`);
      const unchanged = await UserModel.findById(userId);
      if (unchanged?.username !== 'test_renamed') throw new Error('Blocked rename still changed the public username');
    } finally {
      await UserModel.deleteOne({ _id: userId });
    }
  });

  await assertTest('Dynamic presence becomes Active in foreground and Offline in background', async () => {
    const active = await makeRequest('/api/v1/users/presence', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${validToken}` },
      body: JSON.stringify({ active: true }),
    });
    if (active.status !== 200) throw new Error(`Expected foreground presence update 200, got ${active.status}`);
    const activeProfile = await makeRequest(`/api/v1/users/public/id/${validUserPayload.id}`);
    const activeJson = await activeProfile.json() as any;
    if (activeJson.data?.status !== 'Active' || activeJson.data?.presenceStatus !== 'online') {
      throw new Error('Public profile did not show active dynamic presence');
    }
    const background = await makeRequest('/api/v1/users/presence', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${validToken}` },
      body: JSON.stringify({ active: false }),
    });
    if (background.status !== 200) throw new Error(`Expected background presence update 200, got ${background.status}`);
    const offlineProfile = await makeRequest(`/api/v1/users/public/id/${validUserPayload.id}`);
    const offlineJson = await offlineProfile.json() as any;
    if (offlineJson.data?.status !== 'Offline' || offlineJson.data?.presenceStatus !== 'offline') {
      throw new Error('Public profile did not show offline after backgrounding');
    }
  });

  // 8c. PROFILE ASSET AUTHORIZATION & OWNERSHIP ENFORCEMENT
  await assertTest('User B cannot delete User A profile asset (403)', async () => {
    // 1. Create asset for Alice
    const createRes = await makeRequest('/api/v1/users/me/assets', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({
        assetType: 'avatar',
        url: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/AP/EABQQAQAAAAAAAAAAAAAAAAAAACD/2gAIAQEAAQUCcf/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAACD/2gAIAQEABj8Cf//Z',
        name: 'alice_avatar',
      }),
    });
    if (createRes.status !== 201) throw new Error(`Failed to create Alice asset: ${createRes.status}`);
    const assetJson = (await createRes.json()) as any;
    const aliceAssetId = assetJson.data.id;

    // 2. Bob attempts to delete Alice's asset
    const deleteRes = await makeRequest(`/api/v1/users/me/assets/${aliceAssetId}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${bobToken}`,
      },
    });
    if (deleteRes.status !== 403) throw new Error(`Expected 403 when Bob attempts to delete Alice asset, got ${deleteRes.status}`);
  });

  await assertTest('Accepted friend can join invite-only room from persisted invitation', async () => {
    const room = await RoomModel.create({
      name: 'Invite authorization test',
      hostId: validUserPayload.id,
      privacy: 'invite_only',
      participants: [{
        userId: validUserPayload.id,
        displayName: validUserPayload.displayName,
        avatarLetter: 'A',
        isHost: true,
      }],
    });
    await FriendshipModel.create({
      requesterId: validUserPayload.id,
      recipientId: bobUserPayload.id,
      status: 'accepted',
    });

    const deniedJoin = await makeRequest(`/api/v1/rooms/${room.id}/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${bobToken}` },
    });
    if (deniedJoin.status !== 403) {
      throw new Error(`Expected uninvited join to be rejected with 403, got ${deniedJoin.status}`);
    }

    const deniedInvite = await makeRequest(`/api/v1/rooms/${room.id}/invite`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${bobToken}`,
      },
      body: JSON.stringify({ userId: validUserPayload.id }),
    });
    if (deniedInvite.status !== 403) {
      throw new Error(`Expected non-host invite to be rejected with 403, got ${deniedInvite.status}`);
    }

    const inviteRes = await makeRequest(`/api/v1/rooms/${room.id}/invite`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${validToken}`,
      },
      body: JSON.stringify({ userId: bobUserPayload.id }),
    });
    if (inviteRes.status !== 201) {
      throw new Error(`Expected invite creation 201, got ${inviteRes.status}`);
    }

    const persistedInvite = await NotificationModel.findOne({
      userId: bobUserPayload.id,
      type: 'room_invite',
      'actionData.roomId': room.id,
    });
    if (!persistedInvite) throw new Error('Room invitation was not persisted for the recipient');

    const joinRes = await makeRequest(`/api/v1/rooms/${room.id}/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${bobToken}` },
    });
    if (joinRes.status !== 200) throw new Error(`Expected invite-authorized join 200, got ${joinRes.status}`);
    const updatedRoom = await RoomModel.findById(room.id);
    if (!updatedRoom?.participants.some((participant) => participant.userId === bobUserPayload.id)) {
      throw new Error('Authorized participant membership was not persisted');
    }
  });

  await assertTest('Direct messages persist a clearable notification and retain media metadata', async () => {
    const sendRes = await makeRequest('/api/v1/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${bobToken}`,
      },
      body: JSON.stringify({
        conversationId: validUserPayload.id,
        text: 'DM notification check',
        messageType: 'media',
        mediaUrl: 'https://example.invalid/media.jpg',
        mediaMimeType: 'image/jpeg',
        isSpoiler: true,
      }),
    });
    if (sendRes.status !== 201) throw new Error(`Expected DM creation 201, got ${sendRes.status}`);
    const sendJson = (await sendRes.json()) as any;
    if (sendJson.data?.mediaMimeType !== 'image/jpeg' || sendJson.data?.isSpoiler !== true) {
      throw new Error('DM validation dropped attachment MIME type or spoiler state');
    }
    const notification = await NotificationModel.findOne({
      userId: validUserPayload.id,
      type: 'direct_message',
      'actionData.conversationId': bobUserPayload.id,
      'actionData.messageId': sendJson.data.id,
    });
    if (!notification) throw new Error('Incoming DM did not create a persistent notification');
    if (notification.expiresAt && notification.expiresAt.getTime() <= Date.now()) {
      throw new Error('DM notification expired immediately');
    }

    const basicEdit = await makeRequest(`/api/v1/chat/${sendJson.data.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bobToken}` },
      body: JSON.stringify({ text: 'Basic edit one' }),
    });
    if (basicEdit.status !== 200) throw new Error(`Basic user's first edit should pass, got ${basicEdit.status}`);
    const basicSecondEdit = await makeRequest(`/api/v1/chat/${sendJson.data.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bobToken}` },
      body: JSON.stringify({ text: 'Basic edit two' }),
    });
    if (basicSecondEdit.status !== 403) throw new Error(`Basic user's second edit should be rejected, got ${basicSecondEdit.status}`);

    const unsend = await makeRequest(`/api/v1/chat/${sendJson.data.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${bobToken}` },
    });
    if (unsend.status !== 200) throw new Error(`Fresh DM should be unsendable within two minutes, got ${unsend.status}`);
    const unsendJson = (await unsend.json()) as any;
    if (!unsendJson.data?.deletedAt || unsendJson.data?.mediaUrl) {
      throw new Error('Unsent DM did not become an attachment-free tombstone');
    }

    for (const tier of ['premium', 'premium_plus']) {
      await UserModel.updateOne({ _id: bobUserPayload.id }, { $set: { tier } });
      const create = await makeRequest('/api/v1/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bobToken}` },
        body: JSON.stringify({ conversationId: validUserPayload.id, text: `${tier} edit test` }),
      });
      if (create.status !== 201) throw new Error(`${tier} DM creation failed with ${create.status}`);
      const created = (await create.json()) as any;
      for (let edit = 1; edit <= 2; edit++) {
        const response = await makeRequest(`/api/v1/chat/${created.data.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bobToken}` },
          body: JSON.stringify({ text: `${tier} edit ${edit}` }),
        });
        if (response.status !== 200) throw new Error(`${tier} edit ${edit} should pass, got ${response.status}`);
      }
      const thirdEdit = await makeRequest(`/api/v1/chat/${created.data.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${bobToken}` },
        body: JSON.stringify({ text: `${tier} edit 3` }),
      });
      if (thirdEdit.status !== 403) throw new Error(`${tier} third edit should be rejected, got ${thirdEdit.status}`);
    }
  });

  await assertTest('Excessive OTP requests trigger rate limiting (429)', async () => {
    let got429 = false;
    for (let i = 0; i < 6; i++) {
      const res = await makeRequest('/api/v1/auth/otp/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: '+12025550199' }),
      });
      if (res.status === 429) {
        got429 = true;
        break;
      }
    }
    if (!got429) throw new Error('Expected 429 after repeated OTP requests');
  });

  await assertTest('OTP request never exposes the verification code', async () => {
    const res = await makeRequest('/api/v1/auth/otp/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: '+12025550200' }),
    });
    const json = (await res.json()) as any;
    if (Object.prototype.hasOwnProperty.call(json, 'debugOtp')) {
      throw new Error('OTP response exposes debugOtp');
    }
  });

  await assertTest('JWT secrets are explicitly configured', async () => {
    if (
      config.auth.jwtSecret === 'nebula_jwt_secret_dev_2026' ||
      config.auth.jwtRefreshSecret === 'nebula_jwt_refresh_secret_dev_2026'
    ) {
      throw new Error('JWT configuration uses a hardcoded development secret');
    }
  });

  // 10. HEALTH & READINESS ENDPOINTS
  await assertTest('GET /health returns healthy status without secrets', async () => {
    const res = await makeRequest('/health');
    if (res.status !== 200) throw new Error(`Expected 200, got ${res.status}`);
    const json = (await res.json()) as any;
    if (json.status !== 'healthy') throw new Error('Expected healthy status');
  });

  await assertTest('GET /ready returns readiness status', async () => {
    const res = await makeRequest('/ready');
    if (res.status !== 200 && res.status !== 503) throw new Error(`Expected 200 or 503, got ${res.status}`);
  });

  await assertTest('Private storage route is registered and rejects invalid media tokens', async () => {
    const res = await makeRequest('/storage/dGVzdC1rZXk?token=invalid');
    if (res.status !== 403) throw new Error(`Expected invalid media token to return 403, got ${res.status}`);
  });

  await assertTest('Legacy localhost object URLs normalize to the configured API origin', async () => {
    const resolved = resolveStoredObjectUrl('http://localhost:5001/storage/asset-key?token=signed');
    const output = new URL(resolved || '');
    const expected = new URL(config.server.apiBaseUrl);
    if (output.origin !== expected.origin || output.pathname !== '/storage/asset-key' || output.searchParams.get('token') !== 'signed') {
      throw new Error(`Legacy object URL did not preserve path/query at configured origin (${output.origin})`);
    }
  });

  // 11. SOCKET.IO UNAUTHORIZED CONNECTION REJECTION
  await assertTest('Socket.IO connection without token is rejected', async () => {
    await new Promise<void>((resolve, reject) => {
      const socket = ClientSocket(baseUrl, {
        transports: ['websocket'],
        reconnection: false,
      });

      socket.on('connect_error', (err) => {
        socket.disconnect();
        if (err.message.includes('Authentication required')) {
          resolve();
        } else {
          resolve(); // Any auth rejection is valid
        }
      });

      socket.on('connect', () => {
        socket.disconnect();
        reject(new Error('Unauthenticated socket connected successfully! Expected rejection.'));
      });
    });
  });

  // Teardown
  try {
    await UserModel.deleteMany({ username: { $in: ['test_alice', 'test_bob', 'test_rename', 'test_renamed', 'renamed_again'] } });
    await mongoose.disconnect();
    await testDatabase.stop();
  } catch {}

  await new Promise<void>((resolve) => server.close(() => resolve()));

  console.log('====================================================');
  console.log(`Audited: ${passed + failed} | Passed: ${passed} | Failed: ${failed}`);
  console.log('====================================================');

  if (failed > 0) {
    process.exit(1);
  } else {
    process.exit(0);
  }
}

runSecurityTests().catch((err) => {
  console.error('Fatal Test Runner Error:', err);
  process.exit(1);
});
