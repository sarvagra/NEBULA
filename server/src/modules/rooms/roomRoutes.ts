import { Router } from 'express';
import {
  listRooms,
  getRoom,
  createRoom,
  joinRoom,
  leaveRoom,
  updateMedia,
  kickParticipant,
  updateRoomSettings,
  transferHost,
  inviteRoomParticipant,
  getRoomByInviteCode,
  listRoomQueue,
  addRoomQueueItem,
  voteSkipQueueItem,
  advanceRoomQueue,
  removeRoomQueueItem,
} from './roomController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { CreateRoomSchema, InviteRoomSchema, UpdateMediaSchema } from '../../utils/schemas';
import { roomCreateRateLimiter, roomJoinRateLimiter } from '../../middlewares/rateLimiter';

export const roomRouter = Router();

roomRouter.get('/', optionalAuthMiddleware, listRooms);
// Resolve a shared code before the id route, so a code is never parsed as an id.
roomRouter.get('/invite/:code', authMiddleware, getRoomByInviteCode);
roomRouter.get('/:id', validateObjectIdParam('id'), optionalAuthMiddleware, getRoom);
roomRouter.post('/', authMiddleware, roomCreateRateLimiter, validateBody(CreateRoomSchema), createRoom);
roomRouter.post('/:id/join', validateObjectIdParam('id'), authMiddleware, roomJoinRateLimiter, joinRoom);
roomRouter.post('/:id/leave', validateObjectIdParam('id'), authMiddleware, leaveRoom);
roomRouter.put('/:id/settings', validateObjectIdParam('id'), authMiddleware, updateRoomSettings);
roomRouter.post('/:id/transfer-host', validateObjectIdParam('id'), authMiddleware, transferHost);
roomRouter.post('/:id/invite', validateObjectIdParam('id'), authMiddleware, validateBody(InviteRoomSchema), inviteRoomParticipant);
// Room queue: any member may add and vote, only the host may force the next item.
roomRouter.get('/:id/queue', validateObjectIdParam('id'), authMiddleware, listRoomQueue);
roomRouter.post('/:id/queue', validateObjectIdParam('id'), authMiddleware, addRoomQueueItem);
roomRouter.post('/:id/queue/advance', validateObjectIdParam('id'), authMiddleware, advanceRoomQueue);
roomRouter.post('/:id/queue/:itemId/skip-vote', authMiddleware, voteSkipQueueItem);
roomRouter.delete('/:id/queue/:itemId', authMiddleware, removeRoomQueueItem);
roomRouter.put('/:id/media', validateObjectIdParam('id'), authMiddleware, validateBody(UpdateMediaSchema), updateMedia);
roomRouter.delete('/:id/participants/:targetUserId', validateObjectIdParam('id'), authMiddleware, kickParticipant);
