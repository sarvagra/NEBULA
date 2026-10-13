import { Server as HttpServer } from 'http';
import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import { config } from '../config/env';
import { UserModel, SubscriptionTier } from '../models/User';
import { RoomModel } from '../models/Room';
import { MessageModel } from '../models/Message';
import { PollModel } from '../models/Poll';
import { isValidObjectId } from '../middlewares/validation';

interface AuthenticatedSocketUser {
  id: string;
  username: string;
  displayName: string;
  tier: SubscriptionTier;
}

export function initSocketServer(httpServer: HttpServer): Server {
  const io = new Server(httpServer, {
    cors: {
      origin: config.server.isProduction ? config.server.corsAllowedOrigins : '*',
      methods: ['GET', 'POST'],
    },
    maxHttpBufferSize: 8e6, // bounded room media/chat payload limit
    pingTimeout: 20000,
    pingInterval: 25000,
  });

  // STRICT AUTHENTICATION: Zero anonymous or guest fallback
  io.use(async (socket: Socket, next) => {
    const rawToken = socket.handshake.auth?.token || socket.handshake.headers?.authorization?.replace('Bearer ', '');

    if (!rawToken || typeof rawToken !== 'string' || rawToken.trim() === '') {
      return next(new Error('Authentication required: Missing or empty access token'));
    }

    try {
      const decoded = jwt.verify(rawToken, config.auth.jwtSecret) as { id: string };
      if (!decoded || !decoded.id) {
        return next(new Error('Authentication failed: Malformed token payload'));
      }

      let user: any = null;
      if (mongoose.connection.readyState === 1) {
        user = await UserModel.findById(decoded.id).select('_id username displayName tier isBanned');
        if (!user) {
          return next(new Error('Authentication failed: User no longer exists'));
        }
        if (user.isBanned) {
          return next(new Error('Account suspended'));
        }
      } else {
        return next(new Error('Authentication service temporarily unavailable'));
      }

      // Attach authoritative identity to socket data
      socket.data.user = {
        id: user._id.toString(),
        username: user.username,
        displayName: user.displayName,
        tier: user.tier,
      };

      next();
    } catch (err: any) {
      return next(new Error('Authentication failed: Invalid or expired access token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const user = socket.data.user as AuthenticatedSocketUser;
    const joinedRooms = new Set<string>();
    console.log(`[Socket Authenticated] ${user.displayName} (@${user.username}) connected [${socket.id}]`);

    // Rate limiting tracking for this socket
    let messageCount = 0;
    setInterval(() => {
      messageCount = 0;
    }, 5000).unref();

    // Join user's private channel for direct alerts/messages
    socket.join(`user:${user.id}`);

    // 1. ROOM JOIN WITH MEMBERSHIP VERIFICATION
    socket.on('room:join', async (data: { roomId: string }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) {
          socket.emit('error', { message: 'Invalid room identifier format' });
          return;
        }

        const room = await RoomModel.findById(data.roomId);
        if (!room) {
          socket.emit('error', { message: 'Room not found' });
          return;
        }

        if (room.privacy === 'invite_only' && room.hostId !== user.id) {
          const isInvited = room.participants.some((p) => p.userId === user.id);
          if (!isInvited) {
            socket.emit('error', { message: 'Forbidden: This room is invite-only' });
            return;
          }
        }

        // Add to participants if not present
        if (!room.participants.some((p) => p.userId === user.id)) {
          if (room.participants.length >= room.maxParticipants) {
            socket.emit('error', { message: 'Room has reached maximum capacity' });
            return;
          }
          room.participants.push({
            userId: user.id,
            displayName: user.displayName,
            avatarLetter: user.displayName.charAt(0).toUpperCase(),
            joinedAt: new Date(),
            isHost: room.hostId === user.id,
          });
          await room.save();
        }

        socket.join(`room:${room._id.toString()}`);
        joinedRooms.add(room._id.toString());

        io.to(`room:${room._id.toString()}`).emit('participant:joined', {
          userId: user.id,
          displayName: user.displayName,
          avatarLetter: user.displayName.charAt(0).toUpperCase(),
          timestamp: Date.now(),
        });
        socket.emit('media:sync', {
          position: room.mediaState.playbackPosition,
          isPlaying: room.mediaState.isPlaying,
          timestamp: Date.now(),
          senderId: room.hostId,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Failed to join room' });
      }
    });

    socket.on('room:leave', async (data: { roomId: string }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (room) {
          room.participants = room.participants.filter((p) => p.userId !== user.id);
          if (room.participants.length === 0) {
            // Rooms are durable. An empty room is inactive, not destroyed, so
            // members can return without losing the room and its media state.
            await room.save();
          } else {
            if (room.hostId === user.id) {
              room.hostId = room.participants[0].userId;
              room.participants[0].isHost = true;
              io.to(`room:${data.roomId}`).emit('room:host_changed', { newHostId: room.hostId });
            }
            await room.save();
          }
        }

        io.to(`room:${data.roomId}`).emit('participant:left', {
          userId: user.id,
          displayName: user.displayName,
        });

        socket.leave(`room:${data.roomId}`);
        joinedRooms.delete(data.roomId);
      } catch (err: any) {
        // Ignored on leave
      }
    });

    socket.on('media:switch', async (data: { roomId: string; title: string; subtitle?: string; mediaUrl: string; durationSeconds?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id) {
          socket.emit('error', { message: 'Forbidden: Only the room host can switch media' });
          return;
        }

        room.mediaState.title = (data.title || 'Untitled').slice(0, 150);
        room.mediaState.subtitle = (data.subtitle || '').slice(0, 150);
        room.mediaState.mediaUrl = (data.mediaUrl || '').slice(0, 500);
        room.mediaState.durationSeconds = Math.max(0, Number(data.durationSeconds) || 0);
        room.mediaState.playbackPosition = 0;
        room.mediaState.isPlaying = true;
        await room.save();

        io.to(`room:${data.roomId}`).emit('media:switch', {
          mediaState: room.mediaState,
          senderId: user.id,
          timestamp: Date.now(),
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Media switch failed' });
      }
    });

    // 2. SYNCHRONIZED MEDIA (STRICT HOST PERMISSION ONLY)
    socket.on('media:play', async (data: { roomId: string; position: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        // SERVER AUTHORIZATION CHECK
        if (room.hostId !== user.id) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePosition = Math.max(0, Number(data.position) || 0);
        room.mediaState.isPlaying = true;
        room.mediaState.playbackPosition = safePosition;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:play', {
          position: safePosition,
          senderId: user.id,
          timestamp: Date.now(),
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:pause', async (data: { roomId: string; position: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePosition = Math.max(0, Number(data.position) || 0);
        room.mediaState.isPlaying = false;
        room.mediaState.playbackPosition = safePosition;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:pause', {
          position: safePosition,
          senderId: user.id,
          timestamp: Date.now(),
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:seek', async (data: { roomId: string; position: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePosition = Math.max(0, Number(data.position) || 0);
        room.mediaState.playbackPosition = safePosition;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:seek', {
          position: safePosition,
          senderId: user.id,
          timestamp: Date.now(),
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback seek failed' });
      }
    });

    socket.on(
      'media:sync',
      async (data: { roomId: string; position: number; isPlaying: boolean; timestamp: number }) => {
        try {
          if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;
          const room = await RoomModel.findById(data.roomId);
          if (!room || room.hostId !== user.id) {
            socket.emit('error', { message: 'Forbidden: Only the room host can synchronize playback' });
            return;
          }

          const position = Math.max(0, Number(data.position) || 0);
          const isPlaying = data.isPlaying === true;
          room.mediaState.playbackPosition = position;
          room.mediaState.isPlaying = isPlaying;
          await room.save();
          io.to(`room:${data.roomId}`).emit('media:sync', {
            position,
            isPlaying,
            timestamp: Date.now(),
            senderId: user.id,
          });
        } catch {
          socket.emit('error', { message: 'Playback synchronization failed' });
        }
      }
    );

    // 3. REALTIME CHAT (MEMBERSHIP & RATE LIMITING)
    socket.on('chat:message', async (data: { roomId?: string; conversationId?: string; text: string; mediaUrl?: string }) => {
      try {
        messageCount += 1;
        if (messageCount > 10) {
          socket.emit('error', { message: 'Rate limit exceeded: You are sending messages too quickly' });
          return;
        }

        if (!data || (!data.text && !data.mediaUrl)) {
          socket.emit('error', { message: 'Message payload must include text or media' });
          return;
        }

        const safeText = (data.text || '').slice(0, 7000000).trim();

        // If roomId is present, verify membership
        if (data.roomId) {
          if (!isValidObjectId(data.roomId)) {
            socket.emit('error', { message: 'Invalid room ID' });
            return;
          }

          const room = await RoomModel.findById(data.roomId);
          if (!room || !room.participants.some((p) => p.userId === user.id)) {
            socket.emit('error', { message: 'Forbidden: You must join the room before sending messages' });
            return;
          }
        }

        const message = await MessageModel.create({
          roomId: data.roomId,
          conversationId: data.conversationId,
          senderId: user.id, // Strictly server-authoritative
          senderName: user.displayName,
          senderAvatarLetter: user.displayName.charAt(0).toUpperCase(),
          text: safeText,
          mediaUrl: data.mediaUrl?.slice(0, 7000000),
          messageType: data.mediaUrl ? 'media' : 'text',
        });

        const json = message.toJSON();

        if (data.roomId) {
          io.to(`room:${data.roomId}`).emit('chat:message', json);
        } else if (data.conversationId) {
          io.to(`user:${data.conversationId}`).emit('chat:message', json);
          socket.emit('chat:message', json);
        }
      } catch (err: any) {
        socket.emit('error', { message: 'Failed to process message' });
      }
    });

    // 4. CHAT REACTIONS
    socket.on('chat:reaction', async (data: { roomId: string; emoji: string; messageId?: string }) => {
      try {
        if (!data || !data.roomId || !data.emoji || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room || !room.participants.some((p) => p.userId === user.id)) return;

        const safeEmoji = data.emoji.slice(0, 8);
        io.to(`room:${data.roomId}`).emit('chat:reaction', {
          emoji: safeEmoji,
          userId: user.id,
          displayName: user.displayName,
          messageId: data.messageId,
        });
      } catch {
        // Ignored
      }
    });

    // 5. POLL VOTING
    socket.on('poll:vote', async (data: { roomId: string; pollId: string; optionId: string }) => {
      try {
        if (!data || !data.pollId || !data.optionId || !isValidObjectId(data.pollId)) return;

        const poll = await PollModel.findById(data.pollId);
        if (!poll || !poll.isActive) return;

        const room = await RoomModel.findById(poll.roomId);
        if (!room || !room.participants.some((p) => p.userId === user.id)) return;

        const option = poll.options.find((o) => o.id === data.optionId);
        if (option && !option.voterUserIds.includes(user.id)) {
          option.votes += 1;
          option.voterUserIds.push(user.id);
          poll.totalVotes += 1;
          await poll.save();

          io.to(`room:${poll.roomId}`).emit('poll:updated', poll.toJSON());
        }
      } catch {
        // Ignored
      }
    });

    // 6. USER PRESENCE BROADCAST
    socket.on('presence:status', async (data: { status: 'online' | 'in_a_room' | 'private_chat' | 'offline' }) => {
      try {
        const validStatuses = ['online', 'in_a_room', 'private_chat', 'offline'];
        if (validStatuses.includes(data.status)) {
          await UserModel.findByIdAndUpdate(user.id, { status: data.status });
          socket.broadcast.emit('presence:updated', {
            userId: user.id,
            status: data.status,
          });
        }
      } catch {
        // Ignored
      }
    });

    socket.on('disconnect', async () => {
      console.log(`[Socket Disconnected] ${user.displayName} (${socket.id})`);
      for (const roomId of joinedRooms) {
        try {
          const room = await RoomModel.findById(roomId);
          if (!room) continue;
          room.participants = room.participants.filter((p) => p.userId !== user.id);
          if (room.hostId === user.id && room.participants.length > 0) {
            room.hostId = room.participants[0].userId;
            room.participants[0].isHost = true;
          }
          await room.save();
          io.to(`room:${roomId}`).emit('participant:left', {
            userId: user.id,
            displayName: user.displayName,
          });
        } catch {
          // Disconnect cleanup is best-effort; room data remains durable.
        }
      }
    });
  });

  return io;
}
