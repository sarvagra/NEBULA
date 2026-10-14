import { z } from 'zod';

export const GoogleAuthSchema = z.object({
  idToken: z.string().min(10, 'Invalid token length').max(4096),
});

export const OtpRequestSchema = z.object({
  email: z.string().email('Invalid email address format').refine(
    (email) => email.toLowerCase().endsWith('@gmail.com'),
    'Only Gmail addresses ending in @gmail.com are supported'
  ),
});

export const OtpVerifySchema = z.object({
  email: z.string().email('Invalid email address format').refine(
    (email) => email.toLowerCase().endsWith('@gmail.com'),
    'Only Gmail addresses ending in @gmail.com are supported'
  ),
  otp: z.string().regex(/^\d{6}$/, 'OTP must be exactly 6 digits'),
});

export const RefreshTokenSchema = z.object({
  refreshToken: z.string().min(20, 'Invalid refresh token format').max(1024),
});

export const UpdateProfileSchema = z.object({
  displayName: z.string().trim().min(2).max(60).optional(),
  username: z.string().trim().min(3).max(16).regex(/^(?!_)(?!.*_$)[a-z0-9_]+$/, 'Username must be 3 to 16 characters using lowercase letters, numbers, and underscores, without leading or trailing underscores').optional(),
  bio: z.string().trim().max(500).optional(),
  status: z.enum(['dynamic', 'offline', 'custom']).optional(),
  customStatus: z.string().trim().max(80).optional(),
  bannerIndex: z.number().int().min(0).max(4).optional(),
  bannerUrl: z.string().trim().max(5000000).optional(),
  interests: z.array(z.string().trim().max(30)).max(5).optional(),
  avatarUrl: z.string().trim().max(5000000).optional(),
  isFriendCountPublic: z.boolean().optional(),
  isOnboardingCompleted: z.boolean().optional(),
  dateOfBirth: z.string().trim().max(60).optional(),
  gender: z.string().trim().max(60).optional(),
});

export const CompleteOnboardingSchema = z.object({
  displayName: z.string().trim().min(2).max(60),
  username: z.string().trim().min(3).max(16).regex(/^(?!_)(?!.*_$)[a-z0-9_]+$/, 'Username must be 3 to 16 characters using lowercase letters, numbers, and underscores, without leading or trailing underscores'),
  dateOfBirth: z.string().trim().max(60).optional(),
  gender: z.string().trim().max(60).optional(),
  avatarUrl: z.string().trim().max(5000000).optional(),
  bio: z.string().trim().max(500).optional(),
});

export const CreateAssetSchema = z.object({
  assetType: z.enum(['avatar', 'banner']),
  url: z.string().trim().min(1).max(5000000),
  name: z.string().trim().max(100).optional(),
  mimeType: z.string().trim().max(50).optional(),
  sizeBytes: z.number().int().nonnegative().max(10 * 1024 * 1024).optional(),
});

export const CreateRoomSchema = z.object({
  name: z.string().trim().min(2, 'Room name must be at least 2 characters').max(50, 'Room name cannot exceed 50 characters'),
  privacy: z.enum(['friends', 'invite_only', 'public']).default('public'),
  activity: z.enum(['watch', 'chat', 'voice']).default('watch'),
  category: z.string().trim().max(30).default('Social'),
  tags: z.array(z.string().trim().max(30)).max(3).optional().default([]),
  maxParticipants: z.number().int().min(2).max(100).optional(),
  isAgeRestricted: z.boolean().optional().default(false),
  mediaTitle: z.string().trim().max(120).optional(),
  mediaSubtitle: z.string().trim().max(120).optional(),
  mediaUrl: z.string().trim().max(500).optional(),
  isSpoiler: z.boolean().optional().default(false),
  mediaMimeType: z.string().trim().max(80).optional(),
});

export const InviteRoomSchema = z.object({
  userId: z.string().trim().length(24, 'Invalid user ID format'),
});

export const ModerateRoomParticipantSchema = z.object({
  isMuted: z.boolean().optional(),
  isCameraOff: z.boolean().optional(),
}).refine((value) => value.isMuted !== undefined || value.isCameraOff !== undefined);

export const UpdateMediaSchema = z.object({
  title: z.string().trim().max(120).optional(),
  subtitle: z.string().trim().max(120).optional(),
  durationSeconds: z.number().nonnegative().optional(),
  playbackPosition: z.number().nonnegative().optional(),
  isPlaying: z.boolean().optional(),
  mediaUrl: z.string().trim().max(500).optional(),
});

export const SendMessageSchema = z.object({
  roomId: z.string().trim().max(100).optional(),
  conversationId: z.string().trim().max(100).optional(),
  text: z.string().trim().max(2000, 'Message cannot exceed 2000 characters').optional(),
  mediaUrl: z.string().trim().max(500).optional(),
  messageType: z.enum(['text', 'media', 'system']).default('text'),
  isSpoiler: z.boolean().optional().default(false),
  mediaMimeType: z.string().trim().max(80).optional(),
}).refine((data) => !!data.text || !!data.mediaUrl, {
  message: 'Message must contain either text or a media URL',
});

export const AddReactionSchema = z.object({
  emoji: z.string().trim().min(1).max(8),
});

export const EditMessageSchema = z.object({
  text: z.string().trim().min(1).max(2000, 'Message cannot exceed 2000 characters'),
});

export const SendFriendRequestSchema = z.object({
  recipientId: z.string().trim().length(24, 'Invalid user ID format').optional(),
  username: z.string().trim().min(2, 'Username must be at least 2 characters').max(30).optional(),
}).refine((data) => !!data.recipientId || !!data.username, {
  message: 'Must provide either recipientId or username',
});

export const CreatePollSchema = z.object({
  roomId: z.string().trim().min(1),
  question: z.string().trim().min(3).max(150),
  options: z.array(z.string().trim().min(1).max(60)).min(2).max(5),
});

export const VotePollSchema = z.object({
  optionId: z.string().trim().min(1).max(30),
});

export const VerifyPurchaseSchema = z.object({
  purchaseToken: z.string().trim().min(10).max(2048),
  planTier: z.enum(['premium', 'premium_plus']),
});

export const TrackAnalyticsSchema = z.object({
  eventName: z.string().trim().min(2).max(60),
  metadata: z.record(z.any()).optional(),
});

export const PaginationQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
  cursor: z.string().optional(),
  category: z.string().max(50).optional(),
  privacy: z.string().max(50).optional(),
});
