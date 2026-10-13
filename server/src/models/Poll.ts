import mongoose, { Schema, Document } from 'mongoose';

export interface IPollOption {
  id: string;
  text: string;
  votes: number;
  voterUserIds: string[];
}

export interface IPoll extends Document {
  roomId: string;
  creatorId: string;
  creatorName: string;
  question: string;
  options: IPollOption[];
  isActive: boolean;
  totalVotes: number;
  createdAt: Date;
}

const PollSchema = new Schema<IPoll>(
  {
    roomId: { type: String, required: true, index: true },
    creatorId: { type: String, required: true },
    creatorName: { type: String, required: true },
    question: { type: String, required: true },
    options: [
      {
        id: { type: String, required: true },
        text: { type: String, required: true },
        votes: { type: Number, default: 0 },
        voterUserIds: { type: [String], default: [] },
      },
    ],
    isActive: { type: Boolean, default: true },
    totalVotes: { type: Number, default: 0 },
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

export const PollModel = mongoose.model<IPoll>('Poll', PollSchema);
