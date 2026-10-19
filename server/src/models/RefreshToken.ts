import mongoose, { Schema, Document } from 'mongoose';

export interface IRefreshToken extends Document {
  userId: string;
  tokenHash: string;
  deviceId?: string;
  deviceInfo?: string;
  ipAddress?: string;
  expiresAt: Date;
  lastActiveAt?: Date;
  revoked: boolean;
  revokedAt?: Date;
  createdAt: Date;
  updatedAt: Date;
}

const RefreshTokenSchema = new Schema<IRefreshToken>(
  {
    userId: { type: String, required: true, index: true },
    tokenHash: { type: String, required: true, unique: true, index: true },
    deviceId: { type: String, index: true },
    deviceInfo: { type: String },
    ipAddress: { type: String },
    expiresAt: { type: Date, required: true, index: { expires: 0 } }, // MongoDB TTL auto-cleanup
    lastActiveAt: { type: Date, default: Date.now },
    revoked: { type: Boolean, default: false, index: true },
    revokedAt: { type: Date },
  },
  {
    timestamps: true,
  }
);

export const RefreshTokenModel = mongoose.model<IRefreshToken>('RefreshToken', RefreshTokenSchema);
