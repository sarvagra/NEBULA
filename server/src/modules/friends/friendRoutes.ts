import { Router } from 'express';
import {
  listFriends,
  listFriendRequests,
  sendFriendRequest,
  acceptFriendRequest,
  declineFriendRequest,
  listSuggestedPeople,
  blockUser,
} from './friendController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { SendFriendRequestSchema } from '../../utils/schemas';
import { friendRateLimiter } from '../../middlewares/rateLimiter';

export const friendRouter = Router();

friendRouter.get('/', authMiddleware, listFriends);
friendRouter.get('/requests', authMiddleware, listFriendRequests);
friendRouter.post('/request', authMiddleware, friendRateLimiter, validateBody(SendFriendRequestSchema), sendFriendRequest);
friendRouter.post('/request/:id/accept', validateObjectIdParam('id'), authMiddleware, acceptFriendRequest);
friendRouter.post('/request/:id/decline', validateObjectIdParam('id'), authMiddleware, declineFriendRequest);
friendRouter.post('/:id/block', validateObjectIdParam('id'), authMiddleware, blockUser);
friendRouter.get('/suggested', optionalAuthMiddleware, listSuggestedPeople);
