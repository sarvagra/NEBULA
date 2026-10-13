import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { ProfileAssetModel } from '../../models/ProfileAsset';
import { UserModel } from '../../models/User';
import { uploadObject } from '../../services/objectStorage';

const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'video/webm'];
const MAX_FILE_SIZE_BYTES = 30 * 1024 * 1024;

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

    const resolvedMime = mimeType || 'image/jpeg';
    if (!ALLOWED_MIME_TYPES.includes(resolvedMime.toLowerCase())) {
      res.status(400).json({ success: false, error: 'Unsupported file type. Only JPEG, PNG, WEBP, and GIF are permitted.' });
      return;
    }

    const resolvedSize = Number(sizeBytes) || 0;
    const maxSize = resolvedMime.startsWith('video/') ? MAX_FILE_SIZE_BYTES : 5 * 1024 * 1024;
    if (resolvedSize > maxSize) {
      res.status(413).json({ success: false, error: `Asset exceeds maximum allowed file size of ${maxSize / (1024 * 1024)}MB.` });
      return;
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

      await UserModel.findByIdAndUpdate(req.user.id, updateFields);

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

      // Fall back to default if deleted
      if (asset.assetType === 'avatar') {
        await UserModel.findByIdAndUpdate(req.user.id, { avatarUrl: undefined });
      } else if (asset.assetType === 'banner') {
        await UserModel.findByIdAndUpdate(req.user.id, { bannerIndex: 0 });
      }

      await ProfileAssetModel.findByIdAndDelete(id);
      res.json({ success: true, message: 'Profile asset deleted from server storage' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to delete profile asset' });
  }
}
