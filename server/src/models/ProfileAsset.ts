import mongoose, { Schema, Document } from 'mongoose';

export type AssetType = 'avatar' | 'banner';

export interface IProfileAsset extends Document {
  userId: string;
  assetType: AssetType;
  url: string;
  name: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: Date;
}

const ProfileAssetSchema = new Schema<IProfileAsset>(
  {
    userId: { type: String, required: true, index: true },
    assetType: { type: String, enum: ['avatar', 'banner'], required: true },
    url: { type: String, required: true },
    name: { type: String, required: true },
    mimeType: { type: String, default: 'image/jpeg' },
    sizeBytes: { type: Number, default: 0 },
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

export const ProfileAssetModel = mongoose.model<IProfileAsset>('ProfileAsset', ProfileAssetSchema);
