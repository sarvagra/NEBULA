import { Request, Response } from 'express';
import { UserModel } from '../../models/User';
import {
  REENGAGEMENT_COOLDOWN_HOURS,
  REENGAGEMENT_IDLE_HOURS,
  REENGAGEMENT_KIND_COOLDOWN_HOURS,
  QUIET_HOURS_START,
  QUIET_HOURS_END,
  runReengagementSweep,
} from '../../services/reengagementService';

function preferencesDTO(user: {
  reengagementOptIn?: boolean;
  reengagementOptInAt?: Date;
  reengagementLastSentAt?: Record<string, Date> | null;
}) {
  const lastSent = user.reengagementLastSentAt || {};
  return {
    optIn: user.reengagementOptIn === true,
    optedInAt: user.reengagementOptInAt ?? null,
    lastSentAt: (lastSent as any).__any ?? null,
    policy: {
      cooldownHours: REENGAGEMENT_COOLDOWN_HOURS,
      kindCooldownHours: REENGAGEMENT_KIND_COOLDOWN_HOURS,
      idleHoursBeforeContact: REENGAGEMENT_IDLE_HOURS,
      quietHoursStart: QUIET_HOURS_START,
      quietHoursEnd: QUIET_HOURS_END,
    },
  };
}

/** GET /notifications/reengagement — the user's own consent state and the policy. */
export async function getReengagementPreferences(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const user = await UserModel.findById(req.user.id).select('reengagementOptIn reengagementOptInAt reengagementLastSentAt');
    if (!user) { res.status(404).json({ success: false, error: 'User not found' }); return; }
    res.json({ success: true, data: preferencesDTO(user) });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to read re-engagement preferences' });
  }
}

/**
 * PUT /notifications/reengagement
 *
 * Consent is explicit and revocable. Turning it off clears the schedule markers so
 * a later opt-in starts from a clean cadence window.
 */
export async function updateReengagementPreferences(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const optIn = req.body?.optIn;
    if (typeof optIn !== 'boolean') {
      res.status(400).json({ success: false, error: 'optIn must be a boolean' });
      return;
    }

    const update: Record<string, any> = { reengagementOptIn: optIn };
    if (optIn) {
      update.reengagementOptInAt = new Date();
    } else {
      update.reengagementOptInAt = null;
      update.reengagementLastSentAt = {};
    }

    const updated = await UserModel.findByIdAndUpdate(req.user.id, { $set: update }, { new: true })
      .select('reengagementOptIn reengagementOptInAt reengagementLastSentAt');
    if (!updated) { res.status(404).json({ success: false, error: 'User not found' }); return; }

    res.json({
      success: true,
      message: optIn
        ? 'Re-engagement messages enabled'
        : 'Re-engagement messages disabled',
      data: preferencesDTO(updated),
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to update re-engagement preferences' });
  }
}

/**
 * POST /notifications/reengagement/run
 *
 * Admin-only manual trigger for the scheduler sweep. `dryRun` reports the decision
 * for every candidate without sending anything.
 */
export async function runReengagementNow(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    if (req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Forbidden: admin role required' });
      return;
    }
    const dryRun = req.body?.dryRun === true;
    const result = await runReengagementSweep(new Date(), { dryRun });
    res.json({ success: true, data: { ...result, dryRun } });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to run the re-engagement sweep' });
  }
}
