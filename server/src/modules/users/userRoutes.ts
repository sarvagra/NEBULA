import { Router } from 'express';
import { updateProfile, updatePresence, checkUsername, getPublicProfile, getPublicProfileById } from './userController';
import { listAssets, uploadAsset, applyAsset, deleteAsset } from './profileAssetController';
import {
  getDeletionStatus,
  requestAccountDeletion,
  confirmAccountDeletion,
  cancelAccountDeletion,
} from './accountLifecycleController';
import { getEmailChangeStatus, rebindEmail } from './emailRebindController';
import { authMiddleware, optionalAuthMiddleware } from '../../middlewares/authMiddleware';
import { validateBody } from '../../middlewares/validation';
import { UpdateProfileSchema, CreateAssetSchema } from '../../utils/schemas';
import { profileRateLimiter } from '../../middlewares/rateLimiter';

export const userRouter = Router();

userRouter.put('/profile', authMiddleware, profileRateLimiter, validateBody(UpdateProfileSchema), updateProfile);
userRouter.put('/presence', authMiddleware, updatePresence);
userRouter.get('/check-username/:username', authMiddleware, profileRateLimiter, checkUsername);
userRouter.get('/public/id/:id', optionalAuthMiddleware, getPublicProfileById);
userRouter.get('/public/:username', optionalAuthMiddleware, getPublicProfile);

// Replacing the linked Google identity: one free change per six months, priced after.
userRouter.get('/me/email', authMiddleware, getEmailChangeStatus);
userRouter.post('/me/email/rebind', authMiddleware, rebindEmail);

// Account lifecycle: OTP-verified deletion scheduled 30 days out, cancellable by login.
userRouter.get('/me/deletion', authMiddleware, getDeletionStatus);
userRouter.post('/me/deletion/request', authMiddleware, requestAccountDeletion);
userRouter.post('/me/deletion/confirm', authMiddleware, confirmAccountDeletion);
userRouter.post('/me/deletion/cancel', authMiddleware, cancelAccountDeletion);

// Profile Asset History / Media Management
userRouter.get('/me/assets', authMiddleware, listAssets);
userRouter.post('/me/assets', authMiddleware, validateBody(CreateAssetSchema), uploadAsset);
userRouter.post('/me/assets/:id/apply', authMiddleware, applyAsset);
userRouter.delete('/me/assets/:id', authMiddleware, deleteAsset);
