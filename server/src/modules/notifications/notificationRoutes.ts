import { Router } from 'express';
import { listNotifications, markAsRead, deleteNotification, registerDeviceToken } from './notificationController';
import {
  getReengagementPreferences,
  updateReengagementPreferences,
  runReengagementNow,
  updateNotificationPreferences,
} from './reengagementController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateObjectIdParam } from '../../middlewares/validation';

export const notificationRouter = Router();

notificationRouter.get('/', authMiddleware, listNotifications);
notificationRouter.post('/device-token', authMiddleware, registerDeviceToken);
notificationRouter.put('/:id/read', validateObjectIdParam('id'), authMiddleware, markAsRead);
notificationRouter.delete('/:id', validateObjectIdParam('id'), authMiddleware, deleteNotification);

// Re-engagement messaging is opt-in only; these endpoints own that consent.
notificationRouter.get('/reengagement', authMiddleware, getReengagementPreferences);
notificationRouter.put('/reengagement', authMiddleware, updateReengagementPreferences);
notificationRouter.post('/reengagement/run', authMiddleware, runReengagementNow);
notificationRouter.put('/preferences', authMiddleware, updateNotificationPreferences);
