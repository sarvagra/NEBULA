import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { UserModel } from '../../models/User';
import {
  issueOtp,
  consumeOtp,
  clearOtp,
  peekOtpRequest,
} from '../auth/authController';
import { sendOtpEmail } from '../../services/emailService';
import { MessageModel } from '../../models/Message';
import { FriendshipModel } from '../../models/Friendship';
import { NotificationModel } from '../../models/Notification';
import { RefreshTokenModel } from '../../models/RefreshToken';
import { ProfileAssetModel } from '../../models/ProfileAsset';
import { MediaHistoryModel } from '../../models/MediaHistory';
import { AnalyticsEventModel } from '../../models/AnalyticsEvent';
import { RoomModel } from '../../models/Room';
import { PollModel } from '../../models/Poll';
import { deleteObjects, objectKeyFromAssetUrl } from '../../services/objectStorage';

/** Grace period between the OTP-verified request and irreversible deletion. */
export const DELETION_GRACE_DAYS = 30;
export const DELETION_GRACE_MS = DELETION_GRACE_DAYS * 24 * 60 * 60 * 1000;

const DELETION_PURPOSE = 'account_deletion' as const;
const OTP_MAX_ATTEMPTS = 5;

function newEventId(): string {
  return new mongoose.Types.ObjectId().toString();
}

function buildStatus(user: {
  deletionRequestedAt?: Date;
  deletionScheduledFor?: Date;
  deletionSuspendedAt?: Date;
}) {
  const pending = Boolean(user.deletionScheduledFor && !user.deletionSuspendedAt);
  return {
    pending,
    deletionRequestedAt: user.deletionRequestedAt ?? null,
    deletionScheduledFor: user.deletionScheduledFor ?? null,
    graceDays: DELETION_GRACE_DAYS,
    daysRemaining: pending && user.deletionScheduledFor
      ? Math.max(0, Math.ceil((new Date(user.deletionScheduledFor).getTime() - Date.now()) / (24 * 60 * 60 * 1000)))
      : 0,
  };
}

/** GET /users/me/deletion – current scheduled-deletion state. */
export async function getDeletionStatus(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const user = await UserModel.findById(req.user.id).select('deletionRequestedAt deletionScheduledFor deletionSuspendedAt');
    if (!user) { res.status(404).json({ success: false, error: 'User not found' }); return; }
    res.json({ success: true, data: buildStatus(user) });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to read account deletion status' });
  }
}

/**
 * POST /users/me/deletion/request – sends an OTP to the account email.
 * Deletion is never scheduled from this call alone.
 */
export async function requestAccountDeletion(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const user = await UserModel.findById(req.user.id).select('email');
    if (!user) { res.status(404).json({ success: false, error: 'User not found' }); return; }

    let target = (user.email || '').trim().toLowerCase();
    if (!target) {
      target = `account_${user._id}@nebula.local`;
    }

    const existing = peekOtpRequest(target, DELETION_PURPOSE);
    if (existing && Date.now() < existing.expiresAt && existing.attempts >= OTP_MAX_ATTEMPTS) {
      res.status(429).json({ success: false, error: 'Too many deletion codes requested. Please wait before retrying.' });
      return;
    }

    const code = issueOtp(target, DELETION_PURPOSE);
    const emailResult = await sendOtpEmail(target, code);
    if (!emailResult.sent) {
      clearOtp(target, DELETION_PURPOSE);
      res.status(503).json({
        success: false,
        error: 'Verification email could not be delivered. Please try again later.',
      });
      return;
    }

    res.json({
      success: true,
      message: 'Verification code sent to your account email',
      data: {
        email: target.replace(/^(.{2}).*(@.*)$/, '$1***$2'),
        graceDays: DELETION_GRACE_DAYS,
        devCode: (process.env.NODE_ENV === 'development' || !emailResult.deliveredToInbox) ? code : undefined,
      },
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to start account deletion' });
  }
}

/**
 * POST /users/me/deletion/confirm – OTP-verified request that schedules deletion
 * in 30 days. Logging in during the grace period suspends it.
 */
export async function confirmAccountDeletion(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const user = await UserModel.findById(req.user.id).select('email deletionScheduledFor deletionSuspendedAt');
    if (!user) { res.status(404).json({ success: false, error: 'User not found' }); return; }

    const target = (user.email || '').trim().toLowerCase() || `account_${user._id}@nebula.local`;
    const code = String(req.body?.otp || '').trim();
    if (!/^\d{6}$/.test(code)) {
      res.status(400).json({ success: false, error: 'A 6-digit verification code is required' });
      return;
    }

    if (!consumeOtp(target, code, DELETION_PURPOSE)) {
      res.status(400).json({ success: false, error: 'Invalid or expired verification code' });
      return;
    }

    const now = new Date();
    const scheduledFor = new Date(now.getTime() + DELETION_GRACE_MS);
    // `$set: undefined` does not remove a field, so the suspension marker is
    // cleared with an explicit $unset on every schedule.
    await UserModel.updateOne({ _id: req.user.id }, {
      $set: {
        deletionRequestedAt: now,
        deletionScheduledFor: scheduledFor,
      },
      $unset: { deletionSuspendedAt: 1 },
      $push: { deletionEvents: { action: 'requested', at: now, scheduledFor, eventId: newEventId() } },
    });

    res.json({
      success: true,
      message: `Account deletion scheduled for ${scheduledFor.toISOString()}. Sign in again within ${DELETION_GRACE_DAYS} days to cancel it.`,
      data: buildStatus({ deletionRequestedAt: now, deletionScheduledFor: scheduledFor }),
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to schedule account deletion' });
  }
}

/** POST /users/me/deletion/cancel – explicit cancel while still signed in. */
export async function cancelAccountDeletion(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const user = await UserModel.findById(req.user.id).select('deletionScheduledFor deletionSuspendedAt');
    if (!user) { res.status(404).json({ success: false, error: 'User not found' }); return; }

    if (!user.deletionScheduledFor) {
      res.json({ success: true, message: 'No account deletion was scheduled', data: buildStatus(user) });
      return;
    }

    const now = new Date();
    await UserModel.updateOne({ _id: req.user.id }, {
      $set: { deletionSuspendedAt: now },
      $unset: { deletionScheduledFor: 1 },
      $push: { deletionEvents: { action: 'cancelled', at: now, eventId: newEventId() } },
    });
    clearOtp(((user as any).email || '').trim().toLowerCase(), DELETION_PURPOSE);

    // Report the post-cancel state, not the document read before the update.
    res.json({
      success: true,
      message: 'Scheduled account deletion cancelled',
      data: buildStatus({ deletionScheduledFor: undefined, deletionSuspendedAt: now }),
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to cancel account deletion' });
  }
}

/**
 * Called after any successful sign-in. A login inside the grace period suspends
 * the pending deletion so the account survives, and the caller can tell the user.
 */
export async function suspendDeletionOnLogin(userId: string): Promise<{ suspended: boolean; scheduledFor?: Date }> {
  const user = await UserModel.findById(userId).select('deletionScheduledFor deletionSuspendedAt');
  if (!user?.deletionScheduledFor || user.deletionSuspendedAt) return { suspended: false };
  const now = new Date();
  await UserModel.updateOne({ _id: userId }, {
    $set: { deletionSuspendedAt: now },
    $unset: { deletionScheduledFor: 1 },
    $push: { deletionEvents: { action: 'suspended', at: now, eventId: newEventId() } },
  });
  return { suspended: true, scheduledFor: user.deletionScheduledFor };
}

/**
 * Removes every trace of a user, not just the profile document. Deleting only the
 * `users` row left their messages, friendships, notifications, sessions, uploaded
 * assets and room membership behind, which is the opposite of what "delete my
 * account" promises.
 */
export async function purgeUserData(
  userId: string,
  options: { deleteStorage?: boolean } = {}
): Promise<{ objectsScheduled: number }> {
  const [assets, messages, friendships, notifications, sessions, mediaHistory, analytics, rooms] = await Promise.all([
    ProfileAssetModel.find({ userId }).select('url'),
    MessageModel.deleteMany({ $or: [{ senderId: userId }, { conversationId: userId }] }),
    FriendshipModel.deleteMany({ $or: [{ requesterId: userId }, { recipientId: userId }] }),
    NotificationModel.deleteMany({ userId }),
    RefreshTokenModel.deleteMany({ userId }),
    MediaHistoryModel.deleteMany({ userId }),
    AnalyticsEventModel.deleteMany({ userId }),
    RoomModel.find({ $or: [{ hostId: userId }, { 'participants.userId': userId }] }).select('participants hostId'),
  ]);
  void messages; void friendships; void notifications; void sessions; void mediaHistory;

  // Rooms outlive a participant, so remove the membership instead of deleting the
  // room out from under everyone else. A room that only this user was in goes away,
  // and a room they hosted passes ownership on.
  for (const room of rooms) {
    room.participants = room.participants.filter((participant: any) => participant.userId !== userId);
    if (room.participants.length === 0) {
      await RoomModel.deleteOne({ _id: room._id });
      await PollModel.deleteMany({ roomId: room._id.toString() });
      continue;
    }
    if (room.hostId === userId) {
      room.hostId = room.participants[0].userId;
      room.kickedUserIds = [];
      room.participants.forEach((participant: any) => { participant.isHost = participant.userId === room.hostId; });
    }
    await room.save();
  }

  const keys = assets.map((asset) => objectKeyFromAssetUrl(asset.url)).filter(Boolean) as string[];
  if (options.deleteStorage !== false && keys.length > 0) {
    await deleteObjects(keys);
  }
  await ProfileAssetModel.deleteMany({ userId });
  void analytics;
  return { objectsScheduled: keys.length };
}

/**
 * Purges accounts whose 30-day grace period elapsed without a login.
 * Every candidate is re-checked immediately before deletion.
 */
export async function purgeDueAccountDeletions(now: Date = new Date()): Promise<number> {
  const due = await UserModel.find({
    deletionScheduledFor: { $lte: now, $ne: null },
    deletionSuspendedAt: { $exists: false },
  }).select('_id');

  let purged = 0;
  for (const candidate of due) {
    // Re-read so a concurrent login that suspended deletion wins the race.
    const fresh = await UserModel.findOne({
      _id: candidate._id,
      deletionScheduledFor: { $lte: now, $ne: null },
      deletionSuspendedAt: { $exists: false },
    }).select('_id');
    if (!fresh) continue;
    await purgeUserData(fresh._id.toString());
    await UserModel.deleteOne({ _id: fresh._id });
    purged++;
  }
  return purged;
}
