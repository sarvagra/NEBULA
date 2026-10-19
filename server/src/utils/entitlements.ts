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
    dmRetentionDays: 180,
    dmRetentionLabel: '6 months',
    messageEditLimit: 1,
    profileAssetHistoryLimit: 1,
    maxRoomParticipants: 20,
    maxProfileTags: 5,
    customStatusAllowed: false,
    suggestionDailyRefreshLimit: 3,
    suggestionPageSize: 5,
  },
  premium: {
    dmRetentionDays: 180,
    dmRetentionLabel: '6 months',
    messageEditLimit: 2,
    profileAssetHistoryLimit: 3,
    maxRoomParticipants: 25,
    maxProfileTags: 8,
    customStatusAllowed: false,
    suggestionDailyRefreshLimit: 6,
    suggestionPageSize: 10,
  },
  premium_plus: {
    dmRetentionDays: 365,
    dmRetentionLabel: '12 months',
    messageEditLimit: 2,
    profileAssetHistoryLimit: 5,
    maxRoomParticipants: 50,
    maxProfileTags: 12,
    customStatusAllowed: true,
    suggestionDailyRefreshLimit: 12,
    suggestionPageSize: 12,
  },
};

export function normalizeTier(tier?: string | null): SubscriptionTier {
  return tier === 'premium' || tier === 'premium_plus' ? tier : 'free';
}

export function entitlementsForTier(tier?: string | null): TierEntitlements {
  return ENTITLEMENTS[normalizeTier(tier)];
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
  const resolved = normalizeTier(tier);
  const e = ENTITLEMENTS[resolved];
  return {
    tier: resolved,
    isPremium: resolved !== 'free',
    isPremiumPlus: resolved === 'premium_plus',
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
