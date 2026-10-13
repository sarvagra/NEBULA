import mongoose, { Schema, Document } from 'mongoose';

export type RoomPrivacy = 'friends' | 'invite_only' | 'public';
export type RoomActivity = 'watch' | 'chat' | 'voice';

export interface IRoomParticipant {
  userId: string;
  displayName: string;
  avatarLetter: string;
  avatarUrl?: string;
  joinedAt: Date;
  isHost: boolean;
  isMuted?: boolean;
  isCameraOff?: boolean;
}

export interface IRoomMediaState {
  title: string;
  subtitle: string;
  durationSeconds: number;
  playbackPosition: number;
  isPlaying: boolean;
  mediaUrl: string;
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
  participants: IRoomParticipant[];
  kickedUserIds: string[];
  maxParticipants: number;
  isMicAllowed: boolean;
  isVideoAllowed: boolean;
  createdAt: Date;
  updatedAt: Date;
}

const RoomSchema = new Schema<IRoom>(
  {
    name: { type: String, required: true, trim: true },
    hostId: { type: String, required: true, index: true },
    privacy: { type: String, enum: ['friends', 'invite_only', 'public'], default: 'public' },
    activity: { type: String, enum: ['watch', 'chat', 'voice'], default: 'watch' },
    category: { type: String, default: 'Cinema' },
    tags: { type: [String], default: [] },
    isAgeRestricted: { type: Boolean, default: false },
    isEnded: { type: Boolean, default: false, index: true },
    isMicAllowed: { type: Boolean, default: true },
    isVideoAllowed: { type: Boolean, default: true },
    mediaState: {
      title: { type: String, default: 'YouTube Session' },
      subtitle: { type: String, default: 'Live Streaming' },
      durationSeconds: { type: Number, default: 0 },
      playbackPosition: { type: Number, default: 0 },
      isPlaying: { type: Boolean, default: false },
      mediaUrl: { type: String, default: '' },
    },
    participants: [
      {
        userId: { type: String, required: true },
        displayName: { type: String, required: true },
        avatarLetter: { type: String, default: 'U' },
        avatarUrl: { type: String },
        joinedAt: { type: Date, default: Date.now },
        isHost: { type: Boolean, default: false },
        isMuted: { type: Boolean, default: false },
        isCameraOff: { type: Boolean, default: false },
      },
    ],
    // A host kick blocks re-entry until the room changes host or is removed.
    kickedUserIds: { type: [String], default: [] },
    maxParticipants: { type: Number, default: 25 },
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
