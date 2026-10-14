import { getMessaging } from 'firebase-admin/messaging';
import { getFirebaseAdmin } from './firebaseService';

export async function sendDeviceNotification(
  tokens: string[],
  title: string,
  body: string,
  data: Record<string, string> = {},
): Promise<void> {
  const distinctTokens = [...new Set(tokens.filter(Boolean))];
  if (!distinctTokens.length) return;
  try {
    const result = await getMessaging(getFirebaseAdmin()).sendEachForMulticast({
      tokens: distinctTokens,
      notification: { title, body },
      data,
      android: {
        priority: 'high',
        ...(data.type === 'room_invite' ? { ttl: 5 * 60 * 1000 } : {}),
        notification: { channelId: 'nebula_social' },
      },
    });
    const invalidTokens = result.responses.flatMap((response, index) =>
      !response.success && ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(response.error?.code || '')
        ? [distinctTokens[index]] : []
    );
    if (invalidTokens.length) {
      const { UserModel } = await import('../models/User');
      await UserModel.updateMany({ fcmTokens: { $in: invalidTokens } }, { $pull: { fcmTokens: { $in: invalidTokens } } });
    }
  } catch (error) {
    console.warn('[FCM] Could not deliver push notification:', error instanceof Error ? error.message : 'unknown error');
  }
}
