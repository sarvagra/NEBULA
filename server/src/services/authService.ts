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
   */
  static async issueSessionTokens(user: IUser, deviceInfo?: string, ipAddress?: string): Promise<AuthTokens> {
    const payload = {
      id: user._id.toString(),
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
      userId: user._id.toString(),
      tokenHash,
      deviceInfo: deviceInfo || 'Android Device',
      ipAddress: ipAddress || 'unknown',
      expiresAt,
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
