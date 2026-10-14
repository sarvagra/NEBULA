import http from 'http';
import { createApp } from './app';
import { connectDatabase } from './config/database';
import { initSocketServer } from './realtime/socketServer';
import { config } from './config/env';
import { purgeDueAccountDeletions } from './modules/users/accountLifecycleController';
import { runReengagementSweep } from './services/reengagementService';

async function bootstrap() {
  console.log('----------------------------------------------------');
  console.log(`🌌 Starting NEBULA Server [${config.server.nodeEnv}]...`);
  console.log('----------------------------------------------------');

  // 1. Connect MongoDB
  const databaseConnected = await connectDatabase();
  if (!databaseConnected) {
    throw new Error('MongoDB connection is required; refusing to start without the authoritative database');
  }

  // 2. Initialize Express application
  const app = createApp();

  // 3. Create HTTP Server
  const server = http.createServer(app);

  // 4. Attach Socket.io Realtime Server
  const io = initSocketServer(server);

  // 5. Start Listening
  const port = config.server.port;
  server.listen(port, () => {
    console.log(`🚀 NEBULA API is listening on http://localhost:${port}`);
    console.log(`📡 Realtime WebSocket server attached.`);
    console.log(`✨ Ready for Android client connections.`);
  });

  // 6. Purge accounts whose 30-day deletion grace period elapsed. The job re-checks
  // each candidate immediately before deleting, so a login during the window wins.
  const DELETION_SWEEP_MS = 6 * 60 * 60 * 1000;
  const runDeletionSweep = async () => {
    try {
      const purged = await purgeDueAccountDeletions();
      if (purged > 0) console.log(`[Lifecycle] Purged ${purged} account(s) past their deletion grace period`);
    } catch (err: any) {
      console.error('[Lifecycle] Deletion sweep failed:', err?.message || err);
    }
  };
  void runDeletionSweep();
  const deletionSweepTimer = setInterval(runDeletionSweep, DELETION_SWEEP_MS);
  deletionSweepTimer.unref?.();

  // 7. Controlled re-engagement sweep. The service only ever selects accounts that
  // explicitly opted in, and applies the cadence floor, per-campaign cooldowns and
  // quiet hours before anything is dispatched.
  const REENGAGEMENT_SWEEP_MS = 60 * 60 * 1000;
  const runReengagement = async () => {
    try {
      const result = await runReengagementSweep();
      if (result.sent > 0) console.log(`[Reengagement] Sent ${result.sent} opt-in message(s)`);
    } catch (err: any) {
      console.error('[Reengagement] Sweep failed:', err?.message || err);
    }
  };
  const reengagementTimer = setInterval(runReengagement, REENGAGEMENT_SWEEP_MS);
  reengagementTimer.unref?.();
}

bootstrap().catch((err) => {
  console.error('[Bootstrap Fatal Error]', err);
  process.exit(1);
});
