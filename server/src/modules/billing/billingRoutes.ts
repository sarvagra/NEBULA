import { Router } from 'express';
import { getPlans, getEntitlement, verifyGooglePlayPurchase } from './billingController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { VerifyPurchaseSchema } from '../../utils/schemas';
import { billingRateLimiter } from '../../middlewares/rateLimiter';

export const billingRouter = Router();

billingRouter.get('/plans', getPlans);
billingRouter.get('/entitlement', authMiddleware, getEntitlement);
billingRouter.post('/verify', authMiddleware, billingRateLimiter, validateBody(VerifyPurchaseSchema), verifyGooglePlayPurchase);
