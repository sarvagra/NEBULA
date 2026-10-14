import { Router } from 'express';
import { updateProfile, checkUsername, getPublicProfile, getPublicProfileById } from './userController';
import { listAssets, uploadAsset, applyAsset, deleteAsset } from './profileAssetController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { UpdateProfileSchema, CreateAssetSchema } from '../../utils/schemas';
import { profileRateLimiter } from '../../middlewares/rateLimiter';

export const userRouter = Router();

userRouter.put('/profile', authMiddleware, profileRateLimiter, validateBody(UpdateProfileSchema), updateProfile);
userRouter.get('/check-username/:username', authMiddleware, profileRateLimiter, checkUsername);
userRouter.get('/public/id/:id', optionalAuthMiddleware, getPublicProfileById);
userRouter.get('/public/:username', optionalAuthMiddleware, getPublicProfile);

// Profile Asset History / Media Management
userRouter.get('/me/assets', authMiddleware, listAssets);
userRouter.post('/me/assets', authMiddleware, validateBody(CreateAssetSchema), uploadAsset);
userRouter.post('/me/assets/:id/apply', authMiddleware, applyAsset);
userRouter.delete('/me/assets/:id', authMiddleware, deleteAsset);
