import { Router } from 'express';
import { getPlans, getEntitlement, verifyGooglePlayPurchase, createRazorpayOrder, verifyRazorpayPayment } from './billingController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { VerifyPurchaseSchema } from '../../utils/schemas';
import { billingRateLimiter } from '../../middlewares/rateLimiter';

export const billingRouter = Router();

billingRouter.get('/plans', getPlans);
billingRouter.get('/entitlement', authMiddleware, getEntitlement);
billingRouter.post('/razorpay/create-order', authMiddleware, billingRateLimiter, createRazorpayOrder);
billingRouter.post('/razorpay/verify', authMiddleware, billingRateLimiter, verifyRazorpayPayment);
billingRouter.post('/verify', authMiddleware, billingRateLimiter, validateBody(VerifyPurchaseSchema), verifyGooglePlayPurchase);
