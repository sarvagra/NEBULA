import mongoose, { Schema, Document } from 'mongoose';

export type MediaSourceType = 'youtube_video' | 'youtube_music' | 'public_link';

export interface IMediaHistoryItem {
  mediaId: string;
  title: string;
  channel?: string;
  thumbnailUrl?: string;
  sourceUrl?: string;
  sourceType: MediaSourceType;
  selectedAt: Date;
}

export interface IMediaHistory extends Document {
  userId: string;
  items: IMediaHistoryItem[];
  updatedAt: Date;
}

const MediaHistoryItemSchema = new Schema<IMediaHistoryItem>(
  {
    mediaId: { type: String, required: true },
    title: { type: String, required: true },
    channel: { type: String, default: '' },
    thumbnailUrl: { type: String, default: '' },
    sourceUrl: { type: String, default: '' },
    sourceType: {
      type: String,
      enum: ['youtube_video', 'youtube_music', 'public_link'],
      required: true,
    },
    selectedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const MediaHistorySchema = new Schema<IMediaHistory>(
  {
    userId: { type: String, required: true, unique: true, index: true },
    items: { type: [MediaHistoryItemSchema], default: [] },
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

export const MediaHistoryModel = mongoose.model<IMediaHistory>('MediaHistory', MediaHistorySchema);
