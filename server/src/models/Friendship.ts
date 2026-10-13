import mongoose, { Schema, Document } from 'mongoose';

export type FriendshipStatus = 'pending' | 'accepted' | 'declined' | 'blocked';

export interface IFriendship extends Document {
  requesterId: string;
  recipientId: string;
  status: FriendshipStatus;
  createdAt: Date;
  updatedAt: Date;
}

const FriendshipSchema = new Schema<IFriendship>(
  {
    requesterId: { type: String, required: true, index: true },
    recipientId: { type: String, required: true, index: true },
    status: { type: String, enum: ['pending', 'accepted', 'declined', 'blocked'], default: 'pending' },
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

FriendshipSchema.index({ requesterId: 1, recipientId: 1 }, { unique: true });

export const FriendshipModel = mongoose.model<IFriendship>('Friendship', FriendshipSchema);
