import { UserModel } from '../models/User';
import { notifyUser } from '../modules/notifications/notificationController';

/**
 * Re-engagement messaging.
 *
 * Product rules enforced here (never trusted to the client):
 *  1. Opt-in only — a user with `reengagementOptIn !== true` is never messaged,
 *     no matter how long they have been away.
 *  2. A cadence floor — at most one re-engagement message per user per window.
 *  3. A per-message kind cooldown, so the same nudge is not repeated.
 *  4. Local quiet hours — nothing is dispatched while the user is likely asleep.
 *
 * Every candidate is re-read immediately before sending so a preference change or
 * an activity that happens mid-sweep always wins.
 */

export const REENGAGEMENT_COOLDOWN_HOURS = 72;
export const REENGAGEMENT_KIND_COOLDOWN_HOURS = 7 * 24;
export const REENGAGEMENT_IDLE_HOURS = 48;
export const QUIET_HOURS_START = 22;
export const QUIET_HOURS_END = 8;

export type ReengagementKind = 'new_rooms' | 'friends_active' | 'watch_together';

interface Campaign {
  kind: ReengagementKind;
  title: string;
  body: string;
}

const CAMPAIGNS: Campaign[] = [
  { kind: 'new_rooms', title: 'New rooms are open', body: 'Fresh watch parties just opened. Pick one and jump in.' },
  { kind: 'friends_active', title: 'Your friends are watching', body: 'People you follow are in a room right now.' },
  { kind: 'watch_together', title: 'Something worth watching?', body: 'Start a room and sync it with a friend in seconds.' },
];

export interface ReengagementDecision {
  send: boolean;
  reason: string;
  kind?: ReengagementKind;
}

/** Quiet hours, evaluated in the server's local time. */
export function isQuietHours(now: Date = new Date()): boolean {
  const hour = now.getHours();
  if (QUIET_HOURS_START <= QUIET_HOURS_END) {
    return hour >= QUIET_HOURS_START && hour < QUIET_HOURS_END;
  }
  // Window wraps midnight (the normal case).
  return hour >= QUIET_HOURS_START || hour < QUIET_HOURS_END;
}

/**
 * Decide whether one user may receive a re-engagement message right now.
 * Pure decision logic, so it is directly testable.
 */
export function decideReengagement(
  user: {
    reengagementOptIn?: boolean;
    reengagementLastSentAt?: Record<string, Date> | null;
    lastActiveAt?: Date | null;
    fcmTokens?: string[];
    isBanned?: boolean;
  },
  now: Date = new Date(),
  opts: { ignoreQuietHours?: boolean } = {}
): ReengagementDecision {
  if (user.isBanned) return { send: false, reason: 'account_unavailable' };
  if (user.reengagementOptIn !== true) return { send: false, reason: 'no_consent' };
  if (!user.fcmTokens?.length) return { send: false, reason: 'no_delivery_target' };
  if (isQuietHours(now) && !opts.ignoreQuietHours) return { send: false, reason: 'quiet_hours' };

  const lastActiveMs = user.lastActiveAt ? new Date(user.lastActiveAt).getTime() : 0;
  const idleHours = lastActiveMs ? (now.getTime() - lastActiveMs) / (60 * 60 * 1000) : Number.POSITIVE_INFINITY;
  if (idleHours < REENGAGEMENT_IDLE_HOURS) return { send: false, reason: 'recently_active' };

  const sent = user.reengagementLastSentAt || {};
  const lastAny = sent.__any ? new Date(sent.__any).getTime() : 0;
  if (lastAny && now.getTime() - lastAny < REENGAGEMENT_COOLDOWN_HOURS * 60 * 60 * 1000) {
    return { send: false, reason: 'cadence_floor' };
  }

  for (const campaign of CAMPAIGNS) {
    const lastKind = sent[campaign.kind] ? new Date(sent[campaign.kind]).getTime() : 0;
    const kindCooling = lastKind && now.getTime() - lastKind < REENGAGEMENT_KIND_COOLDOWN_HOURS * 60 * 60 * 1000;
    if (!kindCooling) {
      return { send: true, reason: 'eligible', kind: campaign.kind };
    }
  }

  return { send: false, reason: 'all_campaigns_cooling' };
}

export interface ReengagementSweepResult {
  considered: number;
  sent: number;
  skipped: Record<string, number>;
  campaigns: ReengagementKind[];
}

/**
 * Sweep opted-in users and send at most one controlled nudge each.
 * `dryRun` reports what would be sent without dispatching or recording.
 */
export async function runReengagementSweep(
  now: Date = new Date(),
  opts: { dryRun?: boolean; ignoreQuietHours?: boolean; limit?: number } = {}
): Promise<ReengagementSweepResult> {
  const result: ReengagementSweepResult = { considered: 0, sent: 0, skipped: {}, campaigns: [] };

  // Only opted-in accounts are even queried, so consent is enforced at the source.
  const candidates = await UserModel.find({ reengagementOptIn: true, isBanned: { $ne: true } })
    .select('reengagementOptIn reengagementLastSentAt lastActiveAt fcmTokens isBanned')
    .limit(opts.limit ?? 200);

  for (const user of candidates) {
    result.considered++;
    const decision = decideReengagement(user, now, { ignoreQuietHours: opts.ignoreQuietHours });
    if (!decision.send || !decision.kind) {
      result.skipped[decision.reason] = (result.skipped[decision.reason] || 0) + 1;
      continue;
    }

    const campaign = CAMPAIGNS.find((c) => c.kind === decision.kind)!;
    if (!opts.dryRun) {
      // Re-read right before sending so a mid-sweep opt-out wins.
      const fresh = await UserModel.findOne({
        _id: user._id,
        reengagementOptIn: true,
        isBanned: { $ne: true },
      }).select('reengagementOptIn reengagementLastSentAt lastActiveAt fcmTokens isBanned');
      if (!fresh) {
        result.skipped['opted_out_mid_sweep'] = (result.skipped['opted_out_mid_sweep'] || 0) + 1;
        continue;
      }
      const freshDecision = decideReengagement(fresh, now, { ignoreQuietHours: opts.ignoreQuietHours });
      if (!freshDecision.send || freshDecision.kind !== decision.kind) {
        result.skipped['state_changed'] = (result.skipped['state_changed'] || 0) + 1;
        continue;
      }

      const lastSentAt = { ...(fresh.reengagementLastSentAt || {}) } as Record<string, Date>;
      lastSentAt[freshDecision.kind] = now;
      lastSentAt.__any = now;
      await UserModel.updateOne({ _id: fresh._id }, { $set: { reengagementLastSentAt: lastSentAt } });
      await notifyUser(fresh._id.toString(), campaign.title, campaign.body, { type: 'reengagement', kind: freshDecision.kind });
    }

    result.sent++;
    if (decision.kind && !result.campaigns.includes(decision.kind)) result.campaigns.push(decision.kind);
  }

  return result;
}
