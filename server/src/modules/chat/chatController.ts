import { Request, Response } from 'express';
import { MessageModel } from '../../models/Message';
import { RoomModel } from '../../models/Room';
import { FriendshipModel } from '../../models/Friendship';
import { toMessageDTO } from '../../utils/dto';
import { uploadObject } from '../../services/objectStorage';

export async function getRoomMessages(req: Request, res: Response): Promise<void> {
  try {
    const { roomId } = req.params;
    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 50, 1), 50);

    const room = await RoomModel.findById(roomId);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // Authorization: if room is invite-only, verify membership
    if (room.privacy === 'invite_only') {
      const isMember = req.user ? room.participants.some((p) => p.userId === req.user!.id) : false;
      if (!isMember && req.user?.role !== 'admin') {
        res.status(403).json({ success: false, error: 'Forbidden: Cannot access messages of private room' });
        return;
      }
    }

    const messages = await MessageModel.find({ roomId })
      .sort({ createdAt: -1 })
      .limit(limit);

    res.json({
      success: true,
      data: messages.reverse().map(toMessageDTO),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve messages' });
  }
}

export async function getConversationMessages(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { conversationId } = req.params;
    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 50, 1), 50);

    // Verify conversation access: conversationId must involve req.user.id
    const messages = await MessageModel.find({
      conversationId,
      $or: [{ senderId: req.user.id }, { conversationId: req.user.id }],
    })
      .sort({ createdAt: -1 })
      .limit(limit);

    res.json({
      success: true,
      data: messages.reverse().map(toMessageDTO),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve direct conversation' });
  }
}

// Track media rate-limiting and bans per user per room to prevent image/GIF spam
interface RoomMediaState {
  timestamps: number[];
  cooldownUntil: number;
  isBanned: boolean;
}
const roomMediaTracker = new Map<string, RoomMediaState>();

export async function sendMessage(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { roomId, conversationId, text, mediaUrl, messageType } = req.body;

    // 1. If sending to a ROOM: verify that the user is an active participant in that room
    if (roomId) {
      const room = await RoomModel.findById(roomId);
      if (!room) {
        res.status(404).json({ success: false, error: 'Room not found' });
        return;
      }

      const isMember = room.participants.some((p) => p.userId === req.user!.id);
      if (!isMember && req.user.role !== 'admin') {
        res.status(403).json({
          success: false,
          error: 'Forbidden: You must join the room before sending messages',
        });
        return;
      }

      // Check if message includes media
      if (mediaUrl || messageType === 'media') {
        const trackerKey = `${req.user.id}:${roomId}`;
        const tracker = roomMediaTracker.get(trackerKey) || {
          timestamps: [],
          cooldownUntil: 0,
          isBanned: false,
        };

        // Banned check
        if (tracker.isBanned) {
          res.status(403).json({
            success: false,
            error: 'You are banned from sending files in this room due to repeated spam',
          });
          return;
        }

        const now = Date.now();

        // Check if currently under cooldown
        if (now < tracker.cooldownUntil) {
          // Attempting to send again during cooldown triggers an immediate file sharing ban
          tracker.isBanned = true;
          roomMediaTracker.set(trackerKey, tracker);
          res.status(403).json({
            success: false,
            error: 'You attempted to send files during cooldown. You are now banned from sharing files in this room.',
          });
          return;
        }

        // Strict file format check (Images & GIFs only, rejecting video & documents)
        const lowerUrl = (mediaUrl || '').toLowerCase();
        const isAllowedImage =
          lowerUrl.endsWith('.jpg') ||
          lowerUrl.endsWith('.jpeg') ||
          lowerUrl.endsWith('.png') ||
          lowerUrl.endsWith('.gif') ||
          lowerUrl.endsWith('.webp') ||
          lowerUrl.includes('image/') ||
          lowerUrl.includes('format=jpg') ||
          lowerUrl.includes('format=png') ||
          lowerUrl.includes('giphy.com') ||
          lowerUrl.includes('tenor.com');

        const isForbiddenVideo =
          lowerUrl.endsWith('.mp4') ||
          lowerUrl.endsWith('.mov') ||
          lowerUrl.endsWith('.avi') ||
          lowerUrl.endsWith('.mkv') ||
          lowerUrl.endsWith('.webm') ||
          lowerUrl.includes('video/');

        if (isForbiddenVideo || (!isAllowedImage && lowerUrl.length > 0)) {
          res.status(400).json({
            success: false,
            error: 'Invalid media format: Only images and GIFs are permitted in room chats',
          });
          return;
        }

        // Rate limiting check: max 2 media in 10 seconds
        tracker.timestamps = tracker.timestamps.filter((t) => now - t < 10000);
        if (tracker.timestamps.length >= 2) {
          tracker.cooldownUntil = now + 15000; // 15-second cooldown
          roomMediaTracker.set(trackerKey, tracker);
          res.status(429).json({
            success: false,
            error: 'Media cooldown active: You are sending files too fast. Please wait 15 seconds.',
            cooldownSeconds: 15,
          });
          return;
        }

        tracker.timestamps.push(now);
        roomMediaTracker.set(trackerKey, tracker);
      }
    }

    // 2. If sending a DIRECT MESSAGE: verify friendship exists to prevent spam
    if (conversationId && !roomId) {
      const friendship = await FriendshipModel.findOne({
        $or: [
          { requesterId: req.user.id, recipientId: conversationId },
          { requesterId: conversationId, recipientId: req.user.id },
        ],
        status: 'accepted',
      });

      if (!friendship && req.user.role !== 'admin') {
        res.status(403).json({
          success: false,
          error: 'Forbidden: You can only send direct messages to accepted friends',
        });
        return;
      }
    }

    const message = await MessageModel.create({
      roomId,
      conversationId,
      senderId: req.user.id, // Server authoritative
      senderName: req.user.displayName,
      senderAvatarLetter: req.user.displayName.charAt(0).toUpperCase(),
      text: text?.trim() || '',
      mediaUrl,
      messageType: messageType || 'text',
      reactions: [],
    });

    res.status(201).json({ success: true, data: toMessageDTO(message) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to send message' });
  }
}

export async function addReaction(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { messageId } = req.params;
    const { emoji } = req.body;

    const message = await MessageModel.findById(messageId);
    if (!message) {
      res.status(404).json({ success: false, error: 'Message not found' });
      return;
    }

    // If message is in a room, verify room membership
    if (message.roomId) {
      const room = await RoomModel.findById(message.roomId);
      if (room && !room.participants.some((p) => p.userId === req.user!.id)) {
        res.status(403).json({ success: false, error: 'Forbidden: You must be in the room to react to messages' });
        return;
      }
    }

    let reaction = message.reactions.find((r) => r.emoji === emoji);
    if (!reaction) {
      reaction = { emoji, count: 1, userIds: [req.user.id] };
      message.reactions.push(reaction);
    } else {
      if (!reaction.userIds.includes(req.user.id)) {
        reaction.userIds.push(req.user.id);
        reaction.count = reaction.userIds.length;
      }
    }

    await message.save();
    res.json({ success: true, data: toMessageDTO(message) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to add reaction' });
  }
}

const ALLOWED_CHAT_MIMES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'video/mp4',
  'video/webm',
];

export async function uploadMedia(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { url, mimeType, sizeBytes, mediaType } = req.body;

    if (!url || typeof url !== 'string') {
      res.status(400).json({ success: false, error: 'Valid media URL or data is required' });
      return;
    }

    const resolvedMime = (mimeType || 'image/jpeg').toLowerCase();
    if (!ALLOWED_CHAT_MIMES.includes(resolvedMime)) {
      res.status(400).json({
        success: false,
        error: 'Unsupported media format. Only standard images (JPEG, PNG, WEBP, GIF) and videos (MP4, WEBM) are supported.',
      });
      return;
    }

    const resolvedSize = Number(sizeBytes) || 0;
    const maxSize = resolvedMime.startsWith('video/') ? 30 * 1024 * 1024 : 5 * 1024 * 1024;
    if (resolvedSize > maxSize) {
      res.status(413).json({
        success: false,
        error: `Media attachment exceeds maximum allowed size of ${maxSize / (1024 * 1024)}MB.`,
      });
      return;
    }

    const type = mediaType || (resolvedMime.startsWith('video/') ? 'video' : resolvedMime.includes('gif') ? 'gif' : 'image');
    const mediaUrl = await uploadObject(
      url,
      `users/${req.user.id}/chat/${Date.now()}_${type}.${resolvedMime.split('/')[1]}`,
      resolvedMime
    );

    res.status(201).json({
      success: true,
      data: {
        mediaUrl,
        mimeType: resolvedMime,
        mediaType: type,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to upload chat media' });
  }
}
