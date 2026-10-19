import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { OAuth2Client } from 'google-auth-library';
import { config } from '../config/env';
import { IUser, UserModel } from '../models/User';
import { RefreshTokenModel } from '../models/RefreshToken';
import { FirebaseService } from './firebaseService';

const googleClient = new OAuth2Client(config.auth.googleClientId);

export interface AuthTokens {
  token: string;
  refreshToken: string;
  expiresIn: string;
}

export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export class AuthService {
  /**
   * Generates a signed JWT access token and creates a tracked, revocable refresh token session in MongoDB.
   * Strictly enforces a MAXIMUM OF 2 CONCURRENT ACTIVE DEVICES PER ACCOUNT.
   * Attempting to log into a 3rd distinct device without revoking an existing session is rejected.
   */
  static async issueSessionTokens(
    user: IUser,
    deviceInfo?: string,
    ipAddress?: string,
    deviceId?: string
  ): Promise<AuthTokens> {
    const userId = user._id.toString();

    // 1. Query current active (non-revoked, unexpired) sessions
    const activeSessions = await RefreshTokenModel.find({
      userId,
      revoked: false,
      expiresAt: { $gt: new Date() },
    }).sort({ updatedAt: -1 });

    // Check if the current device matches an existing session (same deviceId or exact deviceInfo)
    const existingSameDeviceSession = deviceId
      ? activeSessions.find((s) => s.deviceId === deviceId)
      : activeSessions.find((s) => s.deviceInfo === deviceInfo);

    if (!existingSameDeviceSession && activeSessions.length >= 2) {
      const error: any = new Error(
        'Device limit reached. Your account is already logged into 2 devices. Please log out from another device in Settings > Account before signing in on a new device.'
      );
      error.statusCode = 403;
      error.code = 'DEVICE_LIMIT_REACHED';
      throw error;
    }

    // If re-authenticating from the same device, retire the previous token for this device
    if (existingSameDeviceSession) {
      await RefreshTokenModel.updateOne(
        { _id: existingSameDeviceSession._id },
        { revoked: true, revokedAt: new Date() }
      );
    }

    const payload = {
      id: userId,
      username: user.username,
      displayName: user.displayName,
      tier: user.tier,
      role: user.role,
    };

    const token = jwt.sign(payload, config.auth.jwtSecret, { expiresIn: config.auth.jwtExpiresIn as any });
    const rawRefreshToken = crypto.randomBytes(40).toString('hex');
    const tokenHash = hashToken(rawRefreshToken);

    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 30); // 30 days

    await RefreshTokenModel.create({
      userId,
      tokenHash,
      deviceId: deviceId || crypto.randomUUID(),
      deviceInfo: deviceInfo || 'Android Device',
      ipAddress: ipAddress || 'unknown',
      expiresAt,
      lastActiveAt: new Date(),
      revoked: false,
    });

    return {
      token,
      refreshToken: rawRefreshToken,
      expiresIn: config.auth.jwtExpiresIn,
    };
  }

  /**
   * Refreshes an access token using a valid, non-revoked refresh token.
   * Updates lastActiveAt timestamp to keep session activity up to date.
   */
  static async refreshSession(rawRefreshToken: string, ipAddress?: string): Promise<{ token: string; user: IUser }> {
    const tokenHash = hashToken(rawRefreshToken);
    const session = await RefreshTokenModel.findOne({ tokenHash, revoked: false });

    if (!session || new Date() > session.expiresAt) {
      throw new Error('Refresh token is invalid, expired, or revoked');
    }

    const user = await UserModel.findById(session.userId);
    if (!user || user.isBanned) {
      throw new Error('User not found or account has been suspended');
    }

    session.lastActiveAt = new Date();
    if (ipAddress) session.ipAddress = ipAddress;
    await session.save();

    const payload = {
      id: user._id.toString(),
      username: user.username,
      displayName: user.displayName,
      tier: user.tier,
      role: user.role,
    };

    const token = jwt.sign(payload, config.auth.jwtSecret, { expiresIn: config.auth.jwtExpiresIn as any });
    return { token, user };
  }

  /**
   * Returns active sessions for user (up to 2), with device details and activity timestamps.
   */
  static async getActiveSessions(userId: string, currentTokenHash?: string): Promise<Array<{
    id: string;
    deviceId?: string;
    deviceInfo: string;
    ipAddress: string;
    lastActiveAt: Date;
    createdAt: Date;
    isCurrent: boolean;
  }>> {
    const sessions = await RefreshTokenModel.find({
      userId,
      revoked: false,
      expiresAt: { $gt: new Date() },
    })
      .sort({ lastActiveAt: -1, createdAt: -1 })
      .limit(2);

    return sessions.map((s) => ({
      id: s._id.toString(),
      deviceId: s.deviceId,
      deviceInfo: s.deviceInfo || 'Android Device',
      ipAddress: s.ipAddress || 'unknown',
      lastActiveAt: s.lastActiveAt || s.createdAt,
      createdAt: s.createdAt,
      isCurrent: Boolean(currentTokenHash && s.tokenHash === currentTokenHash),
    }));
  }

  /**
   * Revokes a specific session by session ID (e.g. remote logout from Settings > Account).
   */
  static async revokeSessionById(userId: string, sessionId: string): Promise<boolean> {
    const res = await RefreshTokenModel.updateOne(
      { _id: sessionId, userId, revoked: false },
      { $set: { revoked: true, revokedAt: new Date() } }
    );
    return res.modifiedCount > 0;
  }

  /**
   * Revokes a specific refresh token (e.g. on logout).
   */
  static async revokeSession(rawRefreshToken: string): Promise<void> {
    const tokenHash = hashToken(rawRefreshToken);
    await RefreshTokenModel.findOneAndUpdate(
      { tokenHash },
      { revoked: true, revokedAt: new Date() }
    );
  }

  /**
   * Revokes all active sessions for a user (e.g. security reset).
   */
  static async revokeAllUserSessions(userId: string): Promise<void> {
    await RefreshTokenModel.updateMany(
      { userId, revoked: false },
      { revoked: true, revokedAt: new Date() }
    );
  }

  /**
   * Verifies an ID token from Firebase Auth (authoritative) or Google OAuth.
   * Cryptographically verifies the signature, project ID, and expiration via Firebase Admin SDK.
   * Strictly rejects forged, modified, expired, or unverified tokens.
   */
  static async verifyIdToken(idToken: string): Promise<{ uid: string; email: string; displayName: string; picture?: string }> {
    if (!idToken || typeof idToken !== 'string' || idToken.trim() === '') {
      throw new Error('ID token is empty or missing');
    }

    // 1. PRIMARY AUTHORITATIVE FLOW: Cryptographically verify with Firebase Admin SDK
    try {
      const verified = await FirebaseService.verifyIdToken(idToken);
      return {
        uid: verified.uid,
        email: verified.email,
        displayName: verified.name || 'Nebula User',
        picture: verified.picture,
      };
    } catch (firebaseErr: any) {
      // 2. Cryptographic Google OAuth token fallback (if token was minted directly via Google Web Client)
      if (config.auth.googleClientId) {
        try {
          const ticket = await googleClient.verifyIdToken({
            idToken,
            audience: config.auth.googleClientId,
          });
          const payload = ticket.getPayload();
          if (payload && payload.sub) {
            return {
              uid: payload.sub,
              email: payload.email || '',
              displayName: payload.name || 'Nebula User',
              picture: payload.picture,
            };
          }
        } catch (_googleErr: any) {
          // Google verification also failed
        }
      }

      // STRICT SECURITY: Reject completely. Never fall back to unverified jwt.decode.
      throw new Error(`Firebase token verification failed: ${firebaseErr.message || 'Invalid or expired ID token'}`);
    }
  }
}
