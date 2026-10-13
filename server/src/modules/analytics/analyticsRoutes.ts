import { Router } from 'express';
import { trackClientTelemetry, getUserAnalytics } from './analyticsController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { TrackAnalyticsSchema } from '../../utils/schemas';
import { analyticsRateLimiter } from '../../middlewares/rateLimiter';

export const analyticsRouter = Router();

analyticsRouter.post('/track', optionalAuthMiddleware, analyticsRateLimiter, validateBody(TrackAnalyticsSchema), trackClientTelemetry);
analyticsRouter.get('/user', authMiddleware, getUserAnalytics);
