import { Request, Response } from 'express';
import { RoomModel } from '../../models/Room';
import { toPublicRoomDTO } from '../../utils/dto';
import { AnalyticsEventModel } from '../../models/AnalyticsEvent';
import { UserModel } from '../../models/User';

export async function listRooms(req: Request, res: Response): Promise<void> {
  try {
    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 10, 1), 50);
    const category = typeof req.query.category === 'string' ? req.query.category.trim() : undefined;
    const search = typeof req.query.search === 'string' ? req.query.search.trim() : undefined;
    const tag = typeof req.query.tag === 'string' ? req.query.tag.trim() : undefined;

    const filter: Record<string, any> = {
      privacy: { $in: ['public', 'friends'] },
      isEnded: { $ne: true },
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

    res.json({
      success: true,
      data: rooms.map((r) => toPublicRoomDTO(r, req.user?.id)),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to list rooms' });
  }
}

export async function getRoom(req: Request, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const room = await RoomModel.findById(id);
    if (!room) {
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
    const tierLimit = req.user.tier === 'premium_plus' ? 50 : 25;
    const maxParticipants = requestedMax ? Math.min(Math.max(parseInt(String(requestedMax), 10) || 20, 2), tierLimit) : (req.user.tier === 'premium_plus' ? 50 : 20);

    const room = await RoomModel.create({
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
        mediaUrl: mediaUrl || '',
      },
      participants: [
        {
          userId: req.user.id,
          displayName: req.user.displayName,
          avatarLetter: req.user.displayName.charAt(0).toUpperCase(),
          isHost: true,
          joinedAt: new Date(),
        },
      ],
      maxParticipants,
    });

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
    const room = await RoomModel.findById(id);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // Check invite_only privacy
    if (room.privacy === 'invite_only' && room.hostId !== req.user.id) {
      res.status(403).json({ success: false, error: 'Cannot join an invite-only room without an invitation' });
      return;
    }

    const alreadyIn = room.participants.some((p) => p.userId === req.user!.id);
    if (!alreadyIn) {
      if (room.participants.length >= room.maxParticipants) {
        res.status(400).json({ success: false, error: 'Room has reached maximum participant capacity' });
        return;
      }
      room.participants.push({
        userId: req.user.id,
        displayName: req.user.displayName,
        avatarLetter: req.user.displayName.charAt(0).toUpperCase(),
        joinedAt: new Date(),
        isHost: room.hostId === req.user.id,
      });
      await room.save();

      // Record trusted server analytics event
      await AnalyticsEventModel.create({
        userId: req.user.id,
        eventName: 'ROOM_JOINED',
        metadata: { roomId: room._id.toString() },
      });
      await UserModel.findByIdAndUpdate(req.user.id, { $inc: { 'stats.roomsJoinedCount': 1 } });
    }

    res.json({ success: true, data: toPublicRoomDTO(room, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to join room' });
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

    room.participants = room.participants.filter((p) => p.userId !== req.user!.id);

    // If last participant leaves, completely DISSOLVE the room from the database
    if (room.participants.length === 0) {
      await RoomModel.findByIdAndDelete(id);
      res.json({ success: true, message: 'Room dissolved as last participant left', dissolved: true });
      return;
    }

    // If host leaves and other participants remain:
    if (room.hostId === req.user.id) {
      let selectedHost = room.participants.find((p) => p.userId === newHostId);
      if (!selectedHost) {
        // Randomly select next host from remaining participants
        const randomIndex = Math.floor(Math.random() * room.participants.length);
        selectedHost = room.participants[randomIndex];
      }
      room.hostId = selectedHost.userId;
      room.participants.forEach((p) => {
        p.isHost = p.userId === room.hostId;
      });
    }

    await room.save();
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
    if (mediaUrl !== undefined) room.mediaState.mediaUrl = mediaUrl;

    await room.save();
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

    room.participants = room.participants.filter((p) => p.userId !== targetUserId);
    await room.save();

    res.json({ success: true, message: 'Participant kicked successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to kick participant' });
  }
}
