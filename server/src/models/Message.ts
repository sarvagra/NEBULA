import mongoose, { Schema, Document } from 'mongoose';

export type MessageType = 'text' | 'media' | 'system';

export interface IMessage extends Document {
  roomId?: string;
  conversationId?: string;
  senderId: string;
  senderName: string;
  senderAvatarLetter: string;
  senderAvatarUrl?: string;
  text: string;
  mediaUrl?: string;
  messageType: MessageType;
  reactions: { emoji: string; count: number; userIds: string[] }[];
  createdAt: Date;
}

const MessageSchema = new Schema<IMessage>(
  {
    roomId: { type: String, index: true },
    conversationId: { type: String, index: true },
    senderId: { type: String, required: true, index: true },
    senderName: { type: String, required: true },
    senderAvatarLetter: { type: String, default: 'U' },
    senderAvatarUrl: { type: String },
    text: { type: String, default: '' },
    mediaUrl: { type: String },
    messageType: { type: String, enum: ['text', 'media', 'system'], default: 'text' },
    reactions: [
      {
        emoji: { type: String, required: true },
        count: { type: Number, default: 0 },
        userIds: { type: [String], default: [] },
      },
    ],
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

export const MessageModel = mongoose.model<IMessage>('Message', MessageSchema);
