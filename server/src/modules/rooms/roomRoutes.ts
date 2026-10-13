import { Router } from 'express';
import {
  listRooms,
  getRoom,
  createRoom,
  joinRoom,
  leaveRoom,
  updateMedia,
  kickParticipant,
  moderateRoomParticipant,
  updateRoomSettings,
  transferHost,
  inviteRoomParticipant,
} from './roomController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { CreateRoomSchema, InviteRoomSchema, UpdateMediaSchema, ModerateRoomParticipantSchema } from '../../utils/schemas';
import { roomCreateRateLimiter, roomJoinRateLimiter } from '../../middlewares/rateLimiter';

export const roomRouter = Router();

roomRouter.get('/', optionalAuthMiddleware, listRooms);
roomRouter.get('/:id', validateObjectIdParam('id'), optionalAuthMiddleware, getRoom);
roomRouter.post('/', authMiddleware, roomCreateRateLimiter, validateBody(CreateRoomSchema), createRoom);
roomRouter.post('/:id/join', validateObjectIdParam('id'), authMiddleware, roomJoinRateLimiter, joinRoom);
roomRouter.post('/:id/leave', validateObjectIdParam('id'), authMiddleware, leaveRoom);
roomRouter.put('/:id/settings', validateObjectIdParam('id'), authMiddleware, updateRoomSettings);
roomRouter.post('/:id/transfer-host', validateObjectIdParam('id'), authMiddleware, transferHost);
roomRouter.post('/:id/invite', validateObjectIdParam('id'), authMiddleware, validateBody(InviteRoomSchema), inviteRoomParticipant);
roomRouter.put('/:id/media', validateObjectIdParam('id'), authMiddleware, validateBody(UpdateMediaSchema), updateMedia);
roomRouter.delete('/:id/participants/:targetUserId', validateObjectIdParam('id'), authMiddleware, kickParticipant);
roomRouter.put('/:id/participants/:targetUserId/moderation', validateObjectIdParam('id'), authMiddleware, validateBody(ModerateRoomParticipantSchema), moderateRoomParticipant);
