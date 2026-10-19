import { Router } from 'express';
import {
  getRoomMessages,
  getConversationMessages,
  sendMessage,
  addReaction,
  uploadMedia,
  editDirectMessage,
  unsendDirectMessage,
  markConversationRead,
  triggerChatArchive,
} from './chatController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { SendMessageSchema, AddReactionSchema, EditMessageSchema } from '../../utils/schemas';
import { chatRateLimiter } from '../../middlewares/rateLimiter';

export const chatRouter = Router();

chatRouter.get('/room/:roomId', validateObjectIdParam('roomId'), optionalAuthMiddleware, getRoomMessages);
chatRouter.get('/conversation/:conversationId', authMiddleware, getConversationMessages);
chatRouter.post('/', authMiddleware, chatRateLimiter, validateBody(SendMessageSchema), sendMessage);
chatRouter.post('/upload', authMiddleware, chatRateLimiter, uploadMedia);
chatRouter.post('/:messageId/reaction', validateObjectIdParam('messageId'), authMiddleware, validateBody(AddReactionSchema), addReaction);
chatRouter.post('/conversation/:conversationId/read', authMiddleware, markConversationRead);
chatRouter.post('/maintenance/archive', authMiddleware, triggerChatArchive);
chatRouter.put('/:messageId', validateObjectIdParam('messageId'), authMiddleware, validateBody(EditMessageSchema), editDirectMessage);
chatRouter.delete('/:messageId', validateObjectIdParam('messageId'), authMiddleware, unsendDirectMessage);
