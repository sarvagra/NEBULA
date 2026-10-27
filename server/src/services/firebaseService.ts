import { initializeApp, getApps, App, cert } from 'firebase-admin/app';
import { getAuth, UserRecord } from 'firebase-admin/auth';
import { getAppCheck } from 'firebase-admin/app-check';
import { config } from '../config/env';

let firebaseApp: App | null = null;

export function getFirebaseAdmin(): App {
  if (firebaseApp) return firebaseApp;

  const currentApps = getApps();
  if (currentApps.length > 0 && currentApps[0]) {
    firebaseApp = currentApps[0];
    return firebaseApp;
  }

  const projectId = config.firebase.projectId || 'nebula-production-170fa';

  if (config.firebase.clientEmail && config.firebase.privateKey) {
    firebaseApp = initializeApp({
      credential: cert({
        projectId,
        clientEmail: config.firebase.clientEmail,
        privateKey: config.firebase.privateKey,
      }),
    });
  } else {
    firebaseApp = initializeApp({
      projectId,
    });
  }

  return firebaseApp;
}

export interface VerifiedFirebaseToken {
  uid: string;
  email: string;
  name: string;
  picture?: string;
  emailVerified: boolean;
}

export class FirebaseService {
  /**
   * Cryptographically verifies a Firebase ID Token using Firebase Admin SDK.
   * Authoritatively extracts the Firebase UID and user metadata.
   */
  static async verifyIdToken(idToken: string): Promise<VerifiedFirebaseToken> {
    const app = getFirebaseAdmin();
    const auth = getAuth(app);
    const decoded = await auth.verifyIdToken(idToken);

    if (!decoded || !decoded.uid) {
      throw new Error('Invalid Firebase token: missing UID');
    }

    return {
      uid: decoded.uid,
      email: decoded.email || '',
      name: (decoded as any).name || '',
      picture: (decoded as any).picture,
      emailVerified: decoded.email_verified || false,
    };
  }

  /**
   * Cryptographically verifies an incoming Firebase App Check token (backed by Play Integrity on Android).
   * Ensures requests originate exclusively from the legitimate, untampered mobile binary.
   */
  static async verifyAppCheckToken(token: string): Promise<{ appId: string; alreadyConsumed?: boolean }> {
    const app = getFirebaseAdmin();
    const appCheck = getAppCheck(app);
    const result = await appCheck.verifyToken(token);
    return {
      appId: result.appId,
      alreadyConsumed: result.alreadyConsumed,
    };
  }

  /**
   * Fetches user record from Firebase Auth by UID.
   */
  static async getUser(uid: string): Promise<UserRecord | null> {
    try {
      const app = getFirebaseAdmin();
      const auth = getAuth(app);
      return await auth.getUser(uid);
    } catch {
      return null;
    }
  }
}
