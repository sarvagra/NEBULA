import { Request, Response } from 'express';
import { NotificationModel } from '../../models/Notification';
import { UserModel } from '../../models/User';
import { sendDeviceNotification } from '../../services/pushNotificationService';
import { resolveStoredObjectUrl } from '../../services/objectStorage';

export async function registerDeviceToken(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const token = typeof req.body?.token === 'string' ? req.body.token.trim() : '';
    if (token.length < 32 || token.length > 4096) { res.status(400).json({ success: false, error: 'Invalid device token' }); return; }
    await UserModel.updateOne({ _id: req.user.id }, { $addToSet: { fcmTokens: token } });
    res.json({ success: true });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to register notification device' });
  }
}

export async function notifyUser(userId: string, title: string, body: string, data: Record<string, string> = {}): Promise<void> {
  const user = await UserModel.findById(userId).select('fcmTokens').lean();
  if (user?.fcmTokens?.length) await sendDeviceNotification(user.fcmTokens, title, body, data);
}

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
      $or: [
        { expiresAt: { $gt: now } },
        { expiresAt: { $exists: false }, createdAt: { $gt: new Date(now.getTime() - 28 * 24 * 60 * 60 * 1000) } },
      ],
    })
      .sort({ createdAt: -1 })
      .limit(limit);

    // Resolve the actor behind each notification so the client can show who sent
    // it ("Sarvagra S sent you a friend request") instead of a generic icon.
    const actorIds = new Set<string>();
    for (const n of notifications) {
      for (const key of ['requesterId', 'senderId', 'userId', 'fromUserId']) {
        const value = (n.actionData as any)?.[key];
        if (typeof value === 'string' && /^[a-f0-9]{24}$/i.test(value)) actorIds.add(value);
      }
    }
    const actors = actorIds.size
      ? await UserModel.find({ _id: { $in: [...actorIds] } }).select('_id displayName username avatarUrl avatarLetter')
      : [];
    const actorById = new Map(actors.map((u) => [u._id.toString(), u]));

    res.json({
      success: true,
      data: notifications.map((n) => {
        const json = n.toJSON() as any;
        const action = (n.actionData || {}) as any;
        const actorId = ['requesterId', 'senderId', 'userId', 'fromUserId']
          .map((key) => action[key])
          .find((value) => typeof value === 'string' && actorById.has(value));
        if (actorId) {
          const actor = actorById.get(actorId)!;
          json.actor = {
            id: actor._id.toString(),
            displayName: actor.displayName || actor.username,
            username: actor.username,
            avatarUrl: resolveStoredObjectUrl(actor.avatarUrl),
            avatarLetter: actor.avatarLetter || (actor.username || 'U').charAt(0).toUpperCase(),
          };
        }
        return json;
      }),
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

export async function clearAllNotifications(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    await NotificationModel.deleteMany({ userId: req.user.id });
    res.json({ success: true, message: 'All notifications cleared' });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to clear notifications' });
  }
}

