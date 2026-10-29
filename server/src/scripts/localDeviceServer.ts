/**
 * TEMPORARY device-verification launcher.
 * Boots the real API + Socket.IO server against a throwaway in-memory MongoDB so
 * an emulator can exercise the app without touching the configured database.
 * Delete after verification; `npm run dev` remains the normal local entry point.
 */
import http from 'http';
import { MongoMemoryServer } from 'mongodb-memory-server';

async function main() {
  const mongo = await MongoMemoryServer.create();
  process.env.MONGODB_URI = mongo.getUri();
  process.env.MONGODB_DATABASE = 'nebula_local_device';
  // Test mode keeps OTP dispatch in-process (the code comes back in the response)
  // so device sign-in never mails a real address from this throwaway instance.
  process.env.NODE_ENV = 'test';
  process.env.BREVO_API_KEY = '';
  process.env.RESEND_API_KEY = '';
  process.env.OTP_PROVIDER = process.env.OTP_PROVIDER || 'local';
  // The emulator build signs with the shared client secret; keep the shield off
  // for this loopback-only instance so local captures are not blocked.
  process.env.APP_ATTESTATION_ENFORCEMENT = 'off';
  process.env.APP_CHECK_ENFORCEMENT = 'off';

  const { createApp } = await import('../app');
  const { initSocketServer } = await import('../realtime/socketServer');
  const mongoose = (await import('mongoose')).default;

  await mongoose.connect(mongo.getUri(), { dbName: 'nebula_local_device' });

  const server = http.createServer(createApp());
  initSocketServer(server);
  server.listen(5001, '0.0.0.0', () => {
    console.log('[LOCAL DEVICE SERVER] listening on http://0.0.0.0:5001 (in-memory database)');
  });

  // Verification-only helper: reads the pending login code out of the same
  // in-memory OTP store so an emulator sign-in can be completed without email.
  // Loopback only, exists solely in this throwaway script.
  const { peekOtpRequest } = await import('../modules/auth/authController');
  http.createServer((req, res) => {
    const url = new URL(req.url || '/', 'http://127.0.0.1');
    const email = (url.searchParams.get('email') || '').trim().toLowerCase();
    const record = email ? peekOtpRequest(email, 'login') : undefined;
    res.writeHead(record ? 200 : 404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(record ? { code: record.code, expiresAt: record.expiresAt } : {}));
  }).listen(5999, '127.0.0.1', () => {
    console.log('[LOCAL DEVICE SERVER] OTP peek helper on http://127.0.0.1:5999');
  });
}

main().catch((err) => {
  console.error('[LOCAL DEVICE SERVER] failed to start', err);
  process.exit(1);
});
