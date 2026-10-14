import { Router } from 'express';
import {
  listFriends,
  listFriendRequests,
  sendFriendRequest,
  acceptFriendRequest,
  declineFriendRequest,
  withdrawFriendRequest,
  listSuggestedPeople,
  blockUser,
  unblockUser,
  listBlockedUsers,
  refreshSuggestedPeople,
  searchUsers,
  removeFriend,
} from './friendController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { SendFriendRequestSchema } from '../../utils/schemas';
import { friendRateLimiter } from '../../middlewares/rateLimiter';

export const friendRouter = Router();

friendRouter.get('/', authMiddleware, listFriends);
friendRouter.get('/requests', authMiddleware, listFriendRequests);
friendRouter.get('/search', authMiddleware, friendRateLimiter, searchUsers);
friendRouter.get('/blocked', authMiddleware, listBlockedUsers);
friendRouter.delete('/:id/block', validateObjectIdParam('id'), authMiddleware, unblockUser);
friendRouter.post('/request', authMiddleware, friendRateLimiter, validateBody(SendFriendRequestSchema), sendFriendRequest);
friendRouter.post('/request/:id/accept', validateObjectIdParam('id'), authMiddleware, acceptFriendRequest);
friendRouter.post('/request/:id/decline', validateObjectIdParam('id'), authMiddleware, declineFriendRequest);
friendRouter.delete('/request/withdraw/:id', validateObjectIdParam('id'), authMiddleware, withdrawFriendRequest);
friendRouter.delete('/:id', validateObjectIdParam('id'), authMiddleware, removeFriend);
friendRouter.post('/:id/block', validateObjectIdParam('id'), authMiddleware, blockUser);
friendRouter.get('/suggested', optionalAuthMiddleware, listSuggestedPeople);
friendRouter.post('/suggested/refresh', authMiddleware, refreshSuggestedPeople);
