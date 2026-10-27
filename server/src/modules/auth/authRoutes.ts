import { Router } from 'express';
import { googleSignIn, requestOtp, verifyOtp, refreshToken, logout, getMe, devLogin, getActiveSessions, revokeSessionById, revokeOtherSessions } from './authController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import {
  GoogleAuthSchema,
  OtpRequestSchema,
  OtpVerifySchema,
  RefreshTokenSchema,
} from '../../utils/schemas';
import {
  authRateLimiter,
  otpRequestRateLimiter,
  otpVerifyRateLimiter,
  refreshTokenRateLimiter,
} from '../../middlewares/rateLimiter';

export const authRouter = Router();

authRouter.post('/google', authRateLimiter, validateBody(GoogleAuthSchema), googleSignIn);
authRouter.post('/firebase', authRateLimiter, validateBody(GoogleAuthSchema), googleSignIn);
authRouter.post('/otp/request', otpRequestRateLimiter, validateBody(OtpRequestSchema), requestOtp);
authRouter.post('/otp/verify', otpVerifyRateLimiter, validateBody(OtpVerifySchema), verifyOtp);
authRouter.post('/refresh', refreshTokenRateLimiter, validateBody(RefreshTokenSchema), refreshToken);
authRouter.post('/logout', authMiddleware, logout);
authRouter.get('/me', authMiddleware, getMe);
authRouter.get('/sessions', authMiddleware, getActiveSessions);
authRouter.post('/sessions/revoke-others', authMiddleware, revokeOtherSessions);
authRouter.delete('/sessions/:sessionId', authMiddleware, revokeSessionById);
authRouter.post('/dev-login', devLogin);
