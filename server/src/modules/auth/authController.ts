import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { AuthService, hashToken } from '../../services/authService';
import { UserModel } from '../../models/User';
import { toPrivateUserDTO } from '../../utils/dto';
import { config } from '../../config/env';
import { sendOtpEmail } from '../../services/emailService';

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
async function applyLoginDeletionGrace(user: any): Promise<{ deletionSuspended: boolean; deletionScheduledFor: Date | null }> {
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

    let user = await UserModel.findOne({
      $or: [
        { firebaseUid: verified.uid },
        { googleId: verified.uid },
        { email: verified.email },
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

      const isSpecialAdmin = verified.email === 'sarvagrawork@gmail.com' || verified.email === 'sarvagra2@gmail.com';

      user = await UserModel.create({
        email: verified.email,
        googleId: verified.uid,
        firebaseUid: verified.uid,
        displayName: verified.displayName || 'Nebula Explorer',
        username: finalUsername,
        avatarLetter: verified.displayName ? verified.displayName.charAt(0).toUpperCase() : 'N',
        avatarUrl: verified.picture,
        tier: isSpecialAdmin ? 'premium_plus' : 'free',
        role: isSpecialAdmin ? 'admin' : 'user',
        status: 'dynamic',
        presenceStatus: 'online',
        bio: '',
        isOnboardingCompleted: isSpecialAdmin,
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
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip, deviceId);
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

export async function requestOtp(req: Request, res: Response): Promise<void> {
  try {
    const rawTarget = (req.body.email || '').trim().toLowerCase();
    if (!rawTarget) {
      res.status(400).json({ success: false, error: 'A Gmail address is required' });
      return;
    }

    // Check rate limit on OTP store attempts
    const existing = peekOtpRequest(rawTarget, 'login');
    if (existing && Date.now() < existing.expiresAt && existing.attempts >= OTP_MAX_ATTEMPTS) {
      res.status(429).json({ success: false, error: 'Too many OTP requests. Please wait before retrying.' });
      return;
    }

    const existingUser = await UserModel.findOne({ email: rawTarget });

    // Generate random 6-digit verification code
    const code = issueOtp(rawTarget, 'login');

    const emailResult = await sendOtpEmail(rawTarget, code);
    if (!emailResult.sent) {
      clearOtp(rawTarget, 'login');
      res.status(503).json({
        success: false,
        error: 'Verification email could not be delivered. Please try again later.',
      });
      return;
    }

    res.json({
      success: true,
      message: 'Verification code sent to your email',
      isNewUser: !existingUser,
      emailSent: true,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to dispatch verification code' });
  }
}

export async function verifyOtp(req: Request, res: Response): Promise<void> {
  try {
    const rawTarget = (req.body.email || '').trim().toLowerCase();
    const otp = (req.body.otp || '').trim();

    if (!rawTarget || !otp) {
      res.status(400).json({ success: false, error: 'Target identifier and 6-digit OTP are required' });
      return;
    }

    if (!consumeOtp(rawTarget, otp, 'login')) {
      res.status(400).json({ success: false, error: 'Invalid or expired verification code' });
      return;
    }

    const query = { email: rawTarget };
    let user = await UserModel.findOne(query);
    const isSpecialAdmin = rawTarget === 'sarvagrawork@gmail.com';
    let isNewUser = false;

    if (!user) {
      isNewUser = true;
      let finalUsername = '';
      let isUnique = false;
      let attempts = 0;
      while (!isUnique && attempts < 15) {
        const rand = Math.random().toString(36).substring(2, 8);
        finalUsername = `nebula_${rand}`.toLowerCase();
        const existing = await UserModel.findOne({ username: finalUsername });
        if (!existing) isUnique = true;
        attempts++;
      }
      if (!isUnique) finalUsername = `nebula_${Date.now().toString(36)}`;

      user = await UserModel.create({
        email: rawTarget,
        displayName: isSpecialAdmin ? 'Sarvagra (Admin)' : '',
        username: finalUsername,
        avatarLetter: isSpecialAdmin ? 'S' : 'N',
        tier: isSpecialAdmin ? 'premium_plus' : 'free',
        role: isSpecialAdmin ? 'admin' : 'user',
        status: 'dynamic',
        presenceStatus: 'online',
        bio: '',
        isOnboardingCompleted: isSpecialAdmin,
      });
    } else {
      if (isSpecialAdmin) {
        user.role = 'admin';
        user.tier = 'premium_plus';
        user.isOnboardingCompleted = true;
      }
      // Completion is explicit; a generated internal handle is not a chosen identity.
      if (user.isOnboardingCompleted) {
        isNewUser = false;
      } else {
        isNewUser = true;
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
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip, deviceId);
    const grace = await applyLoginDeletionGrace(user);

    res.json({
      success: true,
      data: {
        user: toPrivateUserDTO(user),
        tokens,
        isNewUser,
        deletionSuspended: grace.deletionSuspended,
        deletionWasScheduledFor: grace.deletionScheduledFor,
      },
    });
  } catch (err: any) {
    const status = err.statusCode || 500;
    res.status(status).json({
      success: false,
      error: err.message || 'OTP verification failed',
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
    const { refreshToken } = req.body;
    if (refreshToken) {
      await AuthService.revokeSession(refreshToken);
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

export async function devLogin(req: Request, res: Response): Promise<void> {
  try {
    if (config.server.isProduction) {
      res.status(403).json({ success: false, error: 'Developer login disabled in production' });
      return;
    }

    const email = (req.body.email || '').trim().toLowerCase();
    const requestedUsername = (req.body.username || (email ? email.split('@')[0] : 'nebula_explorer')).toLowerCase().trim().replace(/^@/, '');
    const displayName = (req.body.displayName || (email ? email.split('@')[0] : 'Nebula Explorer')).trim();
    let user = await UserModel.findOne({
      $or: [
        ...(email ? [{ email }] : []),
        { username: requestedUsername }
      ]
    });

    if (!user) {
      user = await UserModel.create({
        email: email || `${requestedUsername}@nebula.local`,
        displayName: displayName || 'Nebula Explorer',
        username: requestedUsername,
        avatarLetter: (displayName || requestedUsername).charAt(0).toUpperCase() || 'N',
        tier: 'free',
        status: 'dynamic',
        presenceStatus: 'online',
        bio: '',
        isOnboardingCompleted: true,
      });
    } else {
      if (!user.isOnboardingCompleted) {
        user.isOnboardingCompleted = true;
        await user.save();
      }
    }

    if (user.isBanned) {
      res.status(403).json({ success: false, error: 'Account has been suspended' });
      return;
    }

    const ip = req.ip || req.socket.remoteAddress;
    const deviceInfo = (req.body.deviceInfo || req.headers['user-agent'] || 'Android Client Emulator').toString();
    const deviceId = (req.body.deviceId || '').toString();
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip, deviceId);
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
    const status = err.statusCode || 500;
    res.status(status).json({ success: false, error: err.message || 'Developer login failed', code: err.code });
  }
}

export async function getActiveSessions(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const currentToken = req.headers.authorization?.replace(/^Bearer\s+/i, '');
    const currentTokenHash = currentToken ? hashToken(currentToken) : undefined;
    const sessions = await AuthService.getActiveSessions(req.user.id, currentTokenHash);

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

    res.json({ success: true, message: 'Device session revoked successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to revoke device session' });
  }
}
