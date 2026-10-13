import { Request, Response, NextFunction } from 'express';
import { RoomModel } from '../models/Room';

export function requireRoomHost(getRoomId: (req: Request) => string = (req) => req.params.id || req.params.roomId) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({ success: false, error: 'Authentication required' });
        return;
      }

      const roomId = getRoomId(req);
      if (!roomId) {
        res.status(400).json({ success: false, error: 'Room ID is required' });
        return;
      }

      const room = await RoomModel.findById(roomId);
      if (!room) {
        res.status(404).json({ success: false, error: 'Room not found' });
        return;
      }

      if (room.hostId !== req.user.id && req.user.role !== 'admin') {
        res.status(403).json({
          success: false,
          error: 'Forbidden: Only the room host has permission to perform this action',
        });
        return;
      }

      // Attach verified room to request
      (req as any).room = room;
      next();
    } catch (err: any) {
      res.status(500).json({ success: false, error: 'Authorization check failed' });
    }
  };
}

export function requireRoomMember(getRoomId: (req: Request) => string = (req) => req.params.id || req.params.roomId || req.body.roomId) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({ success: false, error: 'Authentication required' });
        return;
      }

      const roomId = getRoomId(req);
      if (!roomId) {
        res.status(400).json({ success: false, error: 'Room ID is required' });
        return;
      }

      const room = await RoomModel.findById(roomId);
      if (!room) {
        res.status(404).json({ success: false, error: 'Room not found' });
        return;
      }

      const isMember = room.participants.some((p) => p.userId === req.user!.id);
      if (!isMember && req.user.role !== 'admin') {
        res.status(403).json({
          success: false,
          error: 'Forbidden: You must be an active member of this room to perform this action',
        });
        return;
      }

      (req as any).room = room;
      next();
    } catch (err: any) {
      res.status(500).json({ success: false, error: 'Authorization check failed' });
    }
  };
}
