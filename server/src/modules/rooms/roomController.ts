import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { RoomModel } from '../../models/Room';
import { notifyUser } from '../notifications/notificationController';
import { toPublicRoomDTO } from '../../utils/dto';
import { AnalyticsEventModel } from '../../models/AnalyticsEvent';
import { UserModel } from '../../models/User';
import { FriendshipModel } from '../../models/Friendship';
import { NotificationModel } from '../../models/Notification';
import { notifyRoomDissolved, notifyRoomHostChanged, notifyRoomMediaSwitch, notifyRoomParticipantKicked, notifyRoomParticipantLeft, notifyRoomParticipantUpdated } from '../../realtime/socketServer';
import { entitlementsForTier } from '../../utils/entitlements';
import { tryVerifyPlayableLink } from '../../services/mediaLinkService';

async function claimActiveRoom(userId: string, roomId: string): Promise<boolean> {
  const otherMembership = await RoomModel.findOne({
    _id: { $ne: roomId },
    isEnded: { $ne: true },
    'participants.userId': userId,
  }).select('_id');
  if (otherMembership) return false;

  const result = await UserModel.updateOne(
    { _id: userId, $or: [{ activeRoomId: null }, { activeRoomId: roomId }, { activeRoomId: { $exists: false } }] },
    { $set: { activeRoomId: roomId, presenceStatus: 'in_a_room' } }
  );
  return result.matchedCount > 0;
}

async function releaseActiveRoom(userId: string, roomId: string): Promise<void> {
  await UserModel.updateOne(
    { _id: userId, activeRoomId: roomId },
    { $set: { activeRoomId: null, presenceStatus: 'online' } }
  );
}

export async function listRooms(req: Request, res: Response): Promise<void> {
  try {
    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 10, 1), 50);
    const category = typeof req.query.category === 'string' ? req.query.category.trim() : undefined;
    const search = typeof req.query.search === 'string' ? req.query.search.trim() : undefined;
    const tag = typeof req.query.tag === 'string' ? req.query.tag.trim() : undefined;

    const filter: Record<string, any> = {
      privacy: { $in: ['public', 'friends'] },
      isEnded: { $ne: true },
      'participants.0': { $exists: true },
    };

    if (category && category !== 'All') {
      filter.category = category;
    }

    if (search) {
      filter.name = { $regex: search, $options: 'i' };
    }

    if (tag) {
      filter.tags = tag;
    }

    const rooms = await RoomModel.find(filter)
      .sort({ createdAt: -1 })
      .limit(limit);

    const visibleRooms = rooms.filter((room) => !req.user || !room.kickedUserIds?.includes(req.user.id));
    res.json({
      success: true,
      data: visibleRooms.map((r) => toPublicRoomDTO(r, req.user?.id)),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to list rooms' });
  }
}

export async function getRoom(req: Request, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    let room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }
    if (room.participants.length === 0) {
      await RoomModel.deleteOne({ _id: room._id, 'participants.0': { $exists: false } });
      res.status(404).json({ success: false, error: 'Room has no active members' });
      return;
    }
    if (req.user && room.kickedUserIds?.includes(req.user.id)) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // Visibility check: invite-only rooms can only be viewed by participants
    if (room.privacy === 'invite_only') {
      const isMember = req.user ? room.participants.some((p) => p.userId === req.user!.id) : false;
      if (!isMember && req.user?.role !== 'admin') {
        res.status(403).json({ success: false, error: 'This room is invite-only' });
        return;
      }
    }

    res.json({ success: true, data: toPublicRoomDTO(room, req.user?.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve room' });
  }
}

export async function createRoom(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { name, privacy, activity, category, tags, maxParticipants: requestedMax, isAgeRestricted, mediaTitle, mediaSubtitle, mediaUrl } = req.body;

    const safeTags = Array.isArray(tags) ? tags.slice(0, 3).map((t: any) => String(t).trim()) : [];

    // Server-enforced max participants based on authenticated user tier, respecting client preference
    const tierLimit = entitlementsForTier(req.user.tier).maxRoomParticipants;
    const defaultParticipants = req.user.tier === 'premium_plus' ? 50 : 20;
    const maxParticipants = requestedMax ? Math.min(Math.max(parseInt(String(requestedMax), 10) || 20, 2), tierLimit) : defaultParticipants;

    // A room may only open onto a verified playable video source.
    let verifiedMediaUrl = '';
    if (mediaUrl) {
      const verification = await tryVerifyPlayableLink(String(mediaUrl));
      if (!verification.ok) {
        res.status(422).json({ success: false, code: verification.code, error: verification.error });
        return;
      }
      verifiedMediaUrl = verification.link.sourceUrl;
    }

    const roomId = new mongoose.Types.ObjectId().toString();
    const creatorProfile = await UserModel.findById(req.user.id).select('username displayName avatarLetter avatarUrl bannerUrl bio status interests');
    if (!(await claimActiveRoom(req.user.id, roomId))) {
      res.status(409).json({ success: false, error: 'Leave your current room before creating another one' });
      return;
    }

    let room;
    try {
      room = await RoomModel.create({
      _id: roomId,
      name: name.trim(),
      hostId: req.user.id, // Strictly server-authoritative
      privacy: privacy || 'public',
      activity: activity || 'watch',
      category: category || 'Social',
      tags: safeTags,
      isAgeRestricted: Boolean(isAgeRestricted),
      isEnded: false,
      mediaState: {
        title: mediaTitle || 'Ambient Stream',
        subtitle: mediaSubtitle || 'Synchronized Session',
        durationSeconds: 3600,
        playbackPosition: 0,
        isPlaying: true,
        mediaUrl: verifiedMediaUrl,
      },
      participants: [
        {
          userId: req.user.id,
          username: creatorProfile?.username || req.user.username,
          displayName: creatorProfile?.displayName || req.user.displayName,
          avatarLetter: creatorProfile?.avatarLetter || req.user.displayName.charAt(0).toUpperCase(),
          avatarUrl: creatorProfile?.avatarUrl,
          bannerUrl: creatorProfile?.bannerUrl,
          bio: creatorProfile?.bio,
          status: creatorProfile?.status,
          interests: creatorProfile?.interests || [],
          isHost: true,
          joinedAt: new Date(),
        },
      ],
      maxParticipants,
      });
    } catch (error) {
      await releaseActiveRoom(req.user.id, roomId);
      throw error;
    }

    // Record trusted server analytics event
    await AnalyticsEventModel.create({
      userId: req.user.id,
      eventName: 'ROOM_CREATED',
      metadata: { roomId: room._id.toString(), activity },
    });
    await UserModel.findByIdAndUpdate(req.user.id, { $inc: { 'stats.roomsHostedCount': 1 } });

    res.status(201).json({ success: true, data: toPublicRoomDTO(room, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to create room' });
  }
}

export async function joinRoom(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    let room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    if (room.participants.length === 0) {
      await RoomModel.deleteOne({ _id: room._id, 'participants.0': { $exists: false } });
      res.status(404).json({ success: false, error: 'Room has no active members' });
      return;
    }

    if (room.kickedUserIds?.includes(req.user.id)) {
      res.status(403).json({ success: false, error: 'The room host removed you from this room' });
      return;
    }

    // Check invite_only privacy
    let acceptedInvite: any = null;
    if (room.privacy === 'invite_only' && room.hostId !== req.user.id &&
        !room.participants.some((p) => p.userId === req.user!.id)) {
      acceptedInvite = await NotificationModel.findOne({
        userId: req.user.id,
        type: 'room_invite',
        'actionData.roomId': room._id.toString(),
        expiresAt: { $gt: new Date() },
      });
      if (!acceptedInvite) {
        res.status(403).json({ success: false, error: 'Cannot join an invite-only room without an invitation' });
        return;
      }
    }

    const alreadyIn = room.participants.some((p) => p.userId === req.user!.id);
    if (alreadyIn) {
      res.status(409).json({
        success: false,
        error: 'Your account is already active in this room on another device. Only one device per account is allowed to join a room at any time.',
      });
      return;
    }

    const joiningProfile = await UserModel.findById(req.user.id).select('username displayName avatarLetter avatarUrl bannerUrl bio status interests');
    if (!(await claimActiveRoom(req.user.id, room._id.toString()))) {
      res.status(409).json({
        success: false,
        error: 'Your account is currently active in another room on another device. Only one device per account is allowed to join a room at any time.',
      });
      return;
    }
    if (!alreadyIn) {
      const updated = await RoomModel.findOneAndUpdate(
        {
          _id: room._id,
          'participants.userId': { $ne: req.user.id },
          $expr: { $lt: [{ $size: '$participants' }, '$maxParticipants'] },
        } as any,
        { $push: { participants: {
        userId: req.user.id,
        username: joiningProfile?.username || req.user.username,
        displayName: joiningProfile?.displayName || req.user.displayName,
        avatarLetter: joiningProfile?.avatarLetter || req.user.displayName.charAt(0).toUpperCase(),
        avatarUrl: joiningProfile?.avatarUrl,
        bannerUrl: joiningProfile?.bannerUrl,
        bio: joiningProfile?.bio,
        status: joiningProfile?.status,
        interests: joiningProfile?.interests || [],
        joinedAt: new Date(),
        isHost: room.hostId === req.user.id,
        } } },
        { new: true }
      );
      if (!updated) {
        await releaseActiveRoom(req.user.id, room._id.toString());
        res.status(409).json({ success: false, error: 'Room is full or your membership changed. Refresh and try again.' });
        return;
      }
      room = updated;

      // Record trusted server analytics event
      await AnalyticsEventModel.create({
        userId: req.user.id,
        eventName: 'ROOM_JOINED',
        metadata: { roomId: room._id.toString() },
      });
      await UserModel.findByIdAndUpdate(req.user.id, { $inc: { 'stats.roomsJoinedCount': 1 } });
    }
    if (acceptedInvite) {
      acceptedInvite.isRead = true;
      await acceptedInvite.save();
    }

    res.json({ success: true, data: toPublicRoomDTO(room, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to join room' });
  }
}

export async function inviteRoomParticipant(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }
    const room = await RoomModel.findById(req.params.id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }
    if (!room.participants.some((participant) => participant.userId === req.user!.id)) {
      res.status(403).json({ success: false, error: 'Only current room members can invite participants' });
      return;
    }
    const { userId } = req.body as { userId: string };
    if (userId === req.user.id || !room.participants.some((p) => p.userId === req.user!.id)) {
      res.status(400).json({ success: false, error: 'Invalid room invitation target' });
      return;
    }
    if (room.participants.some((p) => p.userId === userId)) {
      res.status(409).json({ success: false, error: 'That user is already in this room' });
      return;
    }
    const friendship = await FriendshipModel.findOne({
      $or: [
        { requesterId: req.user.id, recipientId: userId, status: 'accepted' },
        { requesterId: userId, recipientId: req.user.id, status: 'accepted' },
      ],
    });
    if (!friendship) {
      res.status(403).json({ success: false, error: 'You can only invite accepted friends' });
      return;
    }

    // N-034: block enforcement — a block from either direction prevents invites.
    const blockRecord = await FriendshipModel.findOne({
      status: 'blocked',
      $or: [
        { requesterId: req.user.id, recipientId: userId },
        { requesterId: userId, recipientId: req.user.id },
      ],
    }).select('_id');
    if (blockRecord) {
      res.status(403).json({ success: false, error: 'Unable to invite this user' });
      return;
    }
    const recipient = await UserModel.findById(userId).select('_id isBanned');
    if (!recipient || recipient.isBanned) {
      res.status(404).json({ success: false, error: 'User not found or unavailable' });
      return;
    }
    await NotificationModel.deleteMany({ userId, type: 'room_invite', expiresAt: { $lte: new Date() } });
    const existing = await NotificationModel.findOne({
      userId,
      type: 'room_invite',
      isRead: false,
      'actionData.roomId': room._id.toString(),
    });
    if (existing) {
      res.status(409).json({ success: false, error: 'An invitation is already pending' });
      return;
    }
    const invite = await NotificationModel.create({
      userId,
      type: 'room_invite',
      title: room.name,
      body: `${req.user.displayName} invited you to join a room.`,
      actionData: { roomId: room._id.toString(), inviterId: req.user.id },
      expiresAt: new Date(Date.now() + 5 * 60 * 1000),
    });
    void notifyUser(userId, 'Room invitation', `${req.user.displayName} invited you to ${room.name}.`, { type: 'room_invite', roomId: room._id.toString() });
    res.status(201).json({ success: true, data: invite.toJSON() });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to invite participant' });
  }
}

export async function leaveRoom(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    const { newHostId } = req.body || {};
    const wasHost = room.hostId === req.user.id;

    room.participants = room.participants.filter((p) => p.userId !== req.user!.id);

    // If last participant leaves, completely DISSOLVE the room from the database
    if (room.participants.length === 0) {
      await RoomModel.deleteOne({ _id: room._id });
      await releaseActiveRoom(req.user.id, id);
      notifyRoomParticipantLeft(id, req.user.id);
      notifyRoomDissolved(id);
      res.json({ success: true, message: 'Left room successfully', dissolved: true });
      return;
    }

    // If host leaves and other participants remain:
    if (wasHost) {
      let selectedHost = room.participants.find((p) => p.userId === newHostId);
      if (!selectedHost) {
        // Randomly select next host from remaining participants
        const randomIndex = Math.floor(Math.random() * room.participants.length);
        selectedHost = room.participants[randomIndex];
      }
      room.hostId = selectedHost.userId;
      room.kickedUserIds = [];
      room.participants.forEach((p) => {
        p.isHost = p.userId === room.hostId;
      });
    }

    await room.save();
    await releaseActiveRoom(req.user.id, id);
    notifyRoomParticipantLeft(id, req.user.id);
    if (wasHost) notifyRoomHostChanged(id, room.hostId);
    res.json({ success: true, message: 'Left room successfully', dissolved: false });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to leave room' });
  }
}

export async function updateRoomSettings(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // Only host or admin can update room settings
    if (room.hostId !== req.user.id && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Only room host or admin can update room settings' });
      return;
    }

    const { name, maxParticipants, isMicAllowed, isVideoAllowed, tags } = req.body;

    if (name && typeof name === 'string' && name.trim().length >= 2) {
      room.name = name.trim();
    }
    if (typeof maxParticipants === 'number' && maxParticipants > 0) {
      room.maxParticipants = maxParticipants;
    }
    if (typeof isMicAllowed === 'boolean') {
      room.isMicAllowed = isMicAllowed;
    }
    if (typeof isVideoAllowed === 'boolean') {
      room.isVideoAllowed = isVideoAllowed;
    }
    if (Array.isArray(tags)) {
      room.tags = tags.map((t: any) => String(t).trim()).filter(Boolean).slice(0, 5);
    }

    await room.save();
    notifyRoomHostChanged(id, room.hostId);
    res.json({ success: true, data: toPublicRoomDTO(room, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to update room settings' });
  }
}

export async function transferHost(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    if (room.hostId !== req.user.id && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Only the current host can transfer host ownership' });
      return;
    }

    const { newHostId, random } = req.body;
    let target = room.participants.find((p) => p.userId === newHostId);

    if (!target && (random || !newHostId)) {
      const candidates = room.participants.filter((p) => p.userId !== req.user!.id);
      if (candidates.length > 0) {
        target = candidates[Math.floor(Math.random() * candidates.length)];
      }
    }

    if (!target) {
      res.status(400).json({ success: false, error: 'No eligible participant found to receive host role' });
      return;
    }

    room.hostId = target.userId;
    room.kickedUserIds = [];
    room.participants.forEach((p) => {
      p.isHost = p.userId === target.userId;
    });

    await room.save();
    res.json({ success: true, data: toPublicRoomDTO(room, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to transfer room host' });
  }
}

export async function updateMedia(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // STRICT SERVER AUTHORIZATION: Only room host can change synchronized media
    if (room.hostId !== req.user.id && req.user.role !== 'admin') {
      res.status(403).json({
        success: false,
        error: 'Forbidden: Only the room host has permission to control synchronized playback',
      });
      return;
    }

    const { title, subtitle, durationSeconds, playbackPosition, isPlaying, mediaUrl } = req.body;

    if (title !== undefined) room.mediaState.title = title;
    if (subtitle !== undefined) room.mediaState.subtitle = subtitle;
    if (durationSeconds !== undefined) room.mediaState.durationSeconds = durationSeconds;
    if (playbackPosition !== undefined) room.mediaState.playbackPosition = playbackPosition;
    if (isPlaying !== undefined) room.mediaState.isPlaying = isPlaying;
    if (mediaUrl !== undefined) {
      if (String(mediaUrl).trim() === '') {
        room.mediaState.mediaUrl = '';
      } else {
        // Reject image pages, unavailable files, and unsupported hosts outright.
        const verification = await tryVerifyPlayableLink(String(mediaUrl));
        if (!verification.ok) {
          res.status(422).json({ success: false, code: verification.code, error: verification.error });
          return;
        }
        room.mediaState.mediaUrl = verification.link.sourceUrl;
        if (title === undefined) room.mediaState.title = verification.link.title;
        if (verification.link.thumbnailUrl === undefined && !room.mediaState.subtitle) {
          room.mediaState.subtitle = 'Synchronized Session';
        }
      }
    }

    if (mediaUrl !== undefined || title !== undefined) {
      room.mediaState.playbackPosition = 0;
      room.mediaState.isPlaying = true;
    }
    await room.save();
    if (mediaUrl !== undefined || title !== undefined) notifyRoomMediaSwitch(room._id.toString(), room.mediaState);
    res.json({ success: true, data: room.mediaState });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to update media playback' });
  }
}

export async function kickParticipant(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id, targetUserId } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // STRICT AUTHORIZATION: Only host can kick participants
    if (room.hostId !== req.user.id && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Forbidden: Only the room host can kick participants' });
      return;
    }

    if (targetUserId === room.hostId) {
      res.status(400).json({ success: false, error: 'Host cannot kick themselves' });
      return;
    }

    if (!room.participants.some((p) => p.userId === targetUserId)) {
      res.status(404).json({ success: false, error: 'Participant is not in this room' });
      return;
    }

    room.participants = room.participants.filter((p) => p.userId !== targetUserId);
    room.kickedUserIds = [...new Set([...(room.kickedUserIds || []), targetUserId])];
    await room.save();
    await releaseActiveRoom(targetUserId, id);
    notifyRoomParticipantKicked(id, targetUserId);

    res.json({ success: true, message: 'Participant kicked successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to kick participant' });
  }
}

export async function moderateRoomParticipant(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }
    const { id, targetUserId } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }
    if (room.hostId !== req.user.id && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Only the host can moderate room participants' });
      return;
    }
    if (targetUserId === room.hostId) {
      res.status(400).json({ success: false, error: 'The host cannot moderate themselves' });
      return;
    }
    const participant = room.participants.find((item) => item.userId === targetUserId);
    if (!participant) {
      res.status(404).json({ success: false, error: 'Participant is not in this room' });
      return;
    }
    if (typeof req.body.isMuted === 'boolean') participant.isMuted = req.body.isMuted;
    if (typeof req.body.isCameraOff === 'boolean') participant.isCameraOff = req.body.isCameraOff;
    await room.save();
    const updatedParticipant = {
      userId: participant.userId,
      isMuted: participant.isMuted ?? false,
      isCameraOff: participant.isCameraOff ?? false,
    };
    notifyRoomParticipantUpdated(id, updatedParticipant);
    res.json({ success: true, data: updatedParticipant });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to update participant moderation' });
  }
}
