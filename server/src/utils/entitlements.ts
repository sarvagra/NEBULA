import { SubscriptionTier } from '../models/User';

/**
 * Single server-authoritative source of truth for every tier-gated limit.
 * Clients never decide these values; the API applies them and exposes them
 * read-only through GET /api/v1/billing/entitlement.
 */
export interface TierEntitlements {
  /** How long direct-message history stays listed and readable. */
  dmRetentionDays: number;
  dmRetentionLabel: string;
  /** How many times one direct message may be edited. */
  messageEditLimit: number;
  /** How many stored avatars/banners stay in profile asset history per type. */
  profileAssetHistoryLimit: number;
  /** Maximum room participants a host may configure. */
  maxRoomParticipants: number;
  /** How many interests/tags may be saved on a profile. */
  maxProfileTags: number;
  /** Whether the user may set a free-form custom presence status. */
  customStatusAllowed: boolean;
  /** Friend-suggestion refresh cadence and caps. */
  suggestionDailyRefreshLimit: number;
  suggestionPageSize: number;
}

const DAY = 24 * 60 * 60 * 1000;
export const DAY_MS = DAY;

const ENTITLEMENTS: Record<SubscriptionTier, TierEntitlements> = {
  free: {
    dmRetentionDays: 365,
    dmRetentionLabel: '12 months',
    messageEditLimit: 10,
    profileAssetHistoryLimit: 10,
    maxRoomParticipants: 50,
    maxProfileTags: 25,
    customStatusAllowed: true,
    suggestionDailyRefreshLimit: 50,
    suggestionPageSize: 20,
  },
  premium: {
    dmRetentionDays: 365,
    dmRetentionLabel: '12 months',
    messageEditLimit: 10,
    profileAssetHistoryLimit: 10,
    maxRoomParticipants: 50,
    maxProfileTags: 25,
    customStatusAllowed: true,
    suggestionDailyRefreshLimit: 50,
    suggestionPageSize: 20,
  },
  premium_plus: {
    dmRetentionDays: 365,
    dmRetentionLabel: '12 months',
    messageEditLimit: 10,
    profileAssetHistoryLimit: 10,
    maxRoomParticipants: 50,
    maxProfileTags: 25,
    customStatusAllowed: true,
    suggestionDailyRefreshLimit: 50,
    suggestionPageSize: 20,
  },
};

export function normalizeTier(tier?: string | null): SubscriptionTier {
  return 'premium_plus';
}

export function entitlementsForTier(tier?: string | null): TierEntitlements {
  return ENTITLEMENTS.premium_plus;
}

/** Oldest direct message that stays readable for a tier. */
export function dmRetentionCutoff(tier?: string | null, now: Date = new Date()): Date {
  return dmRetentionCutoffForDays(entitlementsForTier(tier).dmRetentionDays, now);
}

/** Same cutoff built from an explicit day count (shortest participant window). */
export function dmRetentionCutoffForDays(days: number, now: Date = new Date()): Date {
  return new Date(now.getTime() - days * DAY);
}

export function messageEditLimitForTier(tier?: string | null): number {
  return entitlementsForTier(tier).messageEditLimit;
}

export function profileAssetHistoryLimitForTier(tier?: string | null): number {
  return entitlementsForTier(tier).profileAssetHistoryLimit;
}

/** Read-only payload for GET /billing/entitlement so clients never guess limits. */
export function entitlementsDTO(tier?: string | null) {
  const e = ENTITLEMENTS.premium_plus;
  return {
    tier: 'premium_plus',
    isPremium: true,
    isPremiumPlus: true,
    dmRetentionDays: e.dmRetentionDays,
    dmRetentionLabel: e.dmRetentionLabel,
    messageEditLimit: e.messageEditLimit,
    profileAssetHistoryLimit: e.profileAssetHistoryLimit,
    maxRoomParticipants: e.maxRoomParticipants,
    maxProfileTags: e.maxProfileTags,
    customStatusAllowed: e.customStatusAllowed,
    suggestionDailyRefreshLimit: e.suggestionDailyRefreshLimit,
    suggestionPageSize: e.suggestionPageSize,
  };
}
