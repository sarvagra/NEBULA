import mongoose, { Schema, Document } from 'mongoose';

export type SubscriptionTier = 'free' | 'premium' | 'premium_plus';
export type UserPresenceStatus = 'dynamic' | 'offline' | 'custom';

export interface IUser extends Document {
  email?: string;
  phone?: string;
  googleId?: string;
  firebaseUid?: string;
  username: string;
  usernameChangedAt?: Date;
  usernameHistory: { previousUsername: string; newUsername: string; changedAt: Date; eventId: string }[];
  displayName: string;
  bio: string;
  avatarUrl?: string;
  avatarLetter: string;
  bannerIndex: number;
  bannerUrl?: string;
  isOnboardingCompleted?: boolean;
  dateOfBirth?: string;
  gender?: string;
  tier: SubscriptionTier;
  isTrial?: boolean;
  trialExpiresAt?: Date;
  status: UserPresenceStatus;
  presenceStatus?: 'online' | 'in_a_room' | 'private_chat' | 'offline';
  lastActiveAt?: Date;
  customStatus?: string;
  interests: string[];
  suggestionRefreshDate?: string;
  suggestionRefreshCount?: number;
  isFriendCountPublic: boolean;
  isBanned: boolean;
  role: 'user' | 'moderator' | 'admin';
  /** Account deletion lifecycle: scheduled grace period plus audit trail. */
  deletionRequestedAt?: Date;
  deletionScheduledFor?: Date;
  deletionSuspendedAt?: Date;
  deletionEvents: { action: 'requested' | 'suspended' | 'cancelled' | 'completed'; at: Date; scheduledFor?: Date; eventId: string }[];
  activeRoomId?: string | null;
  fcmTokens: string[];
  stats: {
    roomsJoinedCount: number;
    roomsHostedCount: number;
    timeInSyncMinutes: number;
  };
  createdAt: Date;
  updatedAt: Date;
}

const UserSchema = new Schema<IUser>(
  {
    email: { type: String, sparse: true, index: true },
    phone: { type: String, sparse: true, index: true },
    googleId: { type: String, sparse: true, index: true },
    firebaseUid: { type: String, sparse: true, index: true },
    username: { type: String, required: true, unique: true, index: true, lowercase: true, trim: true },
    usernameChangedAt: { type: Date },
    usernameHistory: [{
      previousUsername: { type: String, required: true },
      newUsername: { type: String, required: true },
      changedAt: { type: Date, required: true },
      eventId: { type: String, required: true },
    }],
    displayName: { type: String, default: '', trim: true },
    bio: { type: String, default: '' },
    avatarUrl: { type: String },
    avatarLetter: { type: String, default: 'N' },
    bannerIndex: { type: Number, default: 0 },
    bannerUrl: { type: String },
    isOnboardingCompleted: { type: Boolean, default: false },
    dateOfBirth: { type: String },
    gender: { type: String },
    tier: { type: String, enum: ['free', 'premium', 'premium_plus'], default: 'free' },
    isTrial: { type: Boolean, default: false },
    trialExpiresAt: { type: Date },
    status: { type: String, default: 'dynamic' },
    presenceStatus: { type: String, enum: ['online', 'in_a_room', 'private_chat', 'offline'], default: 'offline' },
    lastActiveAt: { type: Date },
    customStatus: { type: String, default: '' },
    interests: { type: [String], default: [] },
    suggestionRefreshDate: { type: String, default: '' },
    suggestionRefreshCount: { type: Number, default: 0 },
    isFriendCountPublic: { type: Boolean, default: true },
    isBanned: { type: Boolean, default: false, index: true },
    role: { type: String, enum: ['user', 'moderator', 'admin'], default: 'user' },
    deletionRequestedAt: { type: Date },
    deletionScheduledFor: { type: Date, index: true },
    deletionSuspendedAt: { type: Date },
    deletionEvents: [{
      action: { type: String, enum: ['requested', 'suspended', 'cancelled', 'completed'], required: true },
      at: { type: Date, required: true },
      scheduledFor: { type: Date },
      eventId: { type: String, required: true },
    }],
    activeRoomId: { type: String, default: null, index: true },
    fcmTokens: { type: [String], default: [] },
    stats: {
      roomsJoinedCount: { type: Number, default: 0 },
      roomsHostedCount: { type: Number, default: 0 },
      timeInSyncMinutes: { type: Number, default: 0 },
    },
  },
  {
    timestamps: true,
    toJSON: {
      virtuals: true,
      transform: (_, ret: any) => {
        ret.id = ret._id.toString();
        delete ret._id;
        delete ret.__v;
        return ret;
      },
    },
  }
);

// Indexed for username prefix lookup and name search. Username's unique index
// above remains the primary path for exact lookups.
UserSchema.index({ displayName: 'text', username: 'text' });

export const UserModel = mongoose.model<IUser>('User', UserSchema);
