import { Request, Response } from 'express';
import { UserModel, SubscriptionTier } from '../../models/User';
import { config } from '../../config/env';

export const MEMBERSHIP_PLANS = [
  {
    tier: 'free' as SubscriptionTier,
    name: 'Free',
    price: '₹0',
    billingCycle: 'Forever',
    features: [
      'Public room discovery & joining',
      'Create & host standard rooms',
      'Realtime room chat & standard reactions',
      'Interactive room polls & voting',
      'Direct messaging & friends list',
      'Profile statistics & top content',
    ],
  },
  {
    tier: 'premium' as SubscriptionTier,
    name: 'Premium',
    price: '₹99',
    billingCycle: '2 months',
    features: [
      '100% Completely ad-free experience',
      'Custom profile banner & accent colors',
      'Exclusive Premium profile badge',
      'Advanced room appearance customizations',
      'Extra reactions & custom emojis',
      'Enhanced room usage statistics',
    ],
  },
  {
    tier: 'premium_plus' as SubscriptionTier,
    name: 'Premium Plus',
    price: '₹399',
    billingCycle: '4 months',
    features: [
      'Everything in Premium included',
      'Multiple custom profile identities',
      'Advanced host room controls & moderation',
      'Exclusive cosmic profile cosmetics',
      'In-depth sync analytics & history',
      'Priority room discovery placement',
      'Early access to experimental features',
    ],
  },
];

export async function getPlans(req: Request, res: Response): Promise<void> {
  res.json({ success: true, data: MEMBERSHIP_PLANS });
}

export async function getEntitlement(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const user = await UserModel.findById(req.user.id).select('tier');
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    // SERVER-AUTHORITATIVE ENTITLEMENT
    const tier = user.tier;
    const isPremium = tier === 'premium' || tier === 'premium_plus';
    const isPremiumPlus = tier === 'premium_plus';

    res.json({
      success: true,
      data: {
        tier,
        isPremium,
        isPremiumPlus,
        maxRoomCapacity: isPremiumPlus ? 50 : 25,
        allowedBannerStyles: isPremium ? 4 : 1,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve entitlement' });
  }
}

export async function verifyGooglePlayPurchase(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    // In production: Google Play Service Account JSON must be supplied to verify with Google Play API
    if (config.server.isProduction && !config.billing.serviceAccountJson) {
      res.status(503).json({
        success: false,
        error: 'Google Play Billing verification service account is pending server configuration',
      });
      return;
    }

    // Never grant entitlement from a client-asserted or development-only token.
    // The verifier is unavailable until the Play Developer API integration is configured.
    res.status(400).json({
      success: false,
      error: 'Invalid or unverifiable Google Play purchase token receipt',
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Purchase verification failed' });
  }
}
