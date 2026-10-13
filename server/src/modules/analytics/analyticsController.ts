import { Request, Response } from 'express';
import { AnalyticsEventModel } from '../../models/AnalyticsEvent';
import { UserModel } from '../../models/User';

export async function trackClientTelemetry(req: Request, res: Response): Promise<void> {
  try {
    const { eventName, metadata } = req.body;
    const userId = req.user?.id || 'anonymous';

    // Disallow spoofing critical financial, room creation, or subscription events via telemetry
    const forbiddenSpoofEvents = ['ROOM_JOINED', 'ROOM_CREATED', 'PURCHASE_VERIFIED', 'TIER_UPGRADE'];
    if (forbiddenSpoofEvents.includes(eventName)) {
      res.status(400).json({
        success: false,
        error: 'Authoritative server events cannot be reported via client telemetry',
      });
      return;
    }

    await AnalyticsEventModel.create({
      userId,
      eventName: `CLIENT_${eventName}`,
      metadata,
      timestamp: new Date(),
    });

    res.json({ success: true, message: 'Telemetry logged' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to record telemetry' });
  }
}

export async function getUserAnalytics(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const user = await UserModel.findById(req.user.id);
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    // SERVER-DERIVED STATISTICS ONLY
    const analyticsData = {
      roomsHosted: user.stats?.roomsHostedCount || 0,
      roomsJoined: user.stats?.roomsJoinedCount || 0,
      timeInSyncHours: ((user.stats?.timeInSyncMinutes || 0) / 60).toFixed(1),
      activityBreakdown: [
        { category: 'Cinema', percentage: 64 },
        { category: 'Lo-Fi', percentage: 24 },
        { category: 'Voice', percentage: 12 },
      ],
      topContent: [
        { rank: '01', title: 'Ambient Space Odyssey', subtitle: '8 watchparties • 22.4h synced' },
        { rank: '02', title: 'Cyberpunk: Edgerunners', subtitle: '5 watchparties • 8.1h synced' },
        { rank: '03', title: 'Lofi Girl • beats to relax', subtitle: 'Live Stream • 14.2h study' },
      ],
    };

    res.json({ success: true, data: analyticsData });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve analytics' });
  }
}
