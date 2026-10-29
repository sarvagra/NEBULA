import { Router } from 'express';
import { googleSignIn, refreshToken, logout, getMe, getActiveSessions, revokeSessionById, revokeOtherSessions } from './authController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { GoogleAuthSchema, RefreshTokenSchema } from '../../utils/schemas';
import { authRateLimiter, refreshTokenRateLimiter } from '../../middlewares/rateLimiter';

/**
 * Identity is Firebase Authentication only, and Google is the only enabled
 * provider. There is deliberately no password, email-link, OTP or developer
 * login route: a session can only be minted from a Firebase ID token that the
 * Admin SDK has verified.
 */
export const authRouter = Router();

authRouter.post('/google', authRateLimiter, validateBody(GoogleAuthSchema), googleSignIn);
authRouter.post('/firebase', authRateLimiter, validateBody(GoogleAuthSchema), googleSignIn);
authRouter.post('/refresh', refreshTokenRateLimiter, validateBody(RefreshTokenSchema), refreshToken);
authRouter.post('/logout', authMiddleware, logout);
authRouter.get('/me', authMiddleware, getMe);
authRouter.get('/sessions', authMiddleware, getActiveSessions);
authRouter.post('/sessions/revoke-others', authMiddleware, revokeOtherSessions);
authRouter.delete('/sessions/:sessionId', authMiddleware, revokeSessionById);
