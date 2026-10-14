import mongoose, { Schema, Document } from 'mongoose';

export type NotificationType = 'friend_request' | 'room_invite' | 'room_activity' | 'system';

export interface INotification extends Document {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  actionData?: Record<string, any>;
  isRead: boolean;
  expiresAt?: Date;
  createdAt: Date;
}

const NotificationSchema = new Schema<INotification>(
  {
    userId: { type: String, required: true, index: true },
    type: { type: String, enum: ['friend_request', 'room_invite', 'room_activity', 'system'], default: 'system' },
    title: { type: String, required: true },
    body: { type: String, required: true },
    actionData: { type: Schema.Types.Mixed },
    isRead: { type: Boolean, default: false },
    // Invite records remain visible until acted on, then expire server-side after five minutes.
    expiresAt: { type: Date, index: true },
  },
  {
    timestamps: { createdAt: true, updatedAt: false },
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

export const NotificationModel = mongoose.model<INotification>('Notification', NotificationSchema);
