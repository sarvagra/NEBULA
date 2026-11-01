import mongoose, { Schema, Document } from 'mongoose';

export type RoomPrivacy = 'friends' | 'invite_only' | 'public';
export type RoomActivity = 'watch' | 'chat';

export interface IRoomParticipant {
  userId: string;
  username?: string;
  displayName: string;
  avatarLetter: string;
  avatarUrl?: string;
  bannerUrl?: string;
  bio?: string;
  status?: string;
  interests?: string[];
  joinedAt: Date;
  isHost: boolean;
  isMicMuted?: boolean;
}

export interface IRoomMediaState {
  title: string;
  subtitle: string;
  durationSeconds: number;
  playbackPosition: number;
  isPlaying: boolean;
  mediaUrl: string;
  version?: number;
  updatedAt?: Date;
}

export interface IRoomQueueItem {
  id: string;
  title: string;
  subtitle: string;
  mediaUrl: string;
  durationSeconds: number;
  addedBy: string;
  addedByName: string;
  addedAt: Date;
}

export interface IRoom extends Document {
  name: string;
  hostId: string;
  privacy: RoomPrivacy;
  activity: RoomActivity;
  category: string;
  tags: string[];
  isAgeRestricted: boolean;
  isEnded: boolean;
  mediaState: IRoomMediaState;
  queue: IRoomQueueItem[];
  leaderOnlyControls: boolean;
  votingEnabled: boolean;
  skipVotes: string[];
  likeVotes: string[];
  participants: IRoomParticipant[];
  invitedUserIds: string[];
  kickedUserIds: string[];
  /** Short, non-guessable code used to share a room without exposing its database id. */
  inviteCode?: string;
  maxParticipants: number;
  createdAt: Date;
  updatedAt: Date;
}

const RoomSchema = new Schema<IRoom>(
  {
    name: { type: String, required: true, trim: true },
    hostId: { type: String, required: true, index: true },
    privacy: { type: String, enum: ['friends', 'invite_only', 'public'], default: 'public' },
    activity: { type: String, enum: ['watch', 'chat'], default: 'watch' },
    category: { type: String, default: 'Cinema' },
    tags: { type: [String], default: [] },
    isAgeRestricted: { type: Boolean, default: false },
    isEnded: { type: Boolean, default: false, index: true },
    mediaState: {
      title: { type: String, default: 'YouTube Session' },
      subtitle: { type: String, default: 'Live Streaming' },
      durationSeconds: { type: Number, default: 0 },
      playbackPosition: { type: Number, default: 0 },
      isPlaying: { type: Boolean, default: false },
      mediaUrl: { type: String, default: '' },
      version: { type: Number, default: 0 },
      updatedAt: { type: Date, default: Date.now },
    },
    queue: [
      {
        id: { type: String, required: true },
        title: { type: String, default: 'Untitled' },
        subtitle: { type: String, default: '' },
        mediaUrl: { type: String, required: true },
        durationSeconds: { type: Number, default: 0 },
        addedBy: { type: String, required: true },
        addedByName: { type: String, default: 'Participant' },
        addedAt: { type: Date, default: Date.now },
      },
    ],
    leaderOnlyControls: { type: Boolean, default: true },
    votingEnabled: { type: Boolean, default: true },
    skipVotes: { type: [String], default: [] },
    likeVotes: { type: [String], default: [] },
    participants: [
      {
        userId: { type: String, required: true },
        username: { type: String },
        displayName: { type: String, required: true },
        avatarLetter: { type: String, default: 'U' },
        avatarUrl: { type: String },
        bannerUrl: { type: String },
        bio: { type: String, default: '' },
        status: { type: String, default: 'dynamic' },
        interests: { type: [String], default: [] },
        joinedAt: { type: Date, default: Date.now },
        isHost: { type: Boolean, default: false },
        isMicMuted: { type: Boolean, default: false },
      },
    ],
    invitedUserIds: { type: [String], default: [] },
    inviteCode: { type: String, unique: true, sparse: true, index: true },
    kickedUserIds: { type: [String], default: [] },
    maxParticipants: { type: Number, default: 50 },
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

export const RoomModel = mongoose.model<IRoom>('Room', RoomSchema);
