import { Request, Response } from 'express';
import { AuthService } from '../../services/authService';
import { UserModel } from '../../models/User';
import { toPrivateUserDTO } from '../../utils/dto';
import { config } from '../../config/env';
import { sendOtpEmail } from '../../services/emailService';

// Temporary in-memory OTP verification store (scoped to dev / OTP provider integration)
const otpStore = new Map<string, { code: string; expiresAt: number; attempts: number }>();

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
        status: 'online',
        bio: 'Building ambient systems & shared spaces on Android.',
        isOnboardingCompleted: true,
      });
    } else {
      if (!user.firebaseUid) {
        user.firebaseUid = verified.uid;
      }
      if (!user.isOnboardingCompleted) {
        user.isOnboardingCompleted = true;
      }
      await user.save();
    }

    if (user.isBanned) {
      res.status(403).json({ success: false, error: 'Account has been suspended' });
      return;
    }

    const ip = req.ip || req.socket.remoteAddress;
    const deviceInfo = req.headers['user-agent'] || 'Android Client';
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip);

    res.json({
      success: true,
      data: {
        user: toPrivateUserDTO(user),
        tokens,
      },
    });
  } catch (err: any) {
    res.status(401).json({ success: false, error: err.message || 'Google authentication failed' });
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
    const existing = otpStore.get(rawTarget);
    if (existing && Date.now() < existing.expiresAt && existing.attempts >= 5) {
      res.status(429).json({ success: false, error: 'Too many OTP requests. Please wait before retrying.' });
      return;
    }

    const existingUser = await UserModel.findOne({ email: rawTarget });

    // Generate random 6-digit verification code
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 min expiry
    otpStore.set(rawTarget, { code, expiresAt, attempts: (existing?.attempts || 0) + 1 });

    const emailResult = await sendOtpEmail(rawTarget, code);
    if (!emailResult.sent) {
      otpStore.delete(rawTarget);
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

    const record = otpStore.get(rawTarget);
    const isCodeMatch = record && record.code === otp;

    if (!record || Date.now() > record.expiresAt || !isCodeMatch) {
      res.status(400).json({ success: false, error: 'Invalid or expired verification code' });
      return;
    }

    otpStore.delete(rawTarget);

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
        status: 'online',
        bio: 'Building ambient systems & shared spaces on Android.',
        isOnboardingCompleted: isSpecialAdmin,
      });
    } else {
      if (isSpecialAdmin) {
        user.role = 'admin';
        user.tier = 'premium_plus';
        user.isOnboardingCompleted = true;
      }
      // If user has completed onboarding or has a display name, they are NEVER a new user!
      if (user.isOnboardingCompleted || (user.displayName && user.username)) {
        isNewUser = false;
        user.isOnboardingCompleted = true;
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
    const deviceInfo = req.headers['user-agent'] || 'Android Client';
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip);

    res.json({
      success: true,
      data: {
        user: toPrivateUserDTO(user),
        tokens,
        isNewUser,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'OTP verification failed' });
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
        status: 'online',
        bio: 'Building ambient systems & shared spaces on Android.',
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
    const deviceInfo = req.headers['user-agent'] || 'Android Client Emulator';
    const tokens = await AuthService.issueSessionTokens(user, deviceInfo, ip);

    res.json({
      success: true,
      data: {
        user: toPrivateUserDTO(user),
        tokens,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message || 'Developer login failed' });
  }
}
