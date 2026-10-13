import http from 'http';
import { createApp } from './app';
import { connectDatabase } from './config/database';
import { initSocketServer } from './realtime/socketServer';
import { config } from './config/env';

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
}

bootstrap().catch((err) => {
  console.error('[Bootstrap Fatal Error]', err);
  process.exit(1);
});
