import { Request, Response } from 'express';
import { UserModel } from '../../models/User';
import { AuthService } from '../../services/authService';

/**
 * Changing the Google identity behind an account.
 *
 * The account itself never changes: the same user document, the same id, the same
 * rooms, messages and stored objects (their keys are namespaced by user id). Only
 * the verified identity that may sign in as that user changes.
 *
 * Rules, enforced entirely here:
 *  - the new identity must be a freshly verified Firebase token, never an email
 *    string the client made up;
 *  - an identity already attached to another account is refused instead of silently
 *    merging two people's data;
 *  - one free change per six months. Any further change inside that window is
 *    priced, and the price rises by ₹25 for every change after the free one.
 */

export const FREE_CHANGE_COOLDOWN_DAYS = 183; // ~6 months
const BASE_PRICE_INR = 99;
const PRICE_STEP_INR = 25;

export function priceForChange(changesAfterFree: number): number {
  return BASE_PRICE_INR + PRICE_STEP_INR * Math.max(0, changesAfterFree - 1);
}

function nextFreeAt(lastChangeAt: Date | undefined): Date | null {
  if (!lastChangeAt) return null;
  return new Date(lastChangeAt.getTime() + FREE_CHANGE_COOLDOWN_DAYS * 24 * 60 * 60 * 1000);
}

export async function getEmailChangeStatus(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }
    const user = await UserModel.findById(req.user.id).select(
      'email emailChangedAt emailChangeCount emailChangeHistory'
    );
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    const free = nextFreeAt(user.emailChangedAt);
    const now = Date.now();
    const isFreeNow = !free || free.getTime() <= now;
    const paidChanges = Math.max(0, (user.emailChangeCount || 0));

    res.json({
      success: true,
      data: {
        email: user.email || null,
        lastChangedAt: user.emailChangedAt || null,
        canChangeFreeAt: isFreeNow ? null : free,
        isFreeNow,
        freeChangesUsed: (user.emailChangeCount || 0) > 0 ? 1 : 0,
        priceInr: isFreeNow ? 0 : priceForChange(paidChanges),
      },
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to read email status' });
  }
}

export async function rebindEmail(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { idToken } = req.body;
    if (!idToken || typeof idToken !== 'string') {
      res.status(400).json({ success: false, error: 'A fresh Google sign-in token is required' });
      return;
    }

    // Only a token the identity provider signed for is accepted; the client cannot
    // type in the address it would like to own.
    const verified = await AuthService.verifyIdToken(idToken);
    const newEmail = (verified.email || '').trim().toLowerCase();
    if (!newEmail) {
      res.status(400).json({ success: false, error: 'That Google account has no verified email' });
      return;
    }

    const user = await UserModel.findById(req.user.id).select(
      'email googleId firebaseUid emailChangedAt emailChangeCount emailChangeHistory isBanned'
    );
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    if ((user.email || '').toLowerCase() === newEmail && user.firebaseUid === verified.uid) {
      res.status(409).json({ success: false, code: 'SAME_IDENTITY', error: 'That is already the Google account linked to Nebula' });
      return;
    }

    // Refuse an identity that belongs to somebody else rather than merging accounts.
    const taken = await UserModel.findOne({
      _id: { $ne: user._id },
      $or: [{ firebaseUid: verified.uid }, { googleId: verified.uid }, { email: newEmail }],
    }).select('_id');
    if (taken) {
      res.status(409).json({
        success: false,
        code: 'IDENTITY_IN_USE',
        error: 'That Google account already belongs to another Nebula account.',
      });
      return;
    }

    const free = nextFreeAt(user.emailChangedAt);
    const isFreeNow = !free || free.getTime() <= Date.now();
    if (!isFreeNow) {
      const price = priceForChange(user.emailChangeCount || 0);
      res.status(402).json({
        success: false,
        code: 'EMAIL_CHANGE_PAYMENT_REQUIRED',
        error: `A free change is available from ${free!.toISOString().slice(0, 10)}. Changing now costs ₹${price}.`,
        data: { priceInr: price, canChangeFreeAt: free },
      });
      return;
    }

    const previousEmail = user.email || null;
    const previousUid = user.firebaseUid || user.googleId || null;

    user.email = newEmail;
    user.googleId = verified.uid;
    user.firebaseUid = verified.uid;
    user.emailChangedAt = new Date();
    user.emailChangeCount = (user.emailChangeCount || 0) + 1;
    if (!user.emailChangeHistory) user.emailChangeHistory = [];
    user.emailChangeHistory.push({
      from: previousEmail,
      to: newEmail,
      at: new Date(),
      uidFrom: previousUid,
      uidTo: verified.uid,
    });
    await user.save();

    // The change is an identity event, so every other signed-in device is retired.
    // The caller keeps its own session and simply continues as the same account.
    await AuthService.revokeOtherSessions(req.user.id, req.user.deviceId, req.user.sessionId);

    res.json({
      success: true,
      data: {
        email: user.email,
        changedAt: user.emailChangedAt,
        // Stored objects, messages and rooms are untouched: they are keyed by the
        // account id, which did not change.
        accountDataPreserved: true,
      },
    });
  } catch (err: any) {
    res.status(400).json({ success: false, error: err.message || 'Could not change the linked Google account' });
  }
}
