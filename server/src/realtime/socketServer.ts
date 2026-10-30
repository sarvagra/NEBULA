import { Server as HttpServer } from 'http';
import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import { config } from '../config/env';
import { UserModel, SubscriptionTier } from '../models/User';
import { RoomModel } from '../models/Room';
import { MessageModel } from '../models/Message';
import { PollModel } from '../models/Poll';
import { RefreshTokenModel } from '../models/RefreshToken';
import { isValidObjectId } from '../middlewares/validation';

interface AuthenticatedSocketUser {
  id: string;
  username: string;
  displayName: string;
  tier: SubscriptionTier;
  activeRoomId: string | null;
  avatarUrl?: string;
  avatarLetter?: string;
  /** The signed-in session this socket belongs to; used to evict a logged-out device. */
  sessionId?: string;
}

let roomIo: Server | null = null;
type LivePlayback = { positionMs: number; isPlaying: boolean; timestamp: number; ended?: boolean };
const livePlaybackByRoom = new Map<string, LivePlayback>();
const liveHostByRoom = new Map<string, string>();

/**
 * Playback ordering. Every accepted host command increments the room's version and
 * every outbound playback event carries it. A command built against an older version
 * is a packet that crossed a newer command on the wire: applying it would move the
 * whole room backwards, so it is refused and the sender is corrected instead.
 */
const mediaVersionByRoom = new Map<string, number>();
/** Highest host sample clock seen per room, so a late-arriving sample cannot rewind it. */
const liveSampleClockByRoom = new Map<string, number>();

export function playbackVersionForRoom(roomId: string): number {
  return mediaVersionByRoom.get(roomId) ?? 0;
}

function bumpPlaybackVersion(roomId: string): number {
  const next = playbackVersionForRoom(roomId) + 1;
  mediaVersionByRoom.set(roomId, next);
  return next;
}

function isStaleCommand(roomId: string, clientVersion: unknown): boolean {
  // Clients that do not send a version yet keep working; they simply cannot be
  // protected against reordering until they echo the version back (the Android
  // client does).
  if (typeof clientVersion !== 'number' || !Number.isFinite(clientVersion)) return false;
  return clientVersion < playbackVersionForRoom(roomId);
}

function forbiddenPlayback(socket: Socket, roomId: string): void {
  const state = livePlaybackByRoom.get(roomId);
  socket.emit('media:state', {
    roomId,
    position: (state ? playbackPositionAtNow(state) : 0) / 1000,
    positionMs: state ? playbackPositionAtNow(state) : 0,
    isPlaying: state?.isPlaying ?? false,
    ended: state?.ended ?? false,
    version: playbackVersionForRoom(roomId),
    timestamp: Date.now(),
    senderId: 'server',
    rejected: true,
  });
}

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

export function notifyRoomsParticipantProfileUpdated(roomIds: string[], userId: string): void {
  for (const roomId of roomIds) roomIo?.to(`room:${roomId}`).emit('participant:updated', { userId, profileUpdated: true });
}

export function notifyRoomMediaSwitch(roomId: string, mediaState: { title: string; subtitle: string; mediaUrl: string; durationSeconds: number; playbackPosition: number; isPlaying: boolean }): void {
  if (!roomIo) return;
  const timestamp = Date.now();
  const version = bumpPlaybackVersion(roomId);
  liveSampleClockByRoom.delete(roomId);
  livePlaybackByRoom.set(roomId, { positionMs: 0, isPlaying: true, timestamp, ended: false });
  roomIo.to(`room:${roomId}`).emit('media:switch', {
    mediaState: { ...mediaState, playbackPosition: 0, playbackPositionMs: 0, isPlaying: true },
    senderId: 'server', timestamp, ended: false, version,
  });
}

/**
 * Tells the original sender that the recipient opened their thread, so the
 * message ticks can move from delivered to read without a manual reload.
 */
export function notifyConversationRead(
  readerId: string,
  senderId: string,
  readAt: string,
  readCount: number,
): void {
  roomIo?.to(`user:${senderId}`).emit('chat:read', {
    conversationId: readerId,
    readerId,
    readAt,
    readCount,
  });
}

/**
 * Delivers a direct message to a user's private channel.
 *
 * Messages posted over HTTP previously reached the recipient only through the
 * client's polling loop, so a DM could sit unseen for seconds (or until the user
 * switched tabs). Every authenticated socket joins `user:<id>` on connect, so this
 * reaches the recipient and the sender's other devices at once.
 */
/** True when this user has at least one authenticated socket open right now. */
export async function disconnectSessionSockets(
  userId: string,
  sessionId?: string,
  keepSessionId?: string
): Promise<void> {
  if (!roomIo) return;
  try {
    const sockets = await roomIo.in(`user:${userId}`).fetchSockets();
    for (const target of sockets) {
      const socketUser = target.data.user as AuthenticatedSocketUser | undefined;
      if (keepSessionId && socketUser?.sessionId === keepSessionId) continue;
      // A missing session id means the socket predates per-session tracking; it still
      // belongs to the account being signed out, so it goes too.
      if (!sessionId || !socketUser?.sessionId || socketUser.sessionId === sessionId) {
        target.emit('session:revoked', { message: 'This account was signed out from another device' });
        target.disconnect(true);
      }
    }
  } catch {
    // Best effort: the HTTP layer already refuses the stored token.
  }
}

export async function isUserOnline(userId: string): Promise<boolean> {
  if (!roomIo || !userId) return false;
  try {
    return (await roomIo.in(`user:${userId}`).fetchSockets()).length > 0;
  } catch {
    return false;
  }
}

export function notifyDirectMessage(userId: string, payload: unknown): void {
  roomIo?.to(`user:${userId}`).emit('chat:message', payload);
}

export function notifyUserSocialChanged(userIds: string[]): void {
  for (const userId of new Set(userIds)) {
    roomIo?.to(`user:${userId}`).emit('friendship:changed');
  }
}

/** Tells everyone in the room that the queue changed, wherever the change came from. */
export function notifyRoomQueueChanged(roomId: string): void {
  roomIo?.to(`room:${roomId}`).emit('queue:updated', { roomId });
}

export function notifyRoomHostChanged(roomId: string, newHostId: string): void {
  liveHostByRoom.set(roomId, newHostId);
  roomIo?.to(`room:${roomId}`).emit('room:host_changed', { newHostId });
}

export function notifyRoomParticipantLeft(roomId: string, userId: string): void {
  roomIo?.to(`room:${roomId}`).emit('participant:left', { userId });
}

/**
 * Presence is reclaimed only when a client stops proving it is alive. A killed
 * process never runs its disconnect handler, so without this sweep a crashed app
 * keeps advertising "Active" indefinitely to everyone who lists it.
 */
export const PRESENCE_STALE_MS = 120_000;

/** Users whose app currently holds an open socket; they are never reclaimed. */
const connectedSocketCounts = new Map<string, number>();

export async function sweepStalePresence(): Promise<string[]> {
  if (mongoose.connection.readyState !== 1) return [];
  const cutoff = new Date(Date.now() - PRESENCE_STALE_MS);
  const stale = await UserModel.find({
    presenceStatus: { $in: ['online', 'in_a_room', 'private_chat'] },
    $or: [{ lastActiveAt: { $lt: cutoff } }, { lastActiveAt: { $exists: false } }],
  }).select('_id').limit(500);
  const reclaimed = stale.map((user) => user._id.toString()).filter((id) => !connectedSocketCounts.get(id));
  if (reclaimed.length === 0) return [];
  await UserModel.updateMany({ _id: { $in: reclaimed } }, { $set: { presenceStatus: 'offline' } });
  for (const userId of reclaimed) {
    roomIo?.emit('presence:updated', { userId, status: 'offline' });
  }
  return reclaimed;
}

export function notifyRoomDissolved(roomId: string): void {
  livePlaybackByRoom.delete(roomId);
  liveHostByRoom.delete(roomId);
  mediaVersionByRoom.delete(roomId);
  liveSampleClockByRoom.delete(roomId);
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
    // A deploy or an instance restart drops every socket at once. Without recovery
    // each of those clients re-syncs by polling; with it, Socket.IO replays the
    // events they missed during the short reconnect window.
    connectionStateRecovery: {
      maxDisconnectionDuration: 2 * 60 * 1000,
      skipMiddlewares: true,
    },
  });
  roomIo = io;
  livePlaybackByRoom.clear();
  liveHostByRoom.clear();
  mediaVersionByRoom.clear();
  liveSampleClockByRoom.clear();
  const inactivityTimers = new Map<string, NodeJS.Timeout>();
  // Overridable so a test can observe host hand-off without waiting a full minute;
  // production keeps the 60-second grace unless the environment says otherwise.
  const configuredGrace = Number(process.env.NEBULA_INACTIVITY_GRACE_MS);
  const inactivityGraceMs = Number.isFinite(configuredGrace) && configuredGrace > 0 ? configuredGrace : 60_000;
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
          mediaVersionByRoom.delete(roomId);
          liveSampleClockByRoom.delete(roomId);
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
      const decoded = jwt.verify(rawToken, config.auth.jwtSecret) as { id: string; sessionId?: string };
      if (!decoded || !decoded.id) {
        return next(new Error('Authentication failed: Malformed token payload'));
      }

      let user: any = null;
      if (mongoose.connection.readyState === 1) {
        // A socket is a long-lived credential. Without this check a device that was
        // signed out kept receiving room and DM traffic until its token expired.
        if (decoded.sessionId) {
          const activeSession = await RefreshTokenModel.findOne({
            _id: decoded.sessionId,
            revoked: false,
            expiresAt: { $gt: new Date() },
          }).select('_id');
          if (!activeSession) {
            return next(new Error('Your session has been logged out or revoked'));
          }
        }
        user = await UserModel.findById(decoded.id).select('_id username displayName avatarUrl avatarLetter tier isBanned activeRoomId activeRoomSessionId');
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
        avatarUrl: user.avatarUrl,
        avatarLetter: user.avatarLetter,
        tier: user.tier,
        activeRoomId: user.activeRoomId?.toString() ?? null,
        sessionId: decoded.sessionId,
      };

      next();
    } catch (err: any) {
      return next(new Error('Authentication failed: Invalid or expired access token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const user = socket.data.user as AuthenticatedSocketUser;
    const joinedRooms = new Set<string>();
    connectedSocketCounts.set(user.id, (connectedSocketCounts.get(user.id) ?? 0) + 1);
    // lastActiveAt is the presence TTL anchor: the sweep only reclaims presence for
    // users that stopped proving they are alive, so it must be refreshed here too.
    void UserModel.findByIdAndUpdate(user.id, { presenceStatus: 'online', lastActiveAt: new Date() });
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

        // The HTTP join already refused a second device; the socket is the other way
        // into the same live session, so it has to refuse it too.
        const slot = await UserModel.findById(user.id).select('activeRoomSessionId');
        const holderSession = (slot?.activeRoomSessionId || '').trim();
        if (holderSession && user.sessionId && holderSession !== user.sessionId) {
          const holderStillActive = await RefreshTokenModel.findOne({
            _id: holderSession,
            revoked: false,
            expiresAt: { $gt: new Date() },
          }).select('_id');
          if (holderStillActive) {
            socket.emit('error', {
              message: 'Only one device per account can be in a room. Leave it on your other device first.',
              code: 'ROOM_ON_OTHER_DEVICE',
            });
            return;
          }
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
          avatarLetter: user.avatarLetter || user.displayName.charAt(0).toUpperCase(), avatarUrl: user.avatarUrl, timestamp: Date.now(),
        });
        const livePlayback = livePlaybackByRoom.get(room._id.toString());
        const currentPositionMs = livePlayback
          ? playbackPositionAtNow(livePlayback)
          : room.mediaState.playbackPosition * 1000;
        socket.emit('media:sync', {
          position: currentPositionMs / 1000,
          positionMs: currentPositionMs,
          isPlaying: livePlayback?.isPlaying ?? room.mediaState.isPlaying,
          ended: livePlayback?.ended ?? false,
          version: playbackVersionForRoom(room._id.toString()),
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
            mediaVersionByRoom.delete(data.roomId);
            liveSampleClockByRoom.delete(data.roomId);
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
          mediaVersionByRoom.delete(data.roomId);
          liveSampleClockByRoom.delete(data.roomId);
        }
      } catch (err: any) {
        // Ignored on leave
      }
    });

    socket.on('media:switch', async (data: { roomId: string; title: string; subtitle?: string; mediaUrl: string; durationSeconds?: number; version?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can switch media' });
          return;
        }
        if (isStaleCommand(data.roomId, data.version)) {
          forbiddenPlayback(socket, data.roomId);
          return;
        }

        room.mediaState.title = (data.title || 'Untitled').slice(0, 150);
        room.mediaState.subtitle = (data.subtitle || '').slice(0, 150);
        room.mediaState.mediaUrl = (data.mediaUrl || '').slice(0, 500);
        room.mediaState.durationSeconds = Math.max(0, Number(data.durationSeconds) || 0);
        room.mediaState.playbackPosition = 0;
        room.mediaState.isPlaying = true;
        await room.save();
        const version = bumpPlaybackVersion(data.roomId);
        liveSampleClockByRoom.delete(data.roomId);
        livePlaybackByRoom.set(data.roomId, { positionMs: 0, isPlaying: true, timestamp: Date.now(), ended: false });
        socket.emit('media:ack', { roomId: data.roomId, version });

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
          ended: false,
          version,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Media switch failed' });
      }
    });

    socket.on('media:end', async (data: { roomId: string; positionMs?: number; version?: number }) => {
      try {
        const roomId = data?.roomId;
        if (!roomId || !isValidObjectId(roomId) || liveHostByRoom.get(roomId) !== user.id || !joinedRooms.has(roomId)) return;
        if (isStaleCommand(roomId, data.version)) {
          forbiddenPlayback(socket, roomId);
          return;
        }
        const room = await RoomModel.findById(roomId);
        if (!room || room.hostId !== user.id) return;
        const positionMs = Math.max(0, Number(data.positionMs) || 0);
        const timestamp = Date.now();
        const version = bumpPlaybackVersion(roomId);
        livePlaybackByRoom.set(roomId, { positionMs, isPlaying: false, timestamp, ended: true });
        room.mediaState.playbackPosition = positionMs / 1000;
        room.mediaState.isPlaying = false;
        await room.save();
        socket.emit('media:ack', { roomId, version });
        io.to(`room:${roomId}`).emit('media:ended', { roomId, positionMs, isPlaying: false, ended: true, timestamp, senderId: user.id, version });
      } catch {
        socket.emit('error', { message: 'Could not synchronize video end state' });
      }
    });

    socket.on('media:restart', async (data: { roomId: string; version?: number }) => {
      try {
        const roomId = data?.roomId;
        if (!roomId || !isValidObjectId(roomId) || liveHostByRoom.get(roomId) !== user.id || !joinedRooms.has(roomId)) return;
        if (isStaleCommand(roomId, data.version)) {
          forbiddenPlayback(socket, roomId);
          return;
        }
        const room = await RoomModel.findById(roomId);
        if (!room || room.hostId !== user.id) return;
        const timestamp = Date.now();
        const version = bumpPlaybackVersion(roomId);
        livePlaybackByRoom.set(roomId, { positionMs: 0, isPlaying: true, timestamp, ended: false });
        room.mediaState.playbackPosition = 0;
        room.mediaState.isPlaying = true;
        await room.save();
        socket.emit('media:ack', { roomId, version });
        io.to(`room:${roomId}`).emit('media:restart', { roomId, positionMs: 0, isPlaying: true, ended: false, timestamp, senderId: user.id, version });
      } catch {
        socket.emit('error', { message: 'Could not restart video for this room' });
      }
    });

    // 2. SYNCHRONIZED MEDIA (STRICT HOST PERMISSION ONLY)
    socket.on('media:play', async (data: { roomId: string; positionMs?: number; position?: number; version?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        // SERVER AUTHORIZATION CHECK
        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }
        if (isStaleCommand(data.roomId, data.version)) {
          forbiddenPlayback(socket, data.roomId);
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        const version = bumpPlaybackVersion(data.roomId);
        liveSampleClockByRoom.delete(data.roomId);
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: true, timestamp, ended: false });
        room.mediaState.isPlaying = true;
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();
        socket.emit('media:ack', { roomId: data.roomId, version });

        socket.to(`room:${data.roomId}`).emit('media:play', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
          version,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:pause', async (data: { roomId: string; positionMs?: number; position?: number; version?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }
        if (isStaleCommand(data.roomId, data.version)) {
          forbiddenPlayback(socket, data.roomId);
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        const version = bumpPlaybackVersion(data.roomId);
        liveSampleClockByRoom.delete(data.roomId);
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: false, timestamp, ended: false });
        room.mediaState.isPlaying = false;
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();
        socket.emit('media:ack', { roomId: data.roomId, version });

        socket.to(`room:${data.roomId}`).emit('media:pause', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
          version,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback synchronization failed' });
      }
    });

    socket.on('media:seek', async (data: { roomId: string; positionMs?: number; position?: number; version?: number }) => {
      try {
        if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;

        const room = await RoomModel.findById(data.roomId);
        if (!room) return;

        if (room.hostId !== user.id || !joinedRooms.has(data.roomId) || !room.participants.some((participant) => participant.userId === user.id)) {
          socket.emit('error', { message: 'Forbidden: Only the room host can control synchronized playback' });
          return;
        }
        if (isStaleCommand(data.roomId, data.version)) {
          forbiddenPlayback(socket, data.roomId);
          return;
        }

        const safePositionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
        const timestamp = Date.now();
        const prior = livePlaybackByRoom.get(data.roomId);
        const version = bumpPlaybackVersion(data.roomId);
        liveSampleClockByRoom.delete(data.roomId);
        livePlaybackByRoom.set(data.roomId, { positionMs: safePositionMs, isPlaying: prior?.isPlaying ?? room.mediaState.isPlaying, timestamp, ended: safePositionMs === 0 ? false : prior?.ended });
        room.mediaState.playbackPosition = safePositionMs / 1000;
        await room.save();
        socket.emit('media:ack', { roomId: data.roomId, version });

        socket.to(`room:${data.roomId}`).emit('media:seek', {
          position: safePositionMs / 1000,
          positionMs: safePositionMs,
          senderId: user.id,
          timestamp,
          version,
        });
      } catch (err: any) {
        socket.emit('error', { message: 'Playback seek failed' });
      }
    });

    socket.on(
      'media:sync',
      async (data: { roomId: string; positionMs?: number; position?: number; isPlaying: boolean; timestamp: number; version?: number }) => {
        try {
          if (!data || !data.roomId || !isValidObjectId(data.roomId)) return;
          // room:join already verified membership against Mongo. Keep the active host
          // in memory so each high-frequency playhead sample avoids a database read.
          if (liveHostByRoom.get(data.roomId) !== user.id || !joinedRooms.has(data.roomId) || user.activeRoomId !== data.roomId) {
            socket.emit('error', { message: 'Forbidden: Only the room host can synchronize playback' });
            return;
          }
          // A sample produced before the latest play/pause/seek/switch belongs to the
          // previous playback generation; applying it would drag every listener back.
          if (isStaleCommand(data.roomId, data.version)) {
            forbiddenPlayback(socket, data.roomId);
            return;
          }
          // Samples can also arrive out of order while carrying the same version.
          // The host clock is monotonic, so anything older than the newest accepted
          // sample is a duplicate of an older frame and is dropped silently.
          const sampleClock = Number(data.timestamp);
          const lastSampleClock = liveSampleClockByRoom.get(data.roomId);
          if (Number.isFinite(sampleClock) && lastSampleClock !== undefined && sampleClock < lastSampleClock) return;

          const positionMs = Math.max(0, Number(data.positionMs ?? ((data.position ?? 0) * 1000)) || 0);
          const isPlaying = data.isPlaying === true;
          const now = Date.now();
          if (Number.isFinite(sampleClock)) liveSampleClockByRoom.set(data.roomId, sampleClock);
          const ended = livePlaybackByRoom.get(data.roomId)?.ended ?? false;
          livePlaybackByRoom.set(data.roomId, { positionMs, isPlaying: ended ? false : isPlaying, timestamp: now, ended });
          if (now - (mediaCheckpointAt.get(data.roomId) ?? 0) >= 5_000) {
            const room = await RoomModel.findById(data.roomId);
            if (!room || room.hostId !== user.id) return;
            room.mediaState.playbackPosition = positionMs / 1000;
            room.mediaState.isPlaying = ended ? false : isPlaying;
            await room.save();
            mediaCheckpointAt.set(data.roomId, now);
          }
          io.to(`room:${data.roomId}`).except(socket.id).emit('media:sync', {
            position: positionMs / 1000,
            positionMs,
            isPlaying: ended ? false : isPlaying,
            ended,
            timestamp: now,
            senderId: user.id,
            version: playbackVersionForRoom(data.roomId),
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
          senderAvatarUrl: (await UserModel.findById(user.id).select('avatarUrl'))?.avatarUrl,
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
          await UserModel.findByIdAndUpdate(user.id, { presenceStatus: data.status, lastActiveAt: new Date() });
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
      const remainingCount = (connectedSocketCounts.get(user.id) ?? 1) - 1;
      if (remainingCount > 0) connectedSocketCounts.set(user.id, remainingCount);
      else connectedSocketCounts.delete(user.id);
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

  const presenceSweep = setInterval(() => {
    void sweepStalePresence().catch(() => {
      // The next sweep retries; a failed pass must not take the socket server down.
    });
  }, 60_000);
  presenceSweep.unref();

  return io;
}
