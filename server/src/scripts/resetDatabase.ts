import mongoose from 'mongoose';
import { connectDatabase } from '../config/database';
import { UserModel } from '../models/User';
import { RoomModel } from '../models/Room';
import { ProfileAssetModel } from '../models/ProfileAsset';
import { MessageModel } from '../models/Message';
import { FriendshipModel } from '../models/Friendship';
import { RefreshTokenModel } from '../models/RefreshToken';
import { NotificationModel } from '../models/Notification';
import { PollModel } from '../models/Poll';

async function resetDb() {
  console.log('[Reset] Connecting to database...');
  const connected = await connectDatabase();
  if (!connected) {
    console.error('[Reset Error] Could not connect to database.');
    process.exit(1);
  }

  console.log('[Reset] Purging all collections for a fresh kickstart...');
  await UserModel.deleteMany({});
  await RoomModel.deleteMany({});
  await ProfileAssetModel.deleteMany({});
  await MessageModel.deleteMany({});
  await FriendshipModel.deleteMany({});
  await RefreshTokenModel.deleteMany({});
  await NotificationModel.deleteMany({});
  await PollModel.deleteMany({});

  console.log('✅ ALL PRE-EXISTING PROFILES, ROOMS, AND DATA DELETED SUCCESSFULLY.');
  await mongoose.disconnect();
  process.exit(0);
}

resetDb().catch((err) => {
  console.error('[Reset Exception]', err);
  process.exit(1);
});
