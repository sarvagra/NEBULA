import { Request, Response } from 'express';
import { MessageModel } from '../../models/Message';
import { UserModel } from '../../models/User';
import { RoomModel } from '../../models/Room';
import { FriendshipModel } from '../../models/Friendship';
import { toMessageDTO } from '../../utils/dto';
import { uploadObject } from '../../services/objectStorage';
import { notifyUser } from '../notifications/notificationController';
import { notifyConversationRead, notifyDirectMessage } from '../../realtime/socketServer';
import { NotificationModel } from '../../models/Notification';
import { dmRetentionCutoff, dmRetentionCutoffForDays, entitlementsForTier, messageEditLimitForTier } from '../../utils/entitlements';
import { validateImageResolution } from '../../utils/imageSecurity';

const UNSEND_WINDOW_MS = 2 * 60 * 1000;

/**
 * Incremental history reads. A client that lost its realtime connection asks for
 * everything after the last message it already holds instead of pulling a full page
 * and hoping nothing fell between two pages. An unparseable cursor is rejected rather
 * than silently treated as "everything", which would hide a client bug behind a
 * correct-looking response.
 */
function parseSinceCursor(raw: unknown): { since: Date | null; invalid: boolean } {
  if (typeof raw !== 'string' || raw.trim() === '') return { since: null, invalid: false };
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return { since: null, invalid: true };
  return { since: parsed, invalid: false };
}

/**
 * Retention for a two-party conversation. A thread is only as long-lived as the
 * shorter of the two participants' entitlement windows, so a Basic participant
 * never inherits Premium history depth (and vice versa).
 */
async function retentionContext(userId: string, otherUserId?: string) {
  const ids = otherUserId ? [userId, otherUserId] : [userId];
  const users = await UserModel.find({ _id: { $in: ids } }).select('tier');
  const tiers = users.length ? users.map((user) => user.tier) : ['free'];
  const shortestDays = tiers.reduce(
    (min, tier) => Math.min(min, entitlementsForTier(tier).dmRetentionDays),
    Number.POSITIVE_INFINITY,
  );
  const effectiveTier = tiers.find((tier) => entitlementsForTier(tier).dmRetentionDays === shortestDays) || 'free';
  return {
    tier: effectiveTier,
    cutoff: dmRetentionCutoffForDays(shortestDays),
    retention: entitlementsForTier(effectiveTier),
  };
}

async function authorizedDirectMessage(req: Request, messageId: string) {
  const message = await MessageModel.findById(messageId);
  if (!message || !req.user || message.roomId || message.senderId !== req.user.id) return null;
  return message;
}

export async function getRoomMessages(req: Request, res: Response): Promise<void> {
  try {
    const { roomId } = req.params;
    const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 50, 1), 50);
    const { since, invalid } = parseSinceCursor(req.query.since);
    if (invalid) {
      res.status(400).json({ success: false, error: 'Invalid `since` cursor: expected an ISO-8601 timestamp' });
      return;
    }

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

    const messages = await MessageModel.find(since ? { roomId, createdAt: { $gte: since } } : { roomId })
      .sort({ createdAt: since ? 1 : -1 })
      .limit(limit);
    const ordered = since ? messages : messages.reverse();
    const senders = await UserModel.find({ _id: { $in: [...new Set(ordered.map((message) => message.senderId))] } }).select('_id avatarUrl');
    const avatarByUserId = new Map(senders.map((sender) => [sender._id.toString(), sender.avatarUrl]));

    res.json({
      success: true,
      data: ordered.map((message) => ({ ...toMessageDTO(message), senderAvatarUrl: avatarByUserId.get(message.senderId) || message.senderAvatarUrl })),
      meta: {
        since: since ? since.toISOString() : null,
        serverTime: new Date().toISOString(),
        count: ordered.length,
      },
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
    const { since, invalid } = parseSinceCursor(req.query.since);
    if (invalid) {
      res.status(400).json({ success: false, error: 'Invalid `since` cursor: expected an ISO-8601 timestamp' });
      return;
    }

    const friendship = await FriendshipModel.findOne({
      $or: [
        { requesterId: req.user.id, recipientId: conversationId },
        { requesterId: conversationId, recipientId: req.user.id },
      ],
      status: 'accepted',
    });
    if (!friendship && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'You can only view conversations with accepted friends' });
      return;
    }

    // N-034: block check — a blocked relationship from either direction closes the conversation view.
    const blockRecord = await FriendshipModel.findOne({
      status: 'blocked',
      $or: [
        { requesterId: req.user.id, recipientId: conversationId },
        { requesterId: conversationId, recipientId: req.user.id },
      ],
    }).select('_id');
    if (blockRecord && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'This conversation is unavailable' });
      return;
    }

    // Tier retention: Basic 4 weeks, Premium 6 weeks, Premium Plus 12 months.
    // History outside the window is no longer returned to either participant.
    const { cutoff, retention } = await retentionContext(req.user.id, conversationId);

    // A backfill cursor never reaches behind the retention floor for this thread.
    const historyFloor = since && since > cutoff ? since : cutoff;
    const messages = await MessageModel.find({
      $or: [
        { senderId: req.user.id, conversationId },
        { senderId: conversationId, conversationId: req.user.id },
      ],
      createdAt: { $gte: historyFloor },
    })
      .sort({ createdAt: since ? 1 : -1 })
      .limit(limit);
    const ordered = since ? messages : messages.reverse();

    const senders = await UserModel.find({ _id: { $in: [...new Set(ordered.map((message) => message.senderId))] } }).select('_id avatarUrl');
    const avatarByUserId = new Map(senders.map((sender) => [sender._id.toString(), sender.avatarUrl]));
    res.json({
      success: true,
      data: ordered.map((message) => ({ ...toMessageDTO(message), senderAvatarUrl: avatarByUserId.get(message.senderId) || message.senderAvatarUrl })),
      meta: {
        retentionDays: retention.dmRetentionDays,
        retentionLabel: retention.dmRetentionLabel,
        historyFrom: cutoff.toISOString(),
        since: since ? since.toISOString() : null,
        serverTime: new Date().toISOString(),
        count: ordered.length,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve direct conversation' });
  }
}

/** Messages are read only when the recipient explicitly opens this conversation. */
export async function markConversationRead(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) { res.status(401).json({ success: false, error: 'Authentication required' }); return; }
    const { conversationId } = req.params;
    const friendship = await FriendshipModel.findOne({
      $or: [
        { requesterId: req.user.id, recipientId: conversationId },
        { requesterId: conversationId, recipientId: req.user.id },
      ],
      status: 'accepted',
    });
    if (!friendship && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'You can only mark an accepted friend conversation as read' });
      return;
    }
    const { cutoff } = await retentionContext(req.user.id);
    const readAt = new Date();
    const result = await MessageModel.updateMany(
      {
        senderId: conversationId,
        conversationId: req.user.id,
        roomId: { $exists: false },
        readAt: { $exists: false },
        deletedAt: { $exists: false },
        createdAt: { $gte: cutoff },
      },
      { $set: { readAt } },
    );

    // Read receipts are only useful to the sender, so tell *them* that their
    // messages were opened. Without this the sender's ticks stayed single until
    // they happened to reload the thread.
    if (result.modifiedCount > 0) {
      notifyConversationRead(req.user.id, conversationId, readAt.toISOString(), result.modifiedCount);
    }

    res.json({
      success: true,
      data: { readCount: result.modifiedCount, readAt: readAt.toISOString() },
    });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to mark conversation read' });
  }
}

export async function editDirectMessage(req: Request, res: Response): Promise<void> {
  try {
    const message = await authorizedDirectMessage(req, req.params.messageId);
    if (!message) { res.status(404).json({ success: false, error: 'Message not found' }); return; }
    if (message.deletedAt) { res.status(409).json({ success: false, error: 'This message was unsent' }); return; }
    const sender = await UserModel.findById(req.user!.id).select('tier');
    const editsUsed = (message.editHistory || []).length;
    if (editsUsed >= messageEditLimitForTier(sender?.tier)) { res.status(403).json({ success: false, error: 'Your edit limit for this message has been reached' }); return; }
    const nextText = String(req.body.text || '').trim();
    if (nextText === message.text) { res.json({ success: true, data: toMessageDTO(message) }); return; }
    message.editHistory.push({ text: message.text, editedAt: new Date() });
    message.text = nextText;
    message.editedAt = new Date();
    await message.save();
    res.json({ success: true, data: toMessageDTO(message) });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to edit message' });
  }
}

export async function unsendDirectMessage(req: Request, res: Response): Promise<void> {
  try {
    const message = await authorizedDirectMessage(req, req.params.messageId);
    if (!message) { res.status(404).json({ success: false, error: 'Message not found' }); return; }
    if (Date.now() - message.createdAt.getTime() > UNSEND_WINDOW_MS) { res.status(403).json({ success: false, error: 'Messages can only be unsent during the first 2 minutes' }); return; }
    message.text = 'This message was unsent.';
    message.mediaUrl = undefined;
    message.mediaMimeType = undefined;
    message.isSpoiler = false;
    message.messageType = 'text';
    message.deletedAt = new Date();
    await message.save();
    res.json({ success: true, data: toMessageDTO(message) });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to unsend message' });
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

    const { roomId, conversationId, messageType, isSpoiler, mediaMimeType, idempotencyKey } = req.body;
    // text/mediaUrl are reassigned for room cards below, so they cannot be const.
    let { text, mediaUrl } = req.body;

    // A shared room card is an invitation link, not a file. It is validated against
    // rooms that actually exist whatever the destination is — a card is normally sent
    // in a DM, so hanging this check off the room branch meant DM cards skipped it.
    // It also skips the room media tracker, whose image-only rule would reject it.
    if (messageType === 'room_card') {
      const code = String(mediaUrl || '').replace(/^nebula:\/\/room\//i, '').trim().toUpperCase();
      const sharedRoom = code ? await RoomModel.findOne({ inviteCode: code }).select('_id name') : null;
      if (!sharedRoom) {
        res.status(400).json({ success: false, error: 'That room invite is no longer valid' });
        return;
      }
      mediaUrl = `nebula://room/${code}`;
      text = String(text || sharedRoom.name || 'Watch party').slice(0, 120);
    }

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
      if (messageType !== 'room_card' && (mediaUrl || messageType === 'media')) {
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

      // N-034: block enforcement — a blocked relationship from either direction
      // prevents DMs even when an accepted friendship exists.
      const blockRecord = await FriendshipModel.findOne({
        status: 'blocked',
        $or: [
          { requesterId: req.user.id, recipientId: conversationId },
          { requesterId: conversationId, recipientId: req.user.id },
        ],
      }).select('_id');
      if (blockRecord && req.user.role !== 'admin') {
        res.status(403).json({ success: false, error: 'You cannot send messages to this user' });
        return;
      }

      // N-003: idempotency dedup — if the client supplies a key and a message with
      // that exact key already exists in this conversation, return it as-is.
      if (idempotencyKey && typeof idempotencyKey === 'string' && idempotencyKey.length <= 128) {
        const existing = await MessageModel.findOne({
          senderId: req.user.id,
          conversationId,
          idempotencyKey,
        });
        if (existing) {
          res.status(201).json({ success: true, data: toMessageDTO(existing), deduplicated: true });
          return;
        }
      }
    }

    const message = await MessageModel.create({
      roomId,
      conversationId,
      senderId: req.user.id, // Server authoritative
      senderName: req.user.displayName,
      senderAvatarLetter: req.user.displayName.charAt(0).toUpperCase(),
      senderAvatarUrl: (await UserModel.findById(req.user.id).select('avatarUrl'))?.avatarUrl,
      text: text?.trim() || '',
      mediaUrl,
      isSpoiler: Boolean(isSpoiler),
      mediaMimeType,
      messageType: messageType || 'text',
      reactions: [],
      // N-003: only store when the client provided a non-empty key; never store null/undefined
      // so the sparse unique index excludes all messages without a key.
      ...(conversationId && !roomId && idempotencyKey && typeof idempotencyKey === 'string' && idempotencyKey.length <= 128
        ? { idempotencyKey: idempotencyKey.trim() }
        : {}),
    });

    if (conversationId && !roomId) {
      const notificationBody = text?.trim() || 'Sent you an attachment';
      try {
        await NotificationModel.create({
          userId: conversationId,
          type: 'direct_message',
          title: req.user.displayName,
          body: notificationBody,
          // The recipient opens the sender's conversation, not their own ID.
          actionData: { conversationId: req.user.id, messageId: message._id.toString(), fromUserId: req.user.id },
        });
      } catch (error) {
        console.warn('[DM notification] Could not persist notification:', error instanceof Error ? error.message : 'unknown error');
      }
      void notifyUser(conversationId, req.user.displayName, notificationBody, { type: 'dm', conversationId: req.user.id, messageId: message._id.toString() });
    }

    // Push the stored message to both sides' private channels. Polling stays as a
    // fallback, but the thread and the inbox now update as soon as the write lands.
    const delivered = toMessageDTO(message);
    if (conversationId && !roomId) {
      notifyDirectMessage(conversationId, delivered);
      // The sender's other signed-in devices need it too, not only this one.
      notifyDirectMessage(req.user.id, delivered);
    }

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

    const dataUrlMatch = url.match(/^data:([^;,]+);base64,([A-Za-z0-9+/]+={0,2})$/s);
    if (!dataUrlMatch) {
      res.status(400).json({ success: false, error: 'Media must be uploaded as a base64 data URL' });
      return;
    }
    if (dataUrlMatch[1].toLowerCase() !== resolvedMime) {
      res.status(400).json({ success: false, error: 'Media MIME type does not match the uploaded file' });
      return;
    }

    const maxSize = resolvedMime.startsWith('video/') ? 30 * 1024 * 1024 : 5 * 1024 * 1024;
    const declaredSize = Number(sizeBytes);
    const imageBuffer = Buffer.from(dataUrlMatch[2], 'base64');
    const actualSize = imageBuffer.byteLength;
    if ((Number.isFinite(declaredSize) && declaredSize > maxSize) || actualSize > maxSize) {
      res.status(413).json({
        success: false,
        error: `Media attachment exceeds maximum allowed size of ${maxSize / (1024 * 1024)}MB.`,
      });
      return;
    }

    if (!resolvedMime.startsWith('video/')) {
      const resolutionCheck = validateImageResolution(imageBuffer);
      if (!resolutionCheck.valid) {
        res.status(400).json({
          success: false,
          error: resolutionCheck.error || 'Image resolution exceeds maximum allowable dimensions (max 4096 x 4096 pixels)',
        });
        return;
      }
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

export async function triggerChatArchive(req: Request, res: Response): Promise<void> {
  try {
    const { runMonthlyChatRetentionArchive } = await import('../../services/chatArchiveService');
    const report = await runMonthlyChatRetentionArchive();
    res.json({ success: true, data: report });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message || 'Archival cycle failed' });
  }
}
