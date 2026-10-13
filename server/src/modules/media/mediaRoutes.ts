import { Router } from 'express';
import { authMiddleware } from '../../middlewares/authMiddleware';
import {
  searchYoutube,
  resolveYoutube,
  getMediaHistory,
  addMediaHistory,
  deleteMediaHistory,
} from './mediaController';

export const mediaRouter = Router();

// Search and Resolve are accessible to authenticated users
mediaRouter.get('/youtube/search', authMiddleware, searchYoutube);
mediaRouter.get('/youtube/resolve', authMiddleware, resolveYoutube);

// Media history management
mediaRouter.get('/history', authMiddleware, getMediaHistory);
mediaRouter.post('/history', authMiddleware, addMediaHistory);
mediaRouter.delete('/history', authMiddleware, deleteMediaHistory);
