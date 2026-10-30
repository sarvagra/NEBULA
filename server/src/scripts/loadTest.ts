/**
 * Socket scale harness.
 *
 * Simulates N authenticated clients spread over R rooms against a running
 * Nebula server: each socket connects, joins its room, and one client per room
 * sends chat messages while every other client measures how long the broadcast
 * takes to arrive.
 *
 *   NEBULA_LOAD_USERS=1000 NEBULA_LOAD_ROOMS=25 npm run load:test
 *
 * It writes to an isolated database (default `nebula_loadtest`) so it can never
 * touch real users, and drops that database's collections when it starts. Point
 * it at a staging or local server, never at production.
 */
import path from 'path';
import dotenv from 'dotenv';
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

import mongoose from 'mongoose';
import jwt from 'jsonwebtoken';
import { io, Socket } from 'socket.io-client';

const USERS = Number(process.env.NEBULA_LOAD_USERS) || 100;
const ROOMS = Number(process.env.NEBULA_LOAD_ROOMS) || 10;
const BASE_URL = process.env.NEBULA_LOAD_URL || 'http://localhost:5001';
const DB_NAME = process.env.NEBULA_LOAD_DB || 'nebula_loadtest';
const CHAT_INTERVAL_MS = Number(process.env.NEBULA_LOAD_CHAT_MS) || 1500;

const CONNECT_TIMEOUT_MS = 60_000;
const SAMPLE_MS = 15_000;

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

function summarise(label: string, values: number[]): string {
  if (values.length === 0) return `${label}: no samples`;
  const sorted = [...values].sort((a, b) => a - b);
  const avg = sorted.reduce((a, b) => a + b, 0) / sorted.length;
  return `${label}: n=${sorted.length} avg=${avg.toFixed(1)}ms p50=${percentile(sorted, 50).toFixed(1)}ms p95=${percentile(sorted, 95).toFixed(1)}ms p99=${percentile(sorted, 99).toFixed(1)}ms max=${sorted[sorted.length - 1].toFixed(1)}ms`;
}

async function main() {
  const mongoUri = process.env.MONGODB_URI;
  const jwtSecret = process.env.JWT_SECRET;
  if (!mongoUri || !jwtSecret) throw new Error('MONGODB_URI and JWT_SECRET must be available (server/.env)');

  // Import after dotenv so the models see the same environment.
  const { UserModel } = await import('../models/User');
  const { RoomModel } = await import('../models/Room');

  console.log(`[Load] ${USERS} users across ${ROOMS} rooms → ${BASE_URL} (db: ${DB_NAME})`);
  await mongoose.connect(mongoUri, { dbName: DB_NAME, serverSelectionTimeoutMS: 10000 });
  await UserModel.deleteMany({});
  await RoomModel.deleteMany({});

  const tag = Date.now().toString(36).slice(-4);
  const userDocs = await UserModel.insertMany(
    Array.from({ length: USERS }, (_, i) => ({
      // Usernames are capped at 16 characters, so the tag stays short and the
      // per-user suffix is base36: truncating a decimal index made "_10" collide
      // with "_1".
      username: `l_${tag}_${i.toString(36)}`.slice(0, 16),
      displayName: `Load ${i}`,
      email: `load_${tag}_${i}@nebula.test`,
      avatarLetter: 'L',
      tier: 'free',
      status: 'dynamic',
      onboardingComplete: true,
      isOnboardingCompleted: true,
    })),
    { ordered: false }
  );
  console.log(`[Load] Created ${userDocs.length} users`);

  const perRoom = Math.ceil(userDocs.length / ROOMS);
  const groups: typeof userDocs[] = [];
  for (let r = 0; r < ROOMS; r++) {
    const slice = userDocs.slice(r * perRoom, (r + 1) * perRoom);
    if (slice.length > 0) groups.push(slice);
  }

  const roomDocs = await RoomModel.insertMany(
    groups.map((group, r) => ({
      name: `Load Room ${r}`,
      hostId: group[0]._id.toString(),
      privacy: 'public',
      activity: 'watch',
      category: 'Cinema',
      mediaState: { title: 'Load Test', subtitle: '', durationSeconds: 0, playbackPosition: 0, isPlaying: false, mediaUrl: '' },
      participants: group.map((u, i) => ({
        userId: u._id.toString(),
        username: u.username,
        displayName: u.displayName,
        avatarLetter: 'L',
        joinedAt: new Date(),
        isHost: i === 0,
      })),
      maxParticipants: 100,
    }))
  );
  console.log(`[Load] Created ${roomDocs.length} rooms`);

  // room:join refuses a socket whose user is not currently in that room.
  await Promise.all(
    roomDocs.map((room, r) =>
      UserModel.updateMany(
        { _id: { $in: groups[r].map((u) => u._id) } },
        { $set: { activeRoomId: room._id.toString(), presenceStatus: 'online' } }
      )
    )
  );

  const tokens = userDocs.map((u) => jwt.sign({ id: u._id.toString() }, jwtSecret, { expiresIn: '2h' }));

  const sockets: Socket[] = [];
  const joined = new Array(roomDocs.length).fill(0);
  const connectTimes: number[] = [];
  const chatLatencies: number[] = [];
  const errors: string[] = [];
  let messagesSent = 0;
  let messagesReceived = 0;

  const pendingTexts = new Map<string, number>();
  let textCounter = 0;

  await new Promise<void>((resolve) => {
    let settled = 0;
    const started = Date.now();

    userDocs.forEach((user, i) => {
      const roomIndex = Math.floor(i / perRoom);
      const room = roomDocs[roomIndex];
      const socket = io(BASE_URL, {
        auth: { token: tokens[i] },
        transports: ['websocket'],
        reconnection: false,
      });
      sockets.push(socket);
      const connectStarted = Date.now();

      socket.on('connect', () => {
        connectTimes.push(Date.now() - connectStarted);
        socket.emit('room:join', { roomId: room._id.toString() });
      });

      socket.on('media:sync', () => {
        joined[roomIndex] += 1;
        settled += 1;
        if (settled === userDocs.length) resolve();
      });

      socket.on('chat:message', (payload: { text?: string }) => {
        const sentAt = payload?.text ? pendingTexts.get(payload.text) : undefined;
        if (sentAt) {
          chatLatencies.push(Date.now() - sentAt);
          messagesReceived += 1;
        }
      });

      socket.on('error', (err: { message?: string }) => {
        if (err?.message) errors.push(err.message);
      });
      socket.on('connect_error', (err: Error) => errors.push(`connect_error: ${err.message}`));
    });

    setTimeout(() => resolve(), CONNECT_TIMEOUT_MS);
  });

  const connected = sockets.filter((s) => s.connected).length;
  const totalJoined = joined.reduce((a, b) => a + b, 0);
  console.log(`[Load] Connected ${connected}/${USERS}, joined rooms ${totalJoined}/${USERS}`);

  // One sender per room keeps the write path busy without tripping the socket's
  // 10-messages-per-5-seconds ceiling.
  const senders = sockets.filter((_, i) => i % perRoom === 0);
  const senderRoomIds = roomDocs.map((r) => r._id.toString());
  const senderTexts: string[] = [];

  const chatTimer = setInterval(() => {
    senders.forEach((socket, idx) => {
      if (!socket.connected) return;
      const text = `load-${textCounter++}`;
      pendingTexts.set(text, Date.now());
      senderTexts.push(text);
      socket.emit('chat:message', { roomId: senderRoomIds[idx], text });
      messagesSent += 1;
    });
  }, CHAT_INTERVAL_MS);

  await new Promise((r) => setTimeout(r, SAMPLE_MS));
  clearInterval(chatTimer);

  console.log('\n================ SCALE RESULT ================');
  console.log(`users=${USERS} rooms=${ROOMS} connected=${connected} joined=${totalJoined}`);
  console.log(summarise('socket connect', connectTimes));
  console.log(summarise('chat broadcast latency', chatLatencies));
  console.log(`messages sent=${messagesSent} received=${messagesReceived} (${(messagesReceived / Math.max(1, SAMPLE_MS / 1000)).toFixed(1)}/s fanned out)`);
  console.log(`errors=${errors.length}${errors.length ? ` (first: ${errors.slice(0, 3).join(' | ')})` : ''}`);
  console.log('=============================================\n');

  sockets.forEach((s) => s.disconnect());
  // Atlas users on the free tier cannot drop databases, so clean the collections
  // this harness created instead of the whole database.
  try {
    const { MessageModel } = await import('../models/Message');
    await Promise.all([UserModel.deleteMany({}), RoomModel.deleteMany({}), MessageModel.deleteMany({})]);
  } catch {
    /* best-effort cleanup */
  }
  await mongoose.disconnect();
  process.exit(errors.length > 0 && connected < USERS ? 1 : 0);
}

main().catch(async (err) => {
  console.error('[Load] Failed:', err?.message || err);
  try {
    await mongoose.disconnect();
  } catch {
    /* ignore */
  }
  process.exit(1);
});
