import { Request, Response } from 'express';
import { FriendshipModel } from '../../models/Friendship';
import { UserModel } from '../../models/User';
import { NotificationModel } from '../../models/Notification';
import { toPublicUserDTO } from '../../utils/dto';
import { notifyUserSocialChanged } from '../../realtime/socketServer';

export async function listFriends(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const friendships = await FriendshipModel.find({
      $or: [{ requesterId: req.user.id }, { recipientId: req.user.id }],
      status: 'accepted',
    });

    const friendUserIds = friendships.map((f) =>
      f.requesterId === req.user!.id ? f.recipientId : f.requesterId
    );

    const friends = await UserModel.find({
      _id: { $in: friendUserIds },
      isBanned: false,
    });

    res.json({
      success: true,
      data: friends.map((f) => toPublicUserDTO(f, 'friends')),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve friends list' });
  }
}

export async function listFriendRequests(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const requests = await FriendshipModel.find({
      recipientId: req.user.id,
      status: 'pending',
    }).limit(30);

    const requesterIds = requests.map((r) => r.requesterId);
    const requesters = await UserModel.find({ _id: { $in: requesterIds } });

    const data = requests.map((reqItem) => {
      const user = requesters.find((u) => u._id.toString() === reqItem.requesterId);
      return {
        id: reqItem._id.toString(),
        requester: user
          ? toPublicUserDTO(user, 'request_received')
          : { id: reqItem.requesterId, displayName: 'Nebula User', avatarLetter: 'U' },
        createdAt: reqItem.createdAt,
      };
    });

    res.json({ success: true, data });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve friend requests' });
  }
}

export async function sendFriendRequest(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    let targetRecipientId = req.body.recipientId;
    if (!targetRecipientId && req.body.username) {
      const cleanUsername = req.body.username.trim().replace(/^@/, '').toLowerCase();
      if (!cleanUsername) {
        res.status(400).json({ success: false, error: 'Username cannot be empty' });
        return;
      }
      const foundUser = await UserModel.findOne({ username: cleanUsername });
      if (!foundUser) {
        res.status(404).json({ success: false, error: 'User not found with that username' });
        return;
      }
      targetRecipientId = foundUser._id.toString();
    }

    if (!targetRecipientId || targetRecipientId === req.user.id) {
      res.status(400).json({ success: false, error: 'Cannot send friend request to yourself or invalid recipient' });
      return;
    }

    const recipient = await UserModel.findById(targetRecipientId);
    if (!recipient || recipient.isBanned) {
      res.status(404).json({ success: false, error: 'User not found or unavailable' });
      return;
    }

    const existing = await FriendshipModel.findOne({
      $or: [
        { requesterId: req.user.id, recipientId: targetRecipientId },
        { requesterId: targetRecipientId, recipientId: req.user.id },
      ],
    });

    if (existing) {
      if (existing.status === 'blocked') {
        res.status(403).json({ success: false, error: 'Unable to send friend request' });
        return;
      }
      res.status(409).json({ success: false, error: 'A friendship or pending request already exists' });
      return;
    }

    const friendship = await FriendshipModel.create({
      requesterId: req.user.id, // Strictly server-authoritative
      recipientId: targetRecipientId,
      status: 'pending',
    });

    // Create secure in-app notification for recipient
    await NotificationModel.create({
      userId: targetRecipientId,
      type: 'friend_request',
      title: 'New Friend Request',
      body: `${req.user.displayName} sent you a friend request.`,
      actionData: { friendshipId: friendship._id.toString(), requesterId: req.user.id },
    });
    notifyUserSocialChanged([req.user.id, targetRecipientId]);

    res.status(201).json({
      success: true,
      message: 'Friend request sent',
      data: { id: friendship._id.toString(), status: friendship.status },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to send friend request' });
  }
}

export async function blockUser(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const targetUserId = req.params.id;
    if (targetUserId === req.user.id) {
      res.status(400).json({ success: false, error: 'Cannot block yourself' });
      return;
    }

    await FriendshipModel.deleteMany({
      $or: [
        { requesterId: req.user.id, recipientId: targetUserId },
        { requesterId: targetUserId, recipientId: req.user.id },
      ],
    });

    await FriendshipModel.create({
      requesterId: req.user.id,
      recipientId: targetUserId,
      status: 'blocked',
    });

    res.json({ success: true, message: 'User blocked successfully' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to block user' });
  }
}

export async function acceptFriendRequest(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const friendship = await FriendshipModel.findById(id);
    if (!friendship || friendship.recipientId !== req.user.id) {
      res.status(404).json({ success: false, error: 'Friend request not found or unauthorized' });
      return;
    }

    if (friendship.status === 'accepted') {
      res.json({ success: true, message: 'Already accepted' });
      return;
    }

    friendship.status = 'accepted';
    await friendship.save();
    notifyUserSocialChanged([friendship.requesterId, friendship.recipientId]);

    res.json({ success: true, message: 'Friend request accepted' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to accept friend request' });
  }
}

export async function declineFriendRequest(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const friendship = await FriendshipModel.findById(id);
    if (!friendship || (friendship.recipientId !== req.user.id && friendship.requesterId !== req.user.id)) {
      res.status(404).json({ success: false, error: 'Friend request not found or unauthorized' });
      return;
    }

    const requesterId = friendship.requesterId;
    const recipientId = friendship.recipientId;
    await FriendshipModel.findByIdAndDelete(id);
    notifyUserSocialChanged([requesterId, recipientId]);
    res.json({ success: true, message: 'Friend request removed' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to decline friend request' });
  }
}

export async function withdrawFriendRequest(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }
    const friendship = await FriendshipModel.findOneAndDelete({
      requesterId: req.user.id,
      recipientId: req.params.id,
      status: 'pending',
    });
    if (!friendship) {
      res.status(404).json({ success: false, error: 'Pending friend request not found' });
      return;
    }
    notifyUserSocialChanged([friendship.requesterId, friendship.recipientId]);
    res.json({ success: true, message: 'Friend request withdrawn' });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to withdraw friend request' });
  }
}

export async function listSuggestedPeople(req: Request, res: Response): Promise<void> {
  try {
    const currentUserId = req.user?.id;
    const filter: Record<string, any> = { isBanned: false };
    if (currentUserId) filter._id = { $ne: currentUserId };

    const suggested = await UserModel.find(filter).limit(10);

    res.json({
      success: true,
      data: suggested.map((u) => toPublicUserDTO(u, 'none')),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve suggestions' });
  }
}
