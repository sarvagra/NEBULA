import { Request, Response, NextFunction } from 'express';
import { config } from '../config/env';
import { FirebaseService } from '../services/firebaseService';

declare global {
  namespace Express {
    interface Request {
      appCheck?: {
        appId: string;
        alreadyConsumed?: boolean;
      };
    }
  }
}

/**
 * Express middleware to verify incoming Firebase App Check tokens (backed by Google Play Integrity on Android).
 *
 * Enforcement levels:
 * - 'enforce': Rejects all requests lacking a valid App Check token with 401/403.
 * - 'warn': Logs verification warnings without breaking requests (ideal for dev/staging and gradual rollout).
 * - 'off': Completely bypasses App Check validation.
 */
export async function appCheckMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  const enforcement = config.appCheck?.enforcement || 'warn';

  if (enforcement === 'off') {
    next();
    return;
  }

  const appCheckToken = req.header('X-Firebase-AppCheck');

  if (!appCheckToken || appCheckToken.trim() === '') {
    if (enforcement === 'enforce') {
      res.status(401).json({
        success: false,
        error: 'Unauthorized: Firebase App Check token is missing. Play Integrity attestation required.',
        code: 'APP_CHECK_MISSING',
      });
      return;
    }
    next();
    return;
  }

  try {
    const claims = await FirebaseService.verifyAppCheckToken(appCheckToken);
    req.appCheck = claims;
    next();
  } catch (err: any) {
    if (enforcement === 'enforce') {
      res.status(403).json({
        success: false,
        error: 'Forbidden: Firebase App Check verification failed. Untrusted or tampered client detected.',
        code: 'APP_CHECK_INVALID',
      });
      return;
    }
    next();
  }
}
