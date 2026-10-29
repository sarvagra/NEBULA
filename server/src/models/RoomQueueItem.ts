import mongoose, { Schema, Document } from 'mongoose';

/**
 * One queued media item in a room.
 *
 * The queue is deliberately a separate collection rather than an array on the room:
 * a room document is rewritten on every playback checkpoint (every 5 s while playing),
 * and a growing embedded array would make those writes larger and racier.
 */
export interface IRoomQueueItem extends Document {
  roomId: string;
  title: string;
  subtitle: string;
  mediaUrl: string;
  durationSeconds: number;
  addedByUserId: string;
  addedByName: string;
  /** Users who want to skip this item; the host's own vote counts too. */
  skipVoterIds: string[];
  createdAt: Date;
}

const RoomQueueItemSchema = new Schema<IRoomQueueItem>(
  {
    roomId: { type: String, required: true, index: true },
    title: { type: String, required: true, trim: true },
    subtitle: { type: String, default: '' },
    mediaUrl: { type: String, required: true },
    durationSeconds: { type: Number, default: 0 },
    addedByUserId: { type: String, required: true },
    addedByName: { type: String, default: 'Nebula User' },
    skipVoterIds: { type: [String], default: [] },
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

export const RoomQueueItemModel = mongoose.model<IRoomQueueItem>('RoomQueueItem', RoomQueueItemSchema);
