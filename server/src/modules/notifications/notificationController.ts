import { Request, Response } from 'express';
import { NotificationModel } from '../../models/Notification';

export async function listNotifications(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 20, 1), 50);

    const now = new Date();
    await NotificationModel.deleteMany({ userId: req.user.id, expiresAt: { $lte: now } });
    const notifications = await NotificationModel.find({
      userId: req.user.id,
      $or: [{ expiresAt: { $exists: false } }, { expiresAt: { $gt: now } }],
    })
      .sort({ createdAt: -1 })
      .limit(limit);

    res.json({
      success: true,
      data: notifications.map((n) => n.toJSON()),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve notifications' });
  }
}

export async function markAsRead(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const updated = await NotificationModel.findOneAndUpdate(
      { _id: id, userId: req.user.id },
      { isRead: true },
      { new: true }
    );

    if (!updated) {
      res.status(404).json({ success: false, error: 'Notification not found or unauthorized' });
      return;
    }

    res.json({ success: true, message: 'Notification marked as read' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to update notification' });
  }
}

export async function deleteNotification(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const deleted = await NotificationModel.findOneAndDelete({ _id: id, userId: req.user.id });

    if (!deleted) {
      res.status(404).json({ success: false, error: 'Notification not found or unauthorized' });
      return;
    }

    res.json({ success: true, message: 'Notification deleted' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to delete notification' });
  }
}
