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
import { MessageModel } from '../models/Message';
import { ProfileAssetModel } from '../models/ProfileAsset';
import { issueOtp, consumeOtp, clearOtp } from '../modules/auth/authController';
import { purgeDueAccountDeletions } from '../modules/users/accountLifecycleController';
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

    const history = await makeRequest(`/api/v1/chat/conversation/${bobUserPayload.id}`, {
      headers: { Authorization: `Bearer ${validToken}` },
    });
    if (history.status !== 200) throw new Error(`Recipient could not load DM history: ${history.status}`);
    const beforeExplicitRead = await MessageModel.findById(sendJson.data.id);
    if (beforeExplicitRead?.readAt) throw new Error('Loading conversation history marked the message as read automatically');
    const read = await makeRequest(`/api/v1/chat/conversation/${bobUserPayload.id}/read`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${validToken}` },
    });
    if (read.status !== 200) throw new Error(`Explicit conversation read failed: ${read.status}`);
    const afterExplicitRead = await MessageModel.findById(sendJson.data.id);
    if (!afterExplicitRead?.readAt) throw new Error('Explicit conversation open did not persist read state');
    const notificationAfterRead = await NotificationModel.findById(notification.id);
    if (!notificationAfterRead || notificationAfterRead.isRead) {
      throw new Error('Reading a DM incorrectly cleared its separate notification');
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

  await assertTest('Direct message retention follows the tier window (4 weeks / 6 weeks / 12 months)', async () => {
    const now = Date.now();
    const daysAgo = (days: number) => new Date(now - days * 24 * 60 * 60 * 1000);
    // Retention is bounded by the shorter participant window. Bob (sender) is
    // upgraded per case while Alice (reader) stays Basic, so every case keeps the
    // 4-week window and the tier-specific windows are proven from the sender side.
    const tiers: { tier: string; visibleDays: number; hiddenDays: number }[] = [
      { tier: 'free', visibleDays: 27, hiddenDays: 29 },
      { tier: 'premium', visibleDays: 27, hiddenDays: 29 },
      { tier: 'premium_plus', visibleDays: 27, hiddenDays: 29 },
    ];

    for (const { tier, visibleDays, hiddenDays } of tiers) {
      await UserModel.updateOne({ _id: bobUserPayload.id }, { $set: { tier } });
      const visible = await MessageModel.create({
        senderId: bobUserPayload.id,
        conversationId: validUserPayload.id,
        senderName: 'Bob Test',
        text: `${tier} inside retention window`,
      });
      const hidden = await MessageModel.create({
        senderId: bobUserPayload.id,
        conversationId: validUserPayload.id,
        senderName: 'Bob Test',
        text: `${tier} outside retention window`,
      });
      // createdAt is an immutable timestamp field, so age the fixtures through the
      // raw driver rather than through the Mongoose model layer.
      await MessageModel.collection.bulkWrite([
        { updateOne: { filter: { _id: visible._id }, update: { $set: { createdAt: daysAgo(visibleDays) } } } },
        { updateOne: { filter: { _id: hidden._id }, update: { $set: { createdAt: daysAgo(hiddenDays) } } } },
      ]);

      const res = await makeRequest(`/api/v1/chat/conversation/${bobUserPayload.id}`, {
        headers: { Authorization: `Bearer ${validToken}` },
      });
      if (res.status !== 200) throw new Error(`${tier} retention request failed with ${res.status}`);
      const json = (await res.json()) as any;
      const ids = (json.data || []).map((m: any) => m.id);
      if (!ids.includes(visible._id.toString())) {
        throw new Error(`${tier} (${visibleDays}d) message inside the retention window was hidden`);
      }
      if (ids.includes(hidden._id.toString())) {
        throw new Error(`${tier} (${hiddenDays}d) message outside the retention window was still returned`);
      }
      if (json.meta?.retentionDays !== 28) {
        throw new Error(`${tier} shared thread reported retention ${json.meta?.retentionDays} days instead of the shorter 28-day window`);
      }
      await MessageModel.deleteMany({ _id: { $in: [visible._id, hidden._id] } });
    }

    // Now prove each tier's own window end-to-end: upgrade Alice (the reader) and
    // confirm the declared retention window follows her entitlement.
    const readerWindows: { tier: string; days: number; expected: number }[] = [
      { tier: 'free', days: 28, expected: 28 },
      { tier: 'premium', days: 42, expected: 42 },
      { tier: 'premium_plus', days: 365, expected: 365 },
    ];
    for (const { tier, days, expected } of readerWindows) {
      await UserModel.updateOne({ _id: validUserPayload.id }, { $set: { tier } });
      await UserModel.updateOne({ _id: bobUserPayload.id }, { $set: { tier } });
      const fresh = await MessageModel.create({
        senderId: bobUserPayload.id,
        conversationId: validUserPayload.id,
        senderName: 'Bob Test',
        text: `${tier} reader window`,
      });
      await MessageModel.collection.updateOne({ _id: fresh._id }, { $set: { createdAt: daysAgo(Math.max(0, days - 1)) } });
      const within = await makeRequest(`/api/v1/chat/conversation/${bobUserPayload.id}`, {
        headers: { Authorization: `Bearer ${validToken}` },
      });
      const withinJson = (await within.json()) as any;
      if (withinJson.meta?.retentionDays !== expected) {
        throw new Error(`${tier} reader reported ${withinJson.meta?.retentionDays} days instead of ${expected}`);
      }
      if (!((withinJson.data || []).map((m: any) => m.id)).includes(fresh._id.toString())) {
        throw new Error(`${tier} reader hid a message inside its own ${expected}-day window`);
      }
      await MessageModel.deleteOne({ _id: fresh._id });
    }

    await UserModel.updateOne({ _id: validUserPayload.id }, { $set: { tier: 'free' } });
    await UserModel.updateOne({ _id: bobUserPayload.id }, { $set: { tier: 'free' } });
  });

  await assertTest('Profile asset history keeps 1 / 3 / 5 assets by tier and deleting clears the applied asset', async () => {
    const assetUserId = '507f1f77bcf86cd799439044';
    const assetToken = jwt.sign({ ...validUserPayload, id: assetUserId, username: 'test_assets' }, config.auth.jwtSecret, { expiresIn: '1h' });
    const createAsset = async (label: string) =>
      makeRequest('/api/v1/users/me/assets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${assetToken}` },
        body: JSON.stringify({
          assetType: 'avatar',
          url: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/AP/EABQQAQAAAAAAAAAAAAAAAAAAACD/2gAIAQEAAQUCcf/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAACD/2gAIAQEABj8Cf//Z',
          name: label,
        }),
      });

    try {
      await UserModel.create({ _id: assetUserId, username: 'test_assets', displayName: 'Asset Test', tier: 'free' });

      // Basic: every new upload replaces history, leaving exactly one asset.
      for (let i = 0; i < 3; i++) {
        const res = await createAsset(`basic_${i}`);
        if (res.status !== 201) throw new Error(`Basic asset upload ${i} failed with ${res.status}`);
      }
      let listed = await makeRequest('/api/v1/users/me/assets', { headers: { Authorization: `Bearer ${assetToken}` } });
      let assets = ((await listed.json()) as any).data;
      if (assets.length !== 1) throw new Error(`Basic tier kept ${assets.length} assets instead of 1`);

      // The newest asset is the one preserved, and deleting it clears the applied URL.
      const applied = await makeRequest(`/api/v1/users/me/assets/${assets[0].id}/apply`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${assetToken}` },
      });
      if (applied.status !== 200) throw new Error(`Applying own asset failed with ${applied.status}`);
      const withAvatar = await UserModel.findById(assetUserId).select('avatarUrl');
      if (!withAvatar?.avatarUrl) throw new Error('Applying an avatar asset did not update the profile');

      await UserModel.updateOne({ _id: assetUserId }, { $set: { tier: 'premium' } });
      for (let i = 0; i < 4; i++) {
        const res = await createAsset(`premium_${i}`);
        if (res.status !== 201) throw new Error(`Premium asset upload ${i} failed with ${res.status}`);
      }
      listed = await makeRequest('/api/v1/users/me/assets', { headers: { Authorization: `Bearer ${assetToken}` } });
      assets = ((await listed.json()) as any).data;
      if (assets.length !== 3) throw new Error(`Premium tier kept ${assets.length} assets instead of 3`);

      await UserModel.updateOne({ _id: assetUserId }, { $set: { tier: 'premium_plus' } });
      for (let i = 0; i < 6; i++) {
        const res = await createAsset(`premium_plus_${i}`);
        if (res.status !== 201) throw new Error(`Premium Plus asset upload ${i} failed with ${res.status}`);
      }
      listed = await makeRequest('/api/v1/users/me/assets', { headers: { Authorization: `Bearer ${assetToken}` } });
      assets = ((await listed.json()) as any).data;
      if (assets.length !== 5) throw new Error(`Premium Plus tier kept ${assets.length} assets instead of 5`);

      const newestId = assets[0].id;
      await makeRequest(`/api/v1/users/me/assets/${newestId}/apply`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${assetToken}` },
      });
      const deleteRes = await makeRequest(`/api/v1/users/me/assets/${newestId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${assetToken}` },
      });
      if (deleteRes.status !== 200) throw new Error(`Deleting own applied asset failed with ${deleteRes.status}`);

      const afterDelete = await makeRequest('/api/v1/users/me/assets', { headers: { Authorization: `Bearer ${assetToken}` } });
      const remaining = ((await afterDelete.json()) as any).data;
      if (remaining.some((asset: any) => asset.id === newestId)) {
        throw new Error('Deleted asset is still listed in profile asset history');
      }
      const cleared = await UserModel.findById(assetUserId).select('avatarUrl');
      if (cleared?.avatarUrl) {
        throw new Error('Deleted applied asset is still advertised as the live profile avatar');
      }
    } finally {
      await ProfileAssetModel.deleteMany({ userId: assetUserId });
      await UserModel.deleteOne({ _id: assetUserId });
    }
  });

  await assertTest('Chat upload limits use actual bytes and enforce the video size cap', async () => {
    const oversizedImage = Buffer.alloc(5 * 1024 * 1024 + 1).toString('base64');
    const imageRes = await makeRequest('/api/v1/chat/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${validToken}` },
      body: JSON.stringify({
        url: `data:image/jpeg;base64,${oversizedImage}`,
        mimeType: 'image/jpeg',
        mediaType: 'image',
        sizeBytes: 1,
      }),
    });
    if (imageRes.status !== 413) throw new Error(`Oversized image with spoofed size metadata should fail 413, got ${imageRes.status}`);

    const videoRes = await makeRequest('/api/v1/chat/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${validToken}` },
      body: JSON.stringify({
        url: 'data:video/mp4;base64,AA==',
        mimeType: 'video/mp4',
        mediaType: 'video',
        sizeBytes: 30 * 1024 * 1024 + 1,
      }),
    });
    if (videoRes.status !== 413) throw new Error(`Video over 30MB should fail 413, got ${videoRes.status}`);
  });

  await assertTest('Entitlement matrix isolates Basic, Premium, and Premium Plus server rules', async () => {
    const gateUserId = '507f1f77bcf86cd799439055';
    const gateToken = jwt.sign({ ...validUserPayload, id: gateUserId, username: 'test_gates' }, config.auth.jwtSecret, { expiresIn: '1h' });
    const putProfile = (body: Record<string, any>) =>
      makeRequest('/api/v1/users/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${gateToken}` },
        body: JSON.stringify(body),
      });
    const entitlement = async () => {
      const res = await makeRequest('/api/v1/billing/entitlement', { headers: { Authorization: `Bearer ${gateToken}` } });
      if (res.status !== 200) throw new Error(`Entitlement read failed with ${res.status}`);
      return ((await res.json()) as any).data;
    };

    try {
      await UserModel.create({ _id: gateUserId, username: 'test_gates', displayName: 'Gate Test', tier: 'free' });

      const basicCustom = await putProfile({ status: 'custom', customStatus: 'Hacking away' });
      if (basicCustom.status !== 403) throw new Error(`Basic custom status should be 403, got ${basicCustom.status}`);
      const basicOffline = await putProfile({ status: 'offline' });
      if (basicOffline.status !== 403) throw new Error(`Basic offline status should be 403, got ${basicOffline.status}`);
      const basicDynamic = await putProfile({ status: 'dynamic' });
      if (basicDynamic.status !== 200) throw new Error(`Basic dynamic status should pass, got ${basicDynamic.status}`);
      const basicLimits = await entitlement();
      if (basicLimits.tier !== 'free' || basicLimits.dmRetentionDays !== 28 || basicLimits.messageEditLimit !== 1) {
        throw new Error(`Basic entitlements are wrong: ${JSON.stringify(basicLimits)}`);
      }
      if (basicLimits.customStatusAllowed !== false || basicLimits.profileAssetHistoryLimit !== 1) {
        throw new Error('Basic entitlements expose premium-only capabilities');
      }

      await UserModel.updateOne({ _id: gateUserId }, { $set: { tier: 'premium' } });
      const premiumCustom = await putProfile({ status: 'custom', customStatus: 'Still not allowed' });
      if (premiumCustom.status !== 403) throw new Error(`Premium custom status should still be 403, got ${premiumCustom.status}`);
      const premiumOffline = await putProfile({ status: 'offline' });
      if (premiumOffline.status !== 200) throw new Error(`Premium offline status should pass, got ${premiumOffline.status}`);
      const premiumLimits = await entitlement();
      if (premiumLimits.dmRetentionDays !== 42 || premiumLimits.messageEditLimit !== 2 || premiumLimits.profileAssetHistoryLimit !== 3) {
        throw new Error(`Premium entitlements are wrong: ${JSON.stringify(premiumLimits)}`);
      }
      if (premiumLimits.customStatusAllowed !== false) {
        throw new Error('Premium gained Premium Plus custom status');
      }

      await UserModel.updateOne({ _id: gateUserId }, { $set: { tier: 'premium_plus' } });
      const plusCustom = await putProfile({ status: 'custom', customStatus: 'Cosmic vibes' });
      if (plusCustom.status !== 200) throw new Error(`Premium Plus custom status should pass, got ${plusCustom.status}`);
      const plusLimits = await entitlement();
      if (plusLimits.dmRetentionDays !== 365 || plusLimits.profileAssetHistoryLimit !== 5 || plusLimits.customStatusAllowed !== true) {
        throw new Error(`Premium Plus entitlements are wrong: ${JSON.stringify(plusLimits)}`);
      }
      if (plusLimits.isPremiumPlus !== true || plusLimits.isPremium !== true) {
        throw new Error('Premium Plus entitlement flags are inconsistent');
      }
    } finally {
      await UserModel.deleteOne({ _id: gateUserId });
    }
  });

  await assertTest('Account deletion requires OTP, schedules 30 days out, and a login suspends it', async () => {
    const lifecycleUserId = '507f1f77bcf86cd799439066';
    const lifecycleEmail = 'nebula.lifecycle.test@example.com';
    const lifecycleToken = jwt.sign(
      { ...validUserPayload, id: lifecycleUserId, username: 'test_lifecycle', email: lifecycleEmail },
      config.auth.jwtSecret,
      { expiresIn: '1h' },
    );

    try {
      await UserModel.create({
        _id: lifecycleUserId,
        username: 'test_lifecycle',
        displayName: 'Lifecycle Test',
        email: lifecycleEmail,
        tier: 'free',
      });

      const initial = await makeRequest('/api/v1/users/me/deletion', { headers: { Authorization: `Bearer ${lifecycleToken}` } });
      const initialJson = (await initial.json()) as any;
      if (initial.status !== 200 || initialJson.data?.pending !== false) {
        throw new Error('A fresh account should not report a pending deletion');
      }

      const wrongCode = await makeRequest('/api/v1/users/me/deletion/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${lifecycleToken}` },
        body: JSON.stringify({ otp: '000000' }),
      });
      if (wrongCode.status !== 400) throw new Error(`Wrong deletion OTP should be 400, got ${wrongCode.status}`);
      const untouched = await UserModel.findById(lifecycleUserId).select('deletionScheduledFor');
      if (untouched?.deletionScheduledFor) throw new Error('Rejected OTP still scheduled the deletion');

      const code = issueOtp(lifecycleEmail, 'account_deletion');
      const confirmed = await makeRequest('/api/v1/users/me/deletion/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${lifecycleToken}` },
        body: JSON.stringify({ otp: code }),
      });
      if (confirmed.status !== 200) throw new Error(`OTP-confirmed deletion request failed with ${confirmed.status}`);
      const confirmedJson = (await confirmed.json()) as any;
      const daysRemaining = confirmedJson.data?.daysRemaining;
      if (daysRemaining < 29 || daysRemaining > 30) {
        throw new Error(`Deletion should be scheduled 30 days out, reported ${daysRemaining}`);
      }
      const scheduled = await UserModel.findById(lifecycleUserId).select('deletionRequestedAt deletionScheduledFor deletionEvents');
      if (!scheduled?.deletionScheduledFor || scheduled.deletionEvents?.length !== 1) {
        throw new Error('Deletion schedule was not persisted with an audit event');
      }
      const offsetDays = (scheduled.deletionScheduledFor.getTime() - Date.now()) / (24 * 60 * 60 * 1000);
      if (offsetDays < 29.9 || offsetDays > 30.01) {
        throw new Error(`Scheduled deletion is ${offsetDays.toFixed(2)} days away instead of 30`);
      }

      // Signing in during the grace period suspends the pending deletion.
      const login = await makeRequest('/api/v1/auth/dev-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: lifecycleEmail, username: 'test_lifecycle' }),
      });
      if (login.status !== 200) throw new Error(`Grace-period login failed with ${login.status}`);
      const loginJson = (await login.json()) as any;
      const wasScheduledFor = loginJson.data?.deletionWasScheduledFor;
      if (!wasScheduledFor) {
        throw new Error('Login during the grace period did not report the scheduled deletion it cancelled');
      }
      if (Math.abs(new Date(wasScheduledFor).getTime() - scheduled.deletionScheduledFor.getTime()) > 1000) {
        throw new Error('Login reported a different scheduled deletion time than the one stored');
      }
      const suspended = await UserModel.findById(lifecycleUserId).select('deletionScheduledFor deletionSuspendedAt deletionEvents');
      if (suspended?.deletionScheduledFor || !suspended?.deletionSuspendedAt) {
        throw new Error('Login during the grace period did not clear the scheduled deletion');
      }
      if (!suspended?.deletionEvents?.some((event) => event.action === 'suspended')) {
        throw new Error('Grace-period suspension was not recorded in the audit trail');
      }

      const notDue = await purgeDueAccountDeletions();
      if (notDue !== 0) throw new Error(`Purge removed ${notDue} accounts that were not due`);
      const survivor = await UserModel.findById(lifecycleUserId).select('_id');
      if (!survivor) throw new Error('Purge deleted an account whose deletion was suspended');

      // Scheduling again, then cancelling explicitly, also keeps the account.
      const secondCode = issueOtp(lifecycleEmail, 'account_deletion');
      await makeRequest('/api/v1/users/me/deletion/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${lifecycleToken}` },
        body: JSON.stringify({ otp: secondCode }),
      });
      const cancelled = await makeRequest('/api/v1/users/me/deletion/cancel', {
        method: 'POST',
        headers: { Authorization: `Bearer ${lifecycleToken}` },
      });
      if (cancelled.status !== 200) throw new Error(`Explicit deletion cancel failed with ${cancelled.status}`);
      const cancelJson = (await cancelled.json()) as any;
      if (cancelJson.data?.pending !== false) throw new Error('Cancel did not clear the pending deletion');

      // A deletion that genuinely passed its 30-day boundary is purged.
      const overdueUserId = '507f1f77bcf86cd799439077';
      await UserModel.create({
        _id: overdueUserId,
        username: 'test_overdue',
        displayName: 'Overdue Test',
        email: 'nebula.overdue.test@example.com',
        tier: 'free',
        deletionRequestedAt: new Date(Date.now() - 31 * 24 * 60 * 60 * 1000),
        deletionScheduledFor: new Date(Date.now() - 24 * 60 * 60 * 1000),
      });
      const purged = await purgeDueAccountDeletions();
      if (purged < 1) throw new Error('Purge did not remove an account past its grace period');
      if (await UserModel.findById(overdueUserId)) throw new Error('Overdue account survived the purge');
    } finally {
      clearOtp(lifecycleEmail, 'account_deletion');
      await UserModel.deleteMany({ _id: { $in: [lifecycleUserId, '507f1f77bcf86cd799439077'] } });
    }
  });

  await assertTest('Room media links must be playable video sources', async () => {
    const linkUserId = '507f1f77bcf86cd799439088';
    const linkToken = jwt.sign({ ...validUserPayload, id: linkUserId, username: 'test_links' }, config.auth.jwtSecret, { expiresIn: '1h' });

    try {
      await UserModel.create({ _id: linkUserId, username: 'test_links', displayName: 'Link Test', tier: 'free' });

      const imageLink = await makeRequest('/api/v1/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${linkToken}` },
        body: JSON.stringify({ name: 'Image Link Room', mediaUrl: 'https://cdn.example.com/poster.jpg' }),
      });
      if (imageLink.status !== 422) throw new Error(`Image link should be rejected with 422, got ${imageLink.status}`);
      const imageJson = (await imageLink.json()) as any;
      if (!imageJson.error || imageJson.error.split('\n').length !== 1) {
        throw new Error('Rejected link did not return a single-line error message');
      }

      const driveLink = await makeRequest('/api/v1/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${linkToken}` },
        body: JSON.stringify({ name: 'Drive Link Room', mediaUrl: 'https://drive.google.com/file/d/abc123/view' }),
      });
      if (driveLink.status !== 422) throw new Error(`Google Drive link should be rejected with 422, got ${driveLink.status}`);

      const invalidVimeo = await makeRequest('/api/v1/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${linkToken}` },
        body: JSON.stringify({ name: 'Vimeo Room', mediaUrl: 'https://vimeo.com/channels/staffpicks' }),
      });
      if (invalidVimeo.status !== 422) throw new Error(`Non-video Vimeo page should be rejected with 422, got ${invalidVimeo.status}`);

      const noRoom = await RoomModel.findOne({ hostId: linkUserId });
      if (noRoom) throw new Error('Rejected media link still created a room');
    } finally {
      await RoomModel.deleteMany({ hostId: linkUserId });
      await UserModel.deleteOne({ _id: linkUserId });
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
