import mongoose from 'mongoose';
import { config } from './env';

export async function connectDatabase(): Promise<boolean> {
  let mongoUri = config.mongodb.uri;
  mongoUri = mongoUri.replace(/<([^>]+)>/g, '$1');

  try {
    console.log(`[Database] Connecting to MongoDB (database: ${config.mongodb.database})...`);

    await mongoose.connect(mongoUri, {
      dbName: config.mongodb.database,
      serverSelectionTimeoutMS: 5000,
    });

    console.log(`[Database] MongoDB connected successfully (database: ${mongoose.connection.name}).`);
    return true;
  } catch (err: any) {
    const rawMsg = err?.message || 'Connection failed';
    const sanitizedMsg = rawMsg
      .replace(/mongodb(\+srv)?:\/\/[^\s]+/gi, '[REDACTED_URI]')
      .replace(/:[^:@]+@/g, ':***@');
    console.warn(`[Database Warning] Could not connect to Atlas MongoDB: ${sanitizedMsg}`);

    return false;
  }
}

mongoose.connection.on('disconnected', () => {
  console.log('[Database] MongoDB disconnected.');
});

mongoose.connection.on('error', (err: any) => {
  const rawMsg = err?.message || 'Connection error';
  const sanitizedMsg = rawMsg
    .replace(/mongodb(\+srv)?:\/\/[^\s]+/gi, '[REDACTED_URI]')
    .replace(/:[^:@]+@/g, ':***@');
  console.error('[Database Error]', sanitizedMsg);
});
