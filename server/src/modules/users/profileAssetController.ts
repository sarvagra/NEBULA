import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { ProfileAssetModel } from '../../models/ProfileAsset';
import { UserModel } from '../../models/User';
import { uploadObject } from '../../services/objectStorage';
import { RoomModel } from '../../models/Room';
import { notifyRoomsParticipantProfileUpdated } from '../../realtime/socketServer';
import { profileAssetHistoryLimitForTier } from '../../utils/entitlements';
import { validateImageResolution } from '../../utils/imageSecurity';

const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'video/webm'];
const MAX_FILE_SIZE_BYTES = 30 * 1024 * 1024;

/**
 * Keep only the newest `limit` assets of one type for a user.
 * Basic keeps 1, Premium 3, Premium Plus 5.
 */
async function trimAssetHistory(userId: string, assetType: string, limit: number): Promise<void> {
  const stale = await ProfileAssetModel.find({ userId, assetType })
    .sort({ createdAt: -1 })
    .skip(limit)
    .select('_id');
  if (stale.length) {
    await ProfileAssetModel.deleteMany({ _id: { $in: stale.map((asset) => asset._id) } });
  }
}

/** Mirror an applied or cleared asset into the user record and live rooms. */
async function syncAppliedAsset(userId: string, fields: Record<string, any>): Promise<void> {
  // Explicit null removes the stored URL; `undefined` would be dropped by Mongoose.
  const patch: Record<string, any> = {};
  for (const [key, value] of Object.entries(fields)) patch[key] = value === undefined ? null : value;
  const updatedUser = await UserModel.findByIdAndUpdate(userId, patch, { new: true });
  if (!updatedUser) return;
  const participantUpdate: Record<string, any> = {};
  if ('avatarUrl' in patch) participantUpdate['participants.$.avatarUrl'] = updatedUser.avatarUrl ?? null;
  if ('bannerUrl' in patch) participantUpdate['participants.$.bannerUrl'] = updatedUser.bannerUrl ?? null;
  if (!Object.keys(participantUpdate).length) return;
  const rooms = await RoomModel.find({ 'participants.userId': userId }).select('_id');
  await RoomModel.updateMany({ 'participants.userId': userId }, { $set: participantUpdate });
  notifyRoomsParticipantProfileUpdated(rooms.map((room) => room._id.toString()), userId);
}

export async function listAssets(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    if (mongoose.connection.readyState !== 1) {
      res.status(503).json({ success: false, error: 'Database unavailable' });
      return;
    }
    const assets = await ProfileAssetModel.find({ userId: req.user.id }).sort({ createdAt: -1 });
    res.json({
      success: true,
      data: assets.map((a) => ({
        id: a._id.toString(), assetType: a.assetType, url: a.url, name: a.name,
        mimeType: a.mimeType, sizeBytes: a.sizeBytes, createdAt: a.createdAt,
      })),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve profile assets' });
  }
}

export async function uploadAsset(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { assetType, url, name, mimeType, sizeBytes } = req.body;

    if (!assetType || !['avatar', 'banner'].includes(assetType)) {
      res.status(400).json({ success: false, error: "Asset type must be 'avatar' or 'banner'" });
      return;
    }

    if (!url || typeof url !== 'string') {
      res.status(400).json({ success: false, error: 'Valid asset URL or data is required' });
      return;
    }

    let resolvedMime = mimeType;
    if (!resolvedMime && url.startsWith('data:')) {
      const dataMatch = url.match(/^data:([^;,]+);base64,/);
      if (dataMatch) {
        resolvedMime = dataMatch[1];
      }
    }
    if (!resolvedMime) {
      resolvedMime = 'image/jpeg';
    }

    if (!ALLOWED_MIME_TYPES.includes(resolvedMime.toLowerCase())) {
      res.status(400).json({ success: false, error: 'Unsupported file type. Only JPEG, PNG, WEBP, GIF, MP4, and WEBM are permitted.' });
      return;
    }

    const resolvedSize = Number(sizeBytes) || 0;
    const maxSize = resolvedMime.startsWith('video/') ? MAX_FILE_SIZE_BYTES : 5 * 1024 * 1024;
    if (resolvedSize > maxSize) {
      res.status(413).json({ success: false, error: `Asset exceeds maximum allowed file size of ${maxSize / (1024 * 1024)}MB.` });
      return;
    }

    if (url.startsWith('data:') && !resolvedMime.startsWith('video/')) {
      const match = url.match(/^data:([^;,]+);base64,([A-Za-z0-9+/]+={0,2})$/s);
      if (match) {
        const imageBuffer = Buffer.from(match[2], 'base64');
        const resolutionCheck = validateImageResolution(imageBuffer);
        if (!resolutionCheck.valid) {
          res.status(400).json({
            success: false,
            error: resolutionCheck.error || 'Image resolution exceeds maximum allowable dimensions (max 4096 x 4096 pixels)',
          });
          return;
        }
      }
    }

    if (mongoose.connection.readyState !== 1) {
      res.status(503).json({ success: false, error: 'Database unavailable' });
      return;
    }
    const storedUrl = await uploadObject(
      url,
      `users/${req.user.id}/profile/${assetType}/${Date.now()}_${(name || assetType).replace(/[^a-zA-Z0-9._-]/g, '_')}`,
      resolvedMime
    );
    const asset = await ProfileAssetModel.create({
        userId: req.user.id,
        assetType,
        url: storedUrl,
        name: (name || `${assetType}_${Date.now()}`).trim(),
        mimeType: resolvedMime,
        sizeBytes: resolvedSize,
      });

    // Tier policy: Basic keeps the newest 1 per type, Premium 3, Premium Plus 5.
    const uploader = await UserModel.findById(req.user.id).select('tier');
    const historyLimit = profileAssetHistoryLimitForTier(uploader?.tier);
    await trimAssetHistory(req.user.id, assetType, historyLimit);

    res.status(201).json({
        success: true,
        data: {
          id: asset._id.toString(),
          assetType: asset.assetType,
          url: asset.url,
          name: asset.name,
          mimeType: asset.mimeType,
          sizeBytes: asset.sizeBytes,
          createdAt: asset.createdAt,
          historyLimit,
        },
      });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to save profile asset' });
  }
}

export async function applyAsset(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;

    if (mongoose.connection.readyState !== 1) {
      res.status(503).json({ success: false, error: 'Database unavailable' });
      return;
    }
    const asset = await ProfileAssetModel.findById(id);
      if (!asset) {
        res.status(404).json({ success: false, error: 'Profile asset not found' });
        return;
      }

      if (asset.userId !== req.user.id && req.user.role !== 'admin') {
        res.status(403).json({ success: false, error: 'Forbidden: You do not own this profile asset' });
        return;
      }

      const updateFields: Record<string, any> = {};
      if (asset.assetType === 'avatar') {
        updateFields.avatarUrl = asset.url;
      } else if (asset.assetType === 'banner') {
        updateFields.bannerUrl = asset.url;
      }

      await syncAppliedAsset(req.user.id, updateFields);

      res.json({
        success: true,
        message: `Asset applied as current ${asset.assetType}`,
        asset: {
          id: asset._id.toString(),
          assetType: asset.assetType,
          url: asset.url,
        },
      });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to apply profile asset' });
  }
}

export async function deleteAsset(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;

    if (mongoose.connection.readyState !== 1) {
      res.status(503).json({ success: false, error: 'Database unavailable' });
      return;
    }
    const asset = await ProfileAssetModel.findById(id);
      if (!asset) {
        res.status(404).json({ success: false, error: 'Profile asset not found' });
        return;
      }

      if (asset.userId !== req.user.id && req.user.role !== 'admin') {
        res.status(403).json({ success: false, error: 'Forbidden: You do not own this profile asset' });
        return;
      }

      // Removing an asset also removes it from user-visible selection: when the
      // deleted object is the currently applied one, the profile falls back to
      // default rather than continuing to advertise a deleted URL.
      const appliedFields: Record<string, any> = {};
      if (asset.assetType === 'avatar') {
        const owner = await UserModel.findById(req.user.id).select('avatarUrl');
        if (!owner?.avatarUrl || owner.avatarUrl === asset.url) appliedFields.avatarUrl = null;
      } else if (asset.assetType === 'banner') {
        const owner = await UserModel.findById(req.user.id).select('bannerUrl');
        if (!owner?.bannerUrl || owner.bannerUrl === asset.url) {
          appliedFields.bannerUrl = null;
          appliedFields.bannerIndex = 0;
        }
      }

      await ProfileAssetModel.findByIdAndDelete(id);
      if (Object.keys(appliedFields).length) await syncAppliedAsset(req.user.id, appliedFields);
      res.json({ success: true, message: 'Profile asset removed from history and selection' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to delete profile asset' });
  }
}
