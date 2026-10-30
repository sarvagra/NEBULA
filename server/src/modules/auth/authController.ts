import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { AuthService, hashToken } from '../../services/authService';
import { UserModel } from '../../models/User';
import { toPrivateUserDTO } from '../../utils/dto';
import { config } from '../../config/env';
import { disconnectSessionSockets } from '../../realtime/socketServer';

// Temporary in-memory OTP verification store (scoped to dev / OTP provider integration)
export type OtpPurpose = 'login' | 'account_deletion';
export interface OtpRecord {
  code: string;
  expiresAt: number;
  attempts: number;
  purpose: OtpPurpose;
}
const otpStore = new Map<string, OtpRecord>();

const OTP_TTL_MS = 10 * 60 * 1000;
const OTP_MAX_ATTEMPTS = 5;

function otpKey(target: string, purpose: OtpPurpose): string {
  return `${purpose}:${target}`;
}

/** Issue a purpose-scoped 6-digit code. Exported so lifecycle flows can reuse it. */
export function issueOtp(target: string, purpose: OtpPurpose = 'login'): string {
  const key = otpKey(target, purpose);
  const existing = otpStore.get(key);
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  otpStore.set(key, { code, expiresAt: Date.now() + OTP_TTL_MS, attempts: (existing?.attempts || 0) + 1, purpose });
  return code;
}

export function peekOtpRequest(target: string, purpose: OtpPurpose = 'login') {
  return otpStore.get(otpKey(target, purpose));
}

export function clearOtp(target: string, purpose: OtpPurpose = 'login'): void {
  otpStore.delete(otpKey(target, purpose));
}

/**
 * Any successful sign-in cancels a deletion that is still inside its grace
 * period. The caller passes the outcome back in the response so the app can
 * tell the user their account was saved.
 */
export async function applyLoginDeletionGrace(user: any): Promise<{ deletionSuspended: boolean; deletionScheduledFor: Date | null }> {
  if (!user?.deletionScheduledFor || user.deletionSuspendedAt) {
    return { deletionSuspended: false, deletionScheduledFor: null };
  }
  const scheduledFor = new Date(user.deletionScheduledFor);
  const now = new Date();
  await UserModel.updateOne({ _id: user._id }, {
    $set: { deletionSuspendedAt: now },
    $unset: { deletionScheduledFor: 1 },
    $push: { deletionEvents: { action: 'suspended', at: now, eventId: new mongoose.Types.ObjectId().toString() } },
  });
  user.deletionScheduledFor = undefined;
  user.deletionSuspendedAt = now;
  return { deletionSuspended: true, deletionScheduledFor: scheduledFor };
}

/** Consume a code, enforcing expiry. Returns false for wrong or expired codes. */
export function consumeOtp(target: string, code: string, purpose: OtpPurpose = 'login'): boolean {
  const key = otpKey(target, purpose);
  const record = otpStore.get(key);
  if (!record || record.code !== code || Date.now() > record.expiresAt) return false;
  otpStore.delete(key);
  return true;
}

export async function googleSignIn(req: Request, res: Response): Promise<void> {
  try {
    const { idToken } = req.body;
    const verified = await AuthService.verifyIdToken(idToken);

    // Identity is the Firebase UID. An email claim is mutable and must never be
    // used to resolve an account, or a verified email could be pointed at
    // someone else's profile.
    let user = await UserModel.findOne({
      $or: [
        { firebaseUid: verified.uid },
        { googleId: verified.uid },
      ],
    });

    if (!user) {
      // Auto-generate clean, unique handle: e.g. nebula_7x4k29
      let finalUsername = '';
      let isUnique = false;
      let attempts = 0;
      while (!isUnique && attempts < 15) {
        const rand = Math.random().toString(36).substring(2, 8); // 6 alphanumeric chars
        finalUsername = `nebula_${rand}`.toLowerCase();
        const existing = await UserModel.findOne({ username: finalUsername });
        if (!existing) {
          isUnique = true;
        }
        attempts++;
      }
      if (!isUnique) {
        finalUsername = `nebula_${Date.now().toString(36)}`;
      }

      // New accounts always start unprivileged. Admin/moderator roles are
      // granted through an audited administrative path, never by email match.
      user = await UserModel.create({
        email: verified.email,
        googleId: verified.uid,
        firebaseUid: verified.uid,
        displayName: verified.displayName || 'Nebula Explorer',
        username: finalUsername,
        avatarLetter: verified.displayName ? verified.displayName.charAt(0).toUpperCase() : 'N',
        avatarUrl: verified.picture,
        tier: 'free',
        role: 'user',
        status: 'dynamic',
        presenceStatus: 'online',
        bio: '',
        isOnboardingCompleted: false,
      });
    } else {
      if (!user.firebaseUid) {
        user.firebaseUid = verified.uid;
      }
      await user.save();
    }

    if (user.isBanned) {
      res.status(403).json({ success: false, error: 'Account has been suspended' });
      return;
    }

    const ip = req.ip || req.socket.remoteAddress;
    const deviceInfo = (req.body.deviceInfo || req.headers['x-device-info'] || req.headers['user-agent'] || 'Android Client').toString();
    const deviceId = (req.body.deviceId || req.headers['x-device-id'] || '').toString();
    const tokens = await AuthService.issueSessionTokens(
      user,
      deviceInfo,
      ip,
      deviceId,
      // Only an explicit opt-in may evict another device; the default stays a refusal.
      req.body.replaceOldestDevice === true
    );
    const grace = await applyLoginDeletionGrace(user);

    res.json({
      success: true,
      data: {
        user: toPrivateUserDTO(user),
        tokens,
        deletionSuspended: grace.deletionSuspended,
        deletionWasScheduledFor: grace.deletionScheduledFor,
      },
    });
  } catch (err: any) {
    const status = err.statusCode || 401;
    res.status(status).json({
      success: false,
      error: err.message || 'Google authentication failed',
      code: err.code || 'AUTH_FAILED',
    });
  }
}

export async function refreshToken(req: Request, res: Response): Promise<void> {
  try {
    const { refreshToken } = req.body;
    const ip = req.ip || req.socket.remoteAddress;
    const { token, user } = await AuthService.refreshSession(refreshToken, ip);

    res.json({
      success: true,
      data: {
        token,
        user: toPrivateUserDTO(user),
      },
    });
  } catch (err: any) {
    res.status(401).json({ success: false, error: err.message || 'Session refresh failed' });
  }
}

export async function logout(req: Request, res: Response): Promise<void> {
  try {
    const { refreshToken, deviceId } = req.body;
    const targetDeviceId = deviceId || (req.headers['x-device-id'] as string) || req.user?.deviceId;
    const targetSessionId = req.user?.sessionId;

    await AuthService.revokeSession({
      rawRefreshToken: refreshToken,
      sessionId: targetSessionId,
      deviceId: targetDeviceId,
      userId: req.user?.id,
    });
    // Drop the live socket now as well. Signing out only in Mongo left the device
    // receiving room and DM events until its access token expired.
    if (req.user?.id) {
      await disconnectSessionSockets(req.user.id, req.user.sessionId);
    }
    res.json({ success: true, message: 'Logged out successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Logout failed' });
  }
}

export async function getMe(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const user = await UserModel.findById(req.user.id);
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    res.json({ success: true, data: toPrivateUserDTO(user) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve profile' });
  }
}

export async function getActiveSessions(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const currentDeviceId = (req.headers['x-device-id'] as string) || req.user.deviceId;
    const currentSessionId = req.user.sessionId;
    const sessions = await AuthService.getActiveSessions(req.user.id, currentDeviceId, currentSessionId);

    res.json({
      success: true,
      data: sessions,
      maxAllowedDevices: 2,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve active sessions' });
  }
}

export async function revokeSessionById(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { sessionId } = req.params;
    if (!sessionId) {
      res.status(400).json({ success: false, error: 'Session ID is required' });
      return;
    }

    const revoked = await AuthService.revokeSessionById(req.user.id, sessionId);
    if (!revoked) {
      res.status(404).json({ success: false, error: 'Session not found or already revoked' });
      return;
    }

    // The revoked phone must lose its live connection immediately, not at the next
    // token expiry.
    await disconnectSessionSockets(req.user.id, sessionId);

    res.json({ success: true, message: 'Device session revoked successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to revoke device session' });
  }
}

export async function revokeOtherSessions(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const currentDeviceId = (req.headers['x-device-id'] as string) || req.user.deviceId;
    const currentSessionId = req.user.sessionId;
    const count = await AuthService.revokeOtherSessions(req.user.id, currentDeviceId, currentSessionId);
    // Every socket from the other devices goes with them; this one stays.
    if (count > 0) {
      await disconnectSessionSockets(req.user.id, undefined, currentSessionId);
    }

    res.json({
      success: true,
      message: count > 0 ? `Logged out of ${count} other device(s)` : 'No other active devices',
      revokedCount: count,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to revoke other sessions' });
  }
}

