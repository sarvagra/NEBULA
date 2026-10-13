import mongoose from 'mongoose';
import { connectDatabase } from '../config/database';
import { config } from '../config/env';
import { UserModel } from '../models/User';
import { RoomModel } from '../models/Room';
import { MessageModel } from '../models/Message';
import { FriendshipModel } from '../models/Friendship';
import { NotificationModel } from '../models/Notification';
import { PollModel } from '../models/Poll';
import { RefreshTokenModel } from '../models/RefreshToken';
import { AnalyticsEventModel } from '../models/AnalyticsEvent';

async function runConnectivityTest() {
  console.log('=== NEBULA MONGODB ATLAS CONNECTIVITY TEST ===');
  console.log(`Target Database: ${config.mongodb.database}`);

  // 1. Establish connection using existing Mongoose configuration
  const connected = await connectDatabase();
  if (!connected || mongoose.connection.readyState !== 1) {
    console.error('FAILED: Could not establish MongoDB connection.');
    process.exit(1);
  }

  const activeDbName = mongoose.connection.name;
  console.log(`Connected to database: ${activeDbName}`);

  if (activeDbName !== config.mongodb.database) {
    console.error(`FAILED: Connected to unexpected database '${activeDbName}' (expected '${config.mongodb.database}')`);
    process.exit(1);
  }

  // 2. Ensure models and indexes are initialized
  console.log('Synchronizing and initializing indexes for models...');
  const models = [
    UserModel,
    RoomModel,
    MessageModel,
    FriendshipModel,
    NotificationModel,
    PollModel,
    RefreshTokenModel,
    AnalyticsEventModel,
  ];

  for (const model of models) {
    await model.init();
    const indexes = await model.collection.indexes();
    console.log(`- Model '${model.modelName}': verified ${indexes.length} index(es) in collection '${model.collection.name}'`);
  }
  console.log('All models and indexes initialized successfully.');

  // 3. Perform a safe minimal write-read-delete cycle
  console.log('Performing minimal read/write connectivity test...');
  const probeEventName = 'atlas_connectivity_probe_' + Date.now();
  const writeDoc = await AnalyticsEventModel.create({
    userId: 'system-connectivity-probe',
    eventName: probeEventName,
    metadata: { test: true, initiatedAt: new Date().toISOString() },
  });

  if (!writeDoc || !writeDoc._id) {
    console.error('FAILED: Write operation did not return a valid document ID.');
    process.exit(1);
  }
  console.log('Write test: PASSED (Document inserted successfully)');

  const readDoc = await AnalyticsEventModel.findOne({ _id: writeDoc._id, eventName: probeEventName });
  if (!readDoc) {
    console.error('FAILED: Read operation could not retrieve the inserted document.');
    process.exit(1);
  }
  console.log('Read test: PASSED (Document retrieved successfully)');

  const deleteResult = await AnalyticsEventModel.deleteOne({ _id: writeDoc._id });
  if (deleteResult.deletedCount !== 1) {
    console.error('FAILED: Delete operation could not clean up probe document.');
    process.exit(1);
  }
  console.log('Delete cleanup: PASSED (Probe document removed)');

  // 4. Clean shutdown of test connection
  await mongoose.disconnect();
  console.log('MongoDB disconnected cleanly.');
  console.log('=== ALL DATABASE CONNECTIVITY CHECKS PASSED ===');
  process.exit(0);
}

runConnectivityTest().catch((err: any) => {
  const sanitizedMsg = (err?.message || 'Unknown error')
    .replace(/mongodb(\+srv)?:\/\/[^\s]+/gi, '[REDACTED_URI]')
    .replace(/:[^:@]+@/g, ':***@');
  console.error('[Connectivity Test Error]', sanitizedMsg);
  process.exit(1);
});
