import { Router } from 'express';
import { listNotifications, markAsRead, deleteNotification } from './notificationController';
import { authMiddleware } from '../../middlewares/authMiddleware';
import { validateObjectIdParam } from '../../middlewares/validation';

export const notificationRouter = Router();

notificationRouter.get('/', authMiddleware, listNotifications);
notificationRouter.put('/:id/read', validateObjectIdParam('id'), authMiddleware, markAsRead);
notificationRouter.delete('/:id', validateObjectIdParam('id'), authMiddleware, deleteNotification);
