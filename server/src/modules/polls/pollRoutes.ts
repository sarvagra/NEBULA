import { Router } from 'express';
import { getRoomPoll, createPoll, votePoll, closePoll } from './pollController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody, validateObjectIdParam } from '../../middlewares/validation';
import { CreatePollSchema, VotePollSchema } from '../../utils/schemas';
import { pollVoteRateLimiter } from '../../middlewares/rateLimiter';

export const pollRouter = Router();

pollRouter.get('/room/:roomId', validateObjectIdParam('roomId'), optionalAuthMiddleware, getRoomPoll);
pollRouter.post('/', authMiddleware, validateBody(CreatePollSchema), createPoll);
pollRouter.post('/:id/vote', validateObjectIdParam('id'), authMiddleware, pollVoteRateLimiter, validateBody(VotePollSchema), votePoll);
pollRouter.post('/:id/close', validateObjectIdParam('id'), authMiddleware, closePoll);
