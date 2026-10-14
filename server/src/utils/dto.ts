import { IUser } from '../models/User';
import { IRoom } from '../models/Room';
import { IMessage } from '../models/Message';

export interface PublicUserDTO {
  id: string;
  username: string;
  displayName: string;
  avatarLetter: string;
  avatarUrl?: string;
  bannerIndex: number;
  bannerUrl?: string;
  tier: string;
  status: string;
  bio: string;
  interests: string[];
  customStatus?: string;
  relationship?: string;
  friendRequestId?: string;
  stats: {
    roomsJoinedCount: number;
    roomsHostedCount: number;
    timeInSyncHours: string;
    friendsCount: number | null;
  };
}

export interface PrivateUserDTO {
  id: string;
  username: string;
  displayName: string;
  avatarLetter: string;
  avatarUrl?: string;
  bannerIndex: number;
  tier: string;
  isTrial?: boolean;
  trialExpiresAt?: Date;
  status: string;
  customStatus?: string;
  presenceStatus?: string;
  bio: string;
  interests: string[];
  isFriendCountPublic: boolean;
  isOnboardingCompleted: boolean;
  dateOfBirth?: string;
  gender?: string;
  bannerUrl?: string;
  role: string;
  stats: {
    roomsJoinedCount: number;
    roomsHostedCount: number;
    timeInSyncHours: string;
  };
  createdAt: Date;
}

export function toPublicUserDTO(user: IUser, relationship: string = 'none'): PublicUserDTO {
  return {
    id: user._id.toString(),
    username: user.username,
    displayName: user.displayName,
    avatarLetter: user.avatarLetter,
    avatarUrl: user.avatarUrl,
    bannerIndex: user.bannerIndex,
    bannerUrl: user.bannerUrl,
    tier: user.tier,
    status: user.status === 'custom' ? (user.customStatus || 'Custom') : user.status === 'offline' || user.presenceStatus === 'offline' ? 'Offline' : user.presenceStatus === 'in_a_room' ? 'In a room' : 'Online',
    customStatus: user.customStatus,
    bio: user.bio,
    interests: (user.interests || []).slice(0, 5),
    relationship,
    stats: {
      roomsJoinedCount: user.stats?.roomsJoinedCount || 0,
      roomsHostedCount: user.stats?.roomsHostedCount || 0,
      timeInSyncHours: ((user.stats?.timeInSyncMinutes || 0) / 60).toFixed(1),
      friendsCount: user.isFriendCountPublic ? ((user as any).friendsCount ?? (user as any).stats?.friendsCount ?? 0) : null,
    },
  };
}

export function toPrivateUserDTO(user: IUser): PrivateUserDTO {
  return {
    id: user._id.toString(),
    username: user.username,
    displayName: user.displayName,
    avatarLetter: user.avatarLetter,
    avatarUrl: user.avatarUrl,
    bannerIndex: user.bannerIndex,
    tier: user.tier,
    isTrial: user.isTrial || false,
    trialExpiresAt: user.trialExpiresAt,
    status: ['dynamic', 'offline', 'custom'].includes(user.status) ? user.status : 'dynamic',
    customStatus: user.customStatus,
    presenceStatus: user.presenceStatus || 'offline',
    bio: user.bio,
    interests: user.interests,
    isFriendCountPublic: user.isFriendCountPublic,
    isOnboardingCompleted: user.isOnboardingCompleted ?? Boolean(user.displayName && user.username),
    dateOfBirth: user.dateOfBirth,
    gender: user.gender,
    bannerUrl: user.bannerUrl,
    role: user.role,
    stats: {
      roomsJoinedCount: user.stats?.roomsJoinedCount || 0,
      roomsHostedCount: user.stats?.roomsHostedCount || 0,
      timeInSyncHours: ((user.stats?.timeInSyncMinutes || 0) / 60).toFixed(1),
    },
    createdAt: user.createdAt,
  };
}

export function toPublicRoomDTO(room: IRoom, currentUserId?: string) {
  const isHost = currentUserId ? room.hostId === currentUserId : false;
  const isMember = currentUserId ? room.participants.some((p) => p.userId === currentUserId) : false;

  return {
    id: room._id.toString(),
    name: room.name,
    hostId: room.hostId,
    hostName: room.participants.find((participant) => participant.userId === room.hostId)?.displayName || 'Host unavailable',
    privacy: room.privacy,
    activity: room.activity,
    category: room.category,
    tags: room.tags || [],
    isAgeRestricted: room.isAgeRestricted || false,
    isEnded: room.isEnded || false,
    mediaState: {
      title: room.mediaState.title,
      subtitle: room.mediaState.subtitle,
      durationSeconds: room.mediaState.durationSeconds,
      playbackPosition: room.mediaState.playbackPosition,
      isPlaying: room.mediaState.isPlaying,
      mediaUrl: room.mediaState.mediaUrl || '',
    },
    participantCount: room.participants.length,
    maxParticipants: room.maxParticipants,
    isMicAllowed: (room as any).isMicAllowed ?? true,
    isVideoAllowed: (room as any).isVideoAllowed ?? true,
    participants: room.participants.map((p) => ({
      userId: p.userId,
      displayName: p.displayName,
      avatarLetter: p.avatarLetter,
      avatarUrl: p.avatarUrl,
      isHost: p.isHost,
      isMuted: p.isMuted ?? false,
      isCameraOff: p.isCameraOff ?? false,
    })),
    userPermissions: {
      isHost,
      isMember,
      canControlPlayback: isHost,
      canKick: isHost,
    },
    createdAt: room.createdAt,
  };
}

export function toMessageDTO(message: IMessage) {
  return {
    id: message._id.toString(),
    roomId: message.roomId,
    conversationId: message.conversationId,
    senderId: message.senderId,
    senderName: message.senderName,
    senderAvatarLetter: message.senderAvatarLetter,
    text: message.text,
    mediaUrl: message.mediaUrl,
    messageType: message.messageType,
    reactions: message.reactions,
    createdAt: message.createdAt,
  };
}
