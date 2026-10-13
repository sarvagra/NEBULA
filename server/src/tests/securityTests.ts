import http from 'http';
import jwt from 'jsonwebtoken';
import { io as ClientSocket } from 'socket.io-client';
import { createApp } from '../app';
import { initSocketServer } from '../realtime/socketServer';
import { config } from '../config/env';
import { UserModel } from '../models/User';
import { RoomModel } from '../models/Room';
import { connectDatabase } from '../config/database';
import mongoose from 'mongoose';

// Standalone security test runner
async function runSecurityTests() {
  console.log('====================================================');
  console.log('🔒 RUNNING NEBULA BACKEND SECURITY AUDIT TEST SUITE');
  console.log('====================================================');

  await connectDatabase();

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
    await UserModel.deleteMany({ username: { $in: ['test_alice', 'test_bob'] } });
    await mongoose.disconnect();
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
