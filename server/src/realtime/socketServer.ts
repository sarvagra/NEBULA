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
  activeRoomId: string | null;
}

let roomIo: Server | null = null;
type LivePlayback = { positionMs: number; isPlaying: boolean; timestamp: number };
const livePlaybackByRoom = new Map<string, LivePlayback>();
const liveHostByRoom = new Map<string, string>();

function playbackPositionAtNow(state: LivePlayback, now = Date.now()): number {
  return Math.max(0, state.positionMs + (state.isPlaying ? Math.max(0, now - state.timestamp) : 0));
}

export function notifyRoomParticipantKicked(roomId: string, userId: string): void {
  if (!roomIo) return;
  const roomChannel = `room:${roomId}`;
  const userChannel = `user:${userId}`;
  roomIo.to(roomChannel).emit('participant:left', { userId });
  void roomIo.in(userChannel).fetchSockets().then((sockets) => {
    for (const socket of sockets) {
      socket.leave(roomChannel);
      socket.emit('room:removed', { roomId, reason: 'kicked' });
    }
  });
}

export function notifyRoomParticipantUpdated(roomId: string, participant: { userId: string; isMuted: boolean; isCameraOff: boolean }): void {
  roomIo?.to(`room:${roomId}`).emit('participant:updated', participant);
}

export function notifyUserSocialChanged(userIds: string[]): void {
  for (const userId of new Set(userIds)) {
    roomIo?.to(`user:${userId}`).emit('friendship:changed');
  }
}

export function notifyRoomHostChanged(roomId: string, newHostId: string): void {
  liveHostByRoom.set(roomId, newHostId);
  roomIo?.to(`room:${roomId}`).emit('room:host_changed', { newHostId });
}

export function notifyRoomParticipantLeft(roomId: string, userId: string): void {
  roomIo?.to(`room:${roomId}`).emit('participant:left', { userId });
}

export function notifyRoomDissolved(roomId: string): void {
  livePlaybackByRoom.delete(roomId);
  liveHostByRoom.delete(roomId);
  roomIo?.to(`room:${roomId}`).emit('room:removed', { roomId, reason: 'dissolved' });
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
  roomIo = io;
  livePlaybackByRoom.clear();
  liveHostByRoom.clear();
  const inactivityTimers = new Map<string, NodeJS.Timeout>();
  const inactivityGraceMs = 60_000;
  const mediaCheckpointAt = new Map<string, number>();
  const timerKey = (roomId: string, userId: string) => `${roomId}:${userId}`;
  const cancelInactivityRemoval = (roomId: string, userId: string) => {
    const key = timerKey(roomId, userId);
    const timer = inactivityTimers.get(key);
    if (timer) clearTimeout(timer);
    inactivityTimers.delete(key);
  };
  const scheduleInactivityRemoval = (roomId: string, user: AuthenticatedSocketUser) => {
    const key = timerKey(roomId, user.id);
    cancelInactivityRemoval(roomId, user.id);
    const timer = setTimeout(async () => {
      inactivityTimers.delete(key);
      try {
        const room = await RoomModel.findById(roomId);
        if (!room || !room.participants.some((participant) => participant.userId === user.id)) return;
        room.participants = room.participants.filter((participant) => participant.userId !== user.id);
        if (room.hostId === user.id && room.participants.length > 0) {
          room.hostId = room.participants[0].userId;
          room.kickedUserIds = [];
          room.participants.forEach((participant) => { participant.isHost = participant.userId === room.hostId; });
        }
        const dissolved = room.participants.length === 0;
        if (dissolved) {
          await RoomModel.deleteOne({ _id: roomId });
          mediaCheckpointAt.delete(roomId);
          livePlaybackByRoom.delete(roomId);
          liveHostByRoom.delete(roomId);
          io.to(`room:${roomId}`).emit('room:removed', { roomId, reason: 'dissolved' });
          for (const roomSocket of await io.in(`room:${roomId}`).fetchSockets()) roomSocket.leave(`room:${roomId}`);
        } else {
          await room.save();
          liveHostByRoom.set(roomId, room.hostId);
        }
        if (!dissolved && room.hostId !== user.id && room.participants.length > 0) {
          io.to(`room:${roomId}`).emit('room:host_changed', { newHostId: room.hostId });
        }
        await UserModel.updateOne({ _id: user.id, activeRoomId: roomId }, { $set: { activeRoomId: null, presenceStatus: 'online' } });
        io.to(`room:${roomId}`).emit('participant:left', { userId: user.id, displayName: user.displayName });
        for (const activeSocket of await io.in(`user:${user.id}`).fetchSockets()) {
          activeSocket.leave(`room:${roomId}`);
          activeSocket.emit('room:removed', { roomId, reason: 'inactive' });
        }
      } catch {
        // A failed cleanup is retried by the next authenticated room join or leave.
      }
    }, inactivityGraceMs);
    timer.unref();
    inactivityTimers.set(key, timer);
  };

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
        user = await UserModel.findById(decoded.id).select('_id username displayName tier isBanned activeRoomId');
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
        activeRoomId: user.activeRoomId?.toString() ?? null,
      };

      next();
    } catch (err: any) {
      return next(new Error('Authentication failed: Invalid or expired access token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const user = socket.data.user as AuthenticatedSocketUser;
    const joinedRooms = new Set<string>();
    void UserModel.findByIdAndUpdate(user.id, { presenceStatus: 'online' });
    console.log(`[Socket Authenticated] ${user.displayName} (@${user.username}) connected [${socket.id}]`);

    // Rate limiting tracking for this socket
    let messageCount = 0;
    const rateLimitReset = setInterval(() => {
      messageCount = 0;
    }, 5000);
    rateLimitReset.unref();

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

        if (user.activeRoomId !== room._id.toString()) {
          socket.emit('error', { message: 'Join this room through Nebula before opening its realtime connection' });
          return;
        }
        if (!room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'You are no longer a participant in this room' });
          return;
        }

        // A socket may subscribe only to its one current room. This keeps room
        // broadcasts isolated even if a client reuses a socket after navigation.
        for (const previousRoomId of joinedRooms) {
          if (previousRoomId !== room._id.toString()) {
            socket.leave(`room:${previousRoomId}`);
            joinedRooms.delete(previousRoomId);
          }
        }

        if (room.privacy === 'invite_only' && room.hostId !== user.id) {
          const isInvited = room.participants.some((p) => p.userId === user.id);
          if (!isInvited) {
            socket.emit('error', { message: 'Forbidden: This room is invite-only' });
            return;
          }
        }

        socket.join(`room:${room._id.toString()}`);
        joinedRooms.add(room._id.toString());
        liveHostByRoom.set(room._id.toString(), room.hostId);
        cancelInactivityRemoval(room._id.toString(), user.id);

        socket.to(`room:${room._id.toString()}`).emit('participant:joined', {
          userId: user.id, displayName: user.displayName,
          avatarLetter: user.displayName.charAt(0).toUpperCase(), timestamp: Date.now(),
        });
        const livePlayback = livePlaybackByRoom.get(room._id.toString());
        const currentPositionMs = livePlayback
          ? playbackPositionAtNow(livePlayback)
          : room.mediaState.playbackPosition * 1000;
        socket.emit('media:sync', {
          position: currentPositionMs / 1000,
          positionMs: currentPositionMs,
          isPlaying: livePlayback?.isPlaying ?? room.mediaState.isPlaying,
          mediaState: {
            title: room.mediaState.title,
            subtitle: room.mediaState.subtitle,
            mediaUrl: room.mediaState.mediaUrl,
            durationSeconds: room.mediaState.durationSeconds,
            playbackPosition: currentPositionMs / 1000,
            playbackPositionMs: currentPositionMs,
            isPlaying: livePlayback?.isPlaying ?? room.mediaState.isPlaying,
          },
          timestamp: Date.now(),
          senderId: room.hostId,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Failed to join room' });
      }
    });

    socket.on('room:background', (data: { roomId?: string }) => {
      const roomId = data?.roomId;
      if (roomId && joinedRooms.has(roomId)) scheduleInactivityRemoval(roomId, user);
    });

    socket.on('room:foreground', (data: { roomId?: string }) => {
      const roomId = data?.roomId;
      if (roomId && joinedRooms.has(roomId)) cancelInactivityRemoval(roomId, user.id);
    });

    socket.on('room:leave', async (data: { roomId: string }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (room) {
          room.participants = room.participants.filter((p) => p.userId !== user.id);
          if (room.participants.length === 0) {
            await RoomModel.deleteOne({ _id: room._id });
            mediaCheckpointAt.delete(data.roomId);
            livePlaybackByRoom.delete(data.roomId);
            liveHostByRoom.delete(data.roomId);
            io.to(`room:${data.roomId}`).emit('room:removed', { roomId: data.roomId, reason: 'dissolved' });
            for (const roomSocket of await io.in(`room:${data.roomId}`).fetchSockets()) roomSocket.leave(`room:${data.roomId}`);
          } else {
            if (room.hostId === user.id) {
              room.hostId = room.participants[0].userId;
              room.kickedUserIds = [];
              room.participants[0].isHost = true;
              liveHostByRoom.set(data.roomId, room.hostId);
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
        cancelInactivityRemoval(data.roomId, user.id);
        await UserModel.updateOne({ _id: user.id, activeRoomId: data.roomId }, { $set: { activeRoomId: null, presenceStatus: 'online' } });
        if (room?.participants.length === 0) {
          mediaCheckpointAt.delete(data.roomId);
          livePlaybackByRoom.delete(data.roomId);
          liveHostByRoom.delete(data.roomId);
        }
      } catch (err: any) {
        // Ignored on leave
      }
    });

    socket.on('media:switch', async (data: { roomId: string; title: string; subtitle?: string; mediaUrl: string; durationSeconds?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
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
        livePlaybackByRoom.set(data.roomId, { positionMs: 0, isPlaying: true, timestamp: Date.now() });

        io.to(`room:${data.roomId}`).emit('media:switch', {
          mediaState: {
            title: room.mediaState.title,
            subtitle: room.mediaState.subtitle,
            mediaUrl: room.mediaState.mediaUrl,
            durationSeconds: room.mediaState.durationSeconds,
            playbackPosition: room.mediaState.playbackPosition,
            isPlaying: room.mediaState.isPlaying,
            playbackPositionMs: room.mediaState.playbackPosition * 1000,
          },
          senderId: user.id,
          timestamp: Date.now(),
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Media switch failed' });
      }
    });

    // 2. SYNCHRONIZED MEDIA (STRICT HOST PERMISSION ONLY)
    socket.on('media:play', async (data: { roomId: string; positionMs?: number; position?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        // SERVER AUTHORIZATION CHECK
        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: true, timestamp });
        room.mediaState.isPlaying = true;
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:play', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:pause', async (data: { roomId: string; positionMs?: number; position?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: false, timestamp });
        room.mediaState.isPlaying = false;
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:pause', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:seek', async (data: { roomId: string; positionMs?: number; position?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        const prior = livePlaybackByRoom.get(data.roomId);
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: prior?.isPlaying ?? room.mediaState.isPlaying, timestamp });
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();

        socket.to(`room:${data.roomId}`).emit('media:seek', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback seek failed' });
      }
    });

    socket.on(
      'media:sync',
      async (data: { roomId: string; positionMs?: number; position?: number; isPlaying: boolean; timestamp: number }) => {
        try {
          if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;
          // room:join already verified membership against Mongo. Keep the active host
          // in memory so each high-frequency playhead sample avoids a database read.
          if (liveHostByRoom.get(data.roomId) !== user.id || !joinedRooms.has(data.roomId) || user.activeRoomId !== data.roomId) {
            socket.emit('error', { message: 'Forbidden: Only the room host can synchronize playback' });
            return;
          }

          const positionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
          const isPlaying = data.isPlaying === true;
          const now = Date.now();
          livePlaybackByRoom.set(data.roomId, { positionMs, isPlaying, timestamp: now });
          if (now - (mediaCheckpointAt.get(data.roomId) ?? 0) >= 5_000) {
            const room = await RoomModel.findById(data.roomId);
            if (!room || room.hostId !== user.id) return;
            room.mediaState.playbackPosition = positionMs / 1000;
            room.mediaState.isPlaying = isPlaying;
            await room.save();
            mediaCheckpointAt.set(data.roomId, now);
          }
          io.to(`room:${data.roomId}`).except(socket.id).emit('media:sync', {
            position: positionMs / 1000,
            positionMs,
            isPlaying,
            timestamp: now,
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
          await UserModel.findByIdAndUpdate(user.id, { presenceStatus: data.status });
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
      clearInterval(rateLimitReset);
      console.log(`[Socket Disconnected] ${user.displayName} (${socket.id})`);
      for (const roomId of joinedRooms) scheduleInactivityRemoval(roomId, user);
      setTimeout(async () => {
        const remainingSockets = await io.in(`user:${user.id}`).fetchSockets();
        if (remainingSockets.length === 0) {
          await UserModel.findByIdAndUpdate(user.id, { presenceStatus: 'offline' });
          io.emit('presence:updated', { userId: user.id, status: 'offline' });
        }
      }, 250);
    });
  });

  return io;
}
