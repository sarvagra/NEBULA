import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import { config } from '../config/env';
import { UserModel, SubscriptionTier } from '../models/User';
import { FirebaseService } from '../services/firebaseService';

export interface AuthenticatedUser {
  id: string;
  username: string;
  displayName: string;
  tier: SubscriptionTier;
  role: 'user' | 'moderator' | 'admin';
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthenticatedUser;
    }
  }
}

export async function authMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({ success: false, error: 'Authentication required. Missing Bearer token.' });
    return;
  }

  const token = authHeader.split(' ')[1];
  if (!token || token.trim() === '') {
    res.status(401).json({ success: false, error: 'Authentication token is empty.' });
    return;
  }

  try {
    let user: any = null;

    // 1. Try verifying as a Nebula session JWT
    try {
      const decoded = jwt.verify(token, config.auth.jwtSecret) as { id: string };
      if (decoded && decoded.id) {
        if (mongoose.connection.readyState === 1) {
          user = await UserModel.findById(decoded.id).select('_id username displayName tier role isBanned');
        }
      }
    } catch (_jwtErr: any) {
      // 2. If not a session JWT, try verifying as a Firebase ID token
      try {
        const firebaseVerified = await FirebaseService.verifyIdToken(token);
        if (firebaseVerified && firebaseVerified.uid) {
          if (mongoose.connection.readyState === 1) {
            user = await UserModel.findOne({
              $or: [
                { firebaseUid: firebaseVerified.uid },
                { googleId: firebaseVerified.uid },
                { email: firebaseVerified.email },
              ],
            }).select('_id username displayName tier role isBanned');

            if (!user) {
              const username = firebaseVerified.email
                ? firebaseVerified.email.split('@')[0].toLowerCase().replace(/[^a-z0-9_]/g, '')
                : `user_${Date.now().toString().slice(-4)}`;

              user = await UserModel.create({
                email: firebaseVerified.email,
                firebaseUid: firebaseVerified.uid,
                googleId: firebaseVerified.uid,
                displayName: firebaseVerified.name || 'Nebula User',
                username,
                avatarLetter: (firebaseVerified.name || 'N').charAt(0).toUpperCase(),
                avatarUrl: firebaseVerified.picture,
                tier: 'free',
                status: 'dynamic',
                presenceStatus: 'online',
              });
            }
          }
        }
      } catch (_firebaseErr: any) {
        res.status(401).json({ success: false, error: 'Invalid or expired authentication token.' });
        return;
      }
    }

    if (!user) {
      res.status(401).json({ success: false, error: 'Session expired or user no longer exists.' });
      return;
    }

    if (user.isBanned) {
      res.status(403).json({ success: false, error: 'Account has been suspended.' });
      return;
    }

    // Attach server-authoritative identity (never trusted from client)
    req.user = {
      id: user._id.toString(),
      username: user.username,
      displayName: user.displayName,
      tier: user.tier,
      role: user.role,
    };

    next();
  } catch (err: any) {
    res.status(401).json({ success: false, error: err.message || 'Invalid authentication token.' });
  }
}

export async function optionalAuthMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    if (token && token.trim() !== '') {
      try {
        const decoded = jwt.verify(token, config.auth.jwtSecret) as { id: string };
        const user = await UserModel.findById(decoded.id).select('_id username displayName tier role isBanned');
        if (user && !user.isBanned) {
          req.user = {
            id: user._id.toString(),
            username: user.username,
            displayName: user.displayName,
            tier: user.tier,
            role: user.role,
          };
        }
      } catch {
        // Ignored for optional endpoints
      }
    }
  }
  next();
}
