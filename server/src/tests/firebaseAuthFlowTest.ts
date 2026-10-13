import http from 'http';
import mongoose from 'mongoose';
import jwt from 'jsonwebtoken';
import { getAuth } from 'firebase-admin/auth';
import { createApp } from '../app';
import { connectDatabase } from '../config/database';
import { config } from '../config/env';
import { getFirebaseAdmin, FirebaseService } from '../services/firebaseService';
import { UserModel } from '../models/User';
import { RefreshTokenModel } from '../models/RefreshToken';

async function runFirebaseAuthFlowTest() {
  console.log('================================================================');
  console.log('🔥 NEBULA END-TO-END FIREBASE AUTHENTICATION VERIFICATION SUITE');
  console.log('================================================================');

  // 1. Connect MongoDB
  await connectDatabase();
  if (mongoose.connection.readyState !== 1) {
    throw new Error('MongoDB connection is not ready');
  }

  // 2. Start HTTP test server on isolated port
  const app = createApp();
  const server = http.createServer(app);
  const TEST_PORT = 5088;
  await new Promise<void>((resolve) => server.listen(TEST_PORT, resolve));
  const baseUrl = `http://127.0.0.1:${TEST_PORT}`;

  const cleanupUserIds: string[] = [];

  try {
    // 3. Step 1: Initialize Firebase Admin SDK & verify configuration
    console.log('[Step 1] Initializing Firebase Admin SDK...');
    const fbApp = getFirebaseAdmin();
    console.log(`- Firebase App initialized: ${fbApp.name}`);
    console.log(`- Project ID: ${config.firebase.projectId}`);
    if (!config.firebase.clientEmail || !config.firebase.privateKey) {
      throw new Error('Firebase service account credentials missing from environment');
    }
    console.log('✅ Firebase Admin SDK successfully configured with service account');

    // 4. Step 2: Acquire authentic Firebase ID token for test user
    console.log('[Step 2] Minting test user and obtaining Google-verified Firebase ID token...');
    const auth = getAuth(fbApp);
    const testUid = `test_firebase_user_${Date.now()}`;
    const testEmail = `test_${Date.now()}@nebula.social`;
    const customToken = await auth.createCustomToken(testUid, {
      displayName: 'Nebula Test Explorer',
      email: testEmail,
    });

    const apiKey = config.firebase.webApiKey || process.env.FIREBASE_WEB_API_KEY;
    if (!apiKey) {
      throw new Error('FIREBASE_WEB_API_KEY is not configured in server/.env');
    }

    const exchangeRes = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: customToken, returnSecureToken: true }),
      }
    );

    const exchangeData: any = await exchangeRes.json();
    if (!exchangeData.idToken) {
      throw new Error(`Failed to exchange custom token with Google: ${JSON.stringify(exchangeData)}`);
    }
    const genuineIdToken = exchangeData.idToken;
    console.log('✅ Received genuine cryptographic Firebase ID token from Google servers');

    // 5. Step 3: Verify Firebase ID token with Firebase Admin SDK
    console.log('[Step 3] Cryptographically verifying ID token using Firebase Admin SDK...');
    const verifiedToken = await FirebaseService.verifyIdToken(genuineIdToken);
    console.log(`- Decoded UID: ${verifiedToken.uid}`);
    if (verifiedToken.uid !== testUid) {
      throw new Error(`UID mismatch: expected ${testUid}, got ${verifiedToken.uid}`);
    }
    console.log('✅ Firebase Admin SDK verified signature, project ID, and expiration');

    // 6. Step 4: Call Nebula Backend /api/v1/auth/google endpoint with genuine Firebase ID token
    console.log('[Step 4] Calling Nebula /api/v1/auth/google endpoint...');
    const authRes = await fetch(`${baseUrl}/api/v1/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: genuineIdToken }),
    });

    if (authRes.status !== 200) {
      const errText = await authRes.text();
      throw new Error(`Expected 200 OK from /auth/google, got ${authRes.status}: ${errText}`);
    }

    const authJson: any = await authRes.json();
    if (!authJson.success || !authJson.data?.tokens?.token) {
      throw new Error('Authentication response missing session token');
    }

    const nebulaSessionToken = authJson.data.tokens.token;
    const returnedUser = authJson.data.user;
    cleanupUserIds.push(returnedUser.id);
    console.log(`- Nebula User resolved: ${returnedUser.username} (ID: ${returnedUser.id})`);
    console.log('✅ Authoritative Nebula session JWT and refresh token issued');

    // 7. Step 5: Verify MongoDB user persistence and identity attribution
    console.log('[Step 5] Verifying MongoDB persistence...');
    const dbUser = await UserModel.findById(returnedUser.id);
    if (!dbUser) {
      throw new Error('User record not found in MongoDB');
    }
    if (dbUser.firebaseUid !== testUid) {
      throw new Error(`User firebaseUid mismatch in DB: expected ${testUid}, got ${dbUser.firebaseUid}`);
    }
    console.log(`- Confirmed User in database '${mongoose.connection.name}' with firebaseUid: ${dbUser.firebaseUid}`);
    console.log('✅ User identity authoritatively linked to Firebase UID');

    // 8. Step 6: Verify accessing protected endpoint with newly issued Nebula session JWT
    console.log('[Step 6] Testing access to protected endpoint /api/v1/auth/me...');
    const meRes = await fetch(`${baseUrl}/api/v1/auth/me`, {
      headers: { Authorization: `Bearer ${nebulaSessionToken}` },
    });
    if (meRes.status !== 200) {
      throw new Error(`Protected endpoint failed with status ${meRes.status}`);
    }
    const meJson: any = await meRes.json();
    const meUserId = meJson.data?.id || meJson.data?.user?.id;
    if (meUserId !== returnedUser.id) {
      throw new Error(`Protected endpoint returned unexpected user identity: expected ${returnedUser.id}, got ${meUserId}`);
    }
    console.log('✅ Session JWT authoritatively accepted at protected endpoints');

    // 9. Step 7: Security check - Verify forged/fake JWT is rejected by /auth/google
    console.log('[Step 7] Testing rejection of forged / modified JWTs...');
    const forgedToken = jwt.sign(
      { uid: 'forged_admin_uid', email: 'forged@admin.org', sub: 'forged_admin_uid' },
      'wrong_untrusted_secret_key',
      { expiresIn: '1h' }
    );

    const fakeRes = await fetch(`${baseUrl}/api/v1/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: forgedToken }),
    });

    if (fakeRes.status !== 401) {
      throw new Error(`Security vulnerability: Forged JWT returned HTTP ${fakeRes.status} instead of 401!`);
    }
    console.log('✅ Forged/self-signed token rejected with HTTP 401 Unauthorized');

    // 10. Step 8: Security check - Verify mock_dev_ token is rejected
    console.log('[Step 8] Testing rejection of mock_dev_ token in standard API flow...');
    const mockRes = await fetch(`${baseUrl}/api/v1/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: 'mock_dev_test_token' }),
    });

    if (mockRes.status !== 401) {
      throw new Error(`Security vulnerability: mock_dev_ token returned HTTP ${mockRes.status} instead of 401!`);
    }
    console.log('✅ mock_dev_ token strictly rejected with HTTP 401 Unauthorized');

    console.log('================================================================');
    console.log('🎉 ALL FIREBASE AUTHENTICATION TESTS PASSED SUCCESSFULLY');
    console.log('================================================================');
    process.exit(0);
  } finally {
    // Cleanup test users from DB
    for (const uid of cleanupUserIds) {
      await UserModel.findByIdAndDelete(uid);
      await RefreshTokenModel.deleteMany({ userId: uid });
    }
    await new Promise<void>((resolve) => server.close(() => resolve()));
    await mongoose.disconnect();
  }
}

runFirebaseAuthFlowTest().catch((err) => {
  console.error('[Firebase Auth Flow Test Error]', err.message);
  process.exit(1);
});
