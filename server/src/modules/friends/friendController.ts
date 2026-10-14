import { Request, Response } from 'express';
import { FriendshipModel } from '../../models/Friendship';
import { UserModel } from '../../models/User';
import { NotificationModel } from '../../models/Notification';
import { toPublicUserDTO } from '../../utils/dto';
import { notifyUserSocialChanged } from '../../realtime/socketServer';
import { RoomModel } from '../../models/Room';

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

    const blockedRecords = await FriendshipModel.find({ status: 'blocked', $or: [{ requesterId: req.user.id }, { recipientId: req.user.id }] }).select('requesterId recipientId');
    const blockedIds = blockedRecords.map((f) => f.requesterId === req.user!.id ? f.recipientId : f.requesterId);
    const friends = await UserModel.find({
      _id: { $in: friendUserIds, $nin: blockedIds },
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

    const blockedRecords = await FriendshipModel.find({ status: 'blocked', $or: [{ requesterId: req.user.id }, { recipientId: req.user.id }] }).select('requesterId recipientId');
    const blockedIds = blockedRecords.map((f) => f.requesterId === req.user!.id ? f.recipientId : f.requesterId);
    const requests = await FriendshipModel.find({
      recipientId: req.user.id,
      status: 'pending',
      requesterId: { $nin: blockedIds },
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

    if (!(await UserModel.exists({ _id: targetUserId }))) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }
    await FriendshipModel.deleteMany({ requesterId: req.user.id, recipientId: targetUserId });
    await FriendshipModel.deleteMany({ requesterId: targetUserId, recipientId: req.user.id, status: { $ne: 'blocked' } });
    await FriendshipModel.findOneAndUpdate(
      { requesterId: req.user.id, recipientId: targetUserId },
      { $set: { status: 'blocked' } },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    notifyUserSocialChanged([req.user.id, targetUserId]);

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

export async function removeFriend(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    res.status(401).json({ success: false, error: 'Authentication required' });
    return;
  }
  const removed = await FriendshipModel.findOneAndDelete({
    status: 'accepted',
    $or: [
      { requesterId: req.user.id, recipientId: req.params.id },
      { requesterId: req.params.id, recipientId: req.user.id },
    ],
  });
  if (!removed) {
    res.status(404).json({ success: false, error: 'Friendship not found' });
    return;
  }
  notifyUserSocialChanged([req.user.id, req.params.id]);
  res.json({ success: true, message: 'Friend removed' });
}

export async function listSuggestedPeople(req: Request, res: Response): Promise<void> {
  try {
    const currentUserId = req.user?.id;
    if (!currentUserId) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }
    const me = await UserModel.findById(currentUserId).select('tier');
    const limit = me?.tier === 'premium_plus' ? 12 : me?.tier === 'premium' ? 10 : 5;
    const excluded = await FriendshipModel.find({ $or: [{ requesterId: currentUserId }, { recipientId: currentUserId }] }).select('requesterId recipientId');
    const excludedIds = new Set<string>([currentUserId]);
    excluded.forEach((item) => excludedIds.add(item.requesterId === currentUserId ? item.recipientId : item.requesterId));
    const omittedIds = Array.isArray(req.body?.excludeIds) ? req.body.excludeIds.filter((id: unknown) => typeof id === 'string') : [];
    omittedIds.forEach((id: string) => excludedIds.add(id));
    const firstDegree = [...excludedIds].filter((id) => id !== currentUserId);
    const [friendLinks, sharedRooms] = await Promise.all([
      firstDegree.length ? FriendshipModel.find({ status: 'accepted', $or: [{ requesterId: { $in: firstDegree } }, { recipientId: { $in: firstDegree } }] }).select('requesterId recipientId').limit(2000) : Promise.resolve([]),
      RoomModel.find({ 'participants.userId': currentUserId }).select('participants.userId').sort({ updatedAt: -1 }).limit(200),
    ]);
    const friendOfFriendIds = new Set<string>();
    friendLinks.forEach((edge: any) => {
      const candidate = firstDegree.includes(edge.requesterId) ? edge.recipientId : edge.requesterId;
      if (!excludedIds.has(candidate)) friendOfFriendIds.add(candidate);
    });
    const roomMateIds = new Set<string>();
    sharedRooms.forEach((room: any) => room.participants.forEach((p: any) => {
      if (!excludedIds.has(p.userId)) roomMateIds.add(p.userId);
    }));
    const priorityIds = [...new Set([...friendOfFriendIds, ...roomMateIds])].slice(0, limit * 4);
    const priorityUsers = priorityIds.length
      ? await UserModel.find({ _id: { $in: priorityIds }, isBanned: false, isOnboardingCompleted: true }).limit(limit * 4)
      : [];
    const priorityMap = new Map(priorityUsers.map((u) => [u._id.toString(), u]));
    const ordered = [
      ...[...friendOfFriendIds].map((id) => priorityMap.get(id)).filter(Boolean),
      ...[...roomMateIds].map((id) => priorityMap.get(id)).filter(Boolean),
    ].filter((u, index, arr) => arr.findIndex((other) => other?._id.toString() === u?._id.toString()) === index) as any[];
    const network = ordered.slice(0, Math.max(0, limit - 1));
    const remaining = limit - network.length;
    const random = remaining > 0 ? await UserModel.aggregate([
      { $match: { _id: { $nin: [...excludedIds, ...ordered.map((u) => u._id)] }, isBanned: false, isOnboardingCompleted: true } },
      { $sample: { size: remaining } },
    ]) : [];
    const suggested = [...network, ...random];

    res.json({
      success: true,
      data: suggested.map((u) => toPublicUserDTO(u, 'none')),
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve suggestions' });
  }
}

export async function refreshSuggestedPeople(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    res.status(401).json({ success: false, error: 'Authentication required' });
    return;
  }
  const user = await UserModel.findById(req.user.id);
  if (!user) {
    res.status(404).json({ success: false, error: 'User not found' });
    return;
  }
  const today = new Date().toISOString().slice(0, 10);
  if (user.suggestionRefreshDate !== today) {
    user.suggestionRefreshDate = today;
    user.suggestionRefreshCount = 0;
  }
  const cap = user.tier === 'premium_plus' ? 12 : user.tier === 'premium' ? 6 : 3;
  if ((user.suggestionRefreshCount || 0) >= cap) {
    res.status(429).json({ success: false, error: `Daily suggestion refresh limit reached (${cap}).` });
    return;
  }
  user.suggestionRefreshCount = (user.suggestionRefreshCount || 0) + 1;
  await user.save();
  // The normal list endpoint applies the same graph ranking and tier result limit.
  await listSuggestedPeople(req, res);
}

export async function unblockUser(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    res.status(401).json({ success: false, error: 'Authentication required' });
    return;
  }
  const removed = await FriendshipModel.findOneAndDelete({ requesterId: req.user.id, recipientId: req.params.id, status: 'blocked' });
  if (!removed) {
    res.status(404).json({ success: false, error: 'Blocked user not found' });
    return;
  }
  notifyUserSocialChanged([req.user.id, req.params.id]);
  res.json({ success: true, message: 'User unblocked' });
}

export async function listBlockedUsers(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    res.status(401).json({ success: false, error: 'Authentication required' });
    return;
  }
  const blocks = await FriendshipModel.find({ requesterId: req.user.id, status: 'blocked' }).sort({ updatedAt: -1 }).limit(500);
  const users = await UserModel.find({ _id: { $in: blocks.map((b) => b.recipientId) }, isBanned: false });
  res.json({ success: true, data: users.map((user) => toPublicUserDTO(user, 'blocked')) });
}

export async function searchUsers(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    res.status(401).json({ success: false, error: 'Authentication required' });
    return;
  }
  const query = String(req.query.q || '').trim().replace(/^@/, '').slice(0, 40);
  if (query.length < 2) {
    res.json({ success: true, data: [] });
    return;
  }
  const blocked = await FriendshipModel.find({ status: 'blocked', $or: [{ requesterId: req.user.id }, { recipientId: req.user.id }] }).select('requesterId recipientId');
  const blockedIds = blocked.map((item) => item.requesterId === req.user!.id ? item.recipientId : item.requesterId);
  const escaped = query.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const users = await UserModel.find({
    _id: { $nin: [req.user.id, ...blockedIds] },
    isBanned: false,
    isOnboardingCompleted: true,
    $or: [
      { username: { $regex: `^${escaped}` } },
      { $text: { $search: query } },
    ],
  }).limit(25);
  const rels = users.length ? await FriendshipModel.find({ $or: users.flatMap((u) => [
    { requesterId: req.user!.id, recipientId: u._id.toString() },
    { requesterId: u._id.toString(), recipientId: req.user!.id },
  ]) }).select('requesterId recipientId status') : [];
  const candidateIds = users.map((user) => user._id.toString());
  const directEdges = await FriendshipModel.find({ status: 'accepted', $or: [{ requesterId: req.user.id }, { recipientId: req.user.id }] }).select('requesterId recipientId');
  const directIds = directEdges.map((edge) => edge.requesterId === req.user!.id ? edge.recipientId : edge.requesterId);
  const [friendOfFriendEdges, sharedRooms] = await Promise.all([
    directIds.length && candidateIds.length ? FriendshipModel.find({ status: 'accepted', $or: [
      { requesterId: { $in: directIds }, recipientId: { $in: candidateIds } },
      { recipientId: { $in: directIds }, requesterId: { $in: candidateIds } },
    ] }).select('requesterId recipientId') : Promise.resolve([]),
    candidateIds.length ? RoomModel.find({ 'participants.userId': req.user.id }).select('participants.userId').sort({ updatedAt: -1 }).limit(200) : Promise.resolve([]),
  ]);
  const friendOfFriendIds = new Set<string>();
  friendOfFriendEdges.forEach((edge: any) => {
    const candidate = candidateIds.includes(edge.requesterId) ? edge.requesterId : edge.recipientId;
    if (candidateIds.includes(candidate)) friendOfFriendIds.add(candidate);
  });
  const sharedRoomIds = new Set<string>();
  sharedRooms.forEach((room: any) => room.participants.forEach((participant: any) => {
    if (candidateIds.includes(participant.userId)) sharedRoomIds.add(participant.userId);
  }));
  const data = users.map((user) => {
    const relation = rels.find((edge) => edge.requesterId === req.user!.id && edge.recipientId === user._id.toString() || edge.recipientId === req.user!.id && edge.requesterId === user._id.toString());
    const dto = toPublicUserDTO(user, relation?.status === 'accepted' ? 'friends' : relation?.status === 'pending' ? (relation.requesterId === req.user!.id ? 'request_sent' : 'request_received') : 'none');
    if (relation?.status === 'pending' && relation.requesterId === user._id.toString()) dto.friendRequestId = relation._id.toString();
    return dto;
  }).sort((a, b) => {
    const rank = (profile: typeof a) => friendOfFriendIds.has(profile.id) ? 0 : sharedRoomIds.has(profile.id) ? 1 : profile.relationship === 'friends' ? 2 : profile.relationship === 'request_received' || profile.relationship === 'request_sent' ? 3 : 4;
    return rank(a) - rank(b);
  });
  res.json({ success: true, data });
}
