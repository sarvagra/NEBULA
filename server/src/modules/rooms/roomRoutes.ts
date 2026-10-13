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
} from './roomController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { CreateRoomSchema, UpdateMediaSchema } from '../../utils/schemas';
import { roomCreateRateLimiter, roomJoinRateLimiter } from '../../middlewares/rateLimiter';

export const roomRouter = Router();

roomRouter.get('/', optionalAuthMiddleware, listRooms);
roomRouter.get('/:id', validateObjectIdParam('id'), optionalAuthMiddleware, getRoom);
roomRouter.post('/', authMiddleware, roomCreateRateLimiter, validateBody(CreateRoomSchema), createRoom);
roomRouter.post('/:id/join', validateObjectIdParam('id'), authMiddleware, roomJoinRateLimiter, joinRoom);
roomRouter.post('/:id/leave', validateObjectIdParam('id'), authMiddleware, leaveRoom);
roomRouter.put('/:id/settings', validateObjectIdParam('id'), authMiddleware, updateRoomSettings);
roomRouter.post('/:id/transfer-host', validateObjectIdParam('id'), authMiddleware, transferHost);
roomRouter.put('/:id/media', validateObjectIdParam('id'), authMiddleware, validateBody(UpdateMediaSchema), updateMedia);
roomRouter.delete('/:id/participants/:targetUserId', validateObjectIdParam('id'), authMiddleware, kickParticipant);
