import mongoose, { Schema, Document } from 'mongoose';

export interface IAnalyticsEvent extends Document {
  userId: string;
  eventName: string;
  metadata?: Record<string, any>;
  timestamp: Date;
}

const AnalyticsEventSchema = new Schema<IAnalyticsEvent>(
  {
    userId: { type: String, required: true, index: true },
    eventName: { type: String, required: true, index: true },
    metadata: { type: Schema.Types.Mixed },
    timestamp: { type: Date, default: Date.now },
  },
  {
    timestamps: false,
  }
);

export const AnalyticsEventModel = mongoose.model<IAnalyticsEvent>('AnalyticsEvent', AnalyticsEventSchema);
