import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { UserModel } from '../../models/User';
import { FriendshipModel } from '../../models/Friendship';
import { toPrivateUserDTO, toPublicUserDTO } from '../../utils/dto';
import { RoomModel } from '../../models/Room';
import { notifyRoomsParticipantProfileUpdated } from '../../realtime/socketServer';

export async function updateProfile(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { displayName, username, bio, status, customStatus, bannerIndex, bannerUrl, interests, avatarUrl, isFriendCountPublic, isOnboardingCompleted, dateOfBirth, gender } = req.body;

    // STRICT SERVER AUTHORIZATION: Only mutate user-permitted properties.
    // NEVER accept tier, role, isBanned, or stats from client!
    const updateFields: Record<string, any> = {};

    // Check if username is being changed, ensure uniqueness
    if (username) {
      const cleanUsername = username.toLowerCase().trim().replace(/^@/, '');
      if (!/^[a-z0-9_]{3,20}$/.test(cleanUsername)) {
        res.status(400).json({
          success: false,
          error: 'Username must be between 3 and 20 characters, containing only letters, numbers, and underscores.',
        });
        return;
      }

      if (mongoose.connection.readyState === 1) {
        const existing = await UserModel.findOne({
          username: cleanUsername,
          _id: { $ne: req.user.id },
        });
        if (existing) {
          res.status(409).json({ success: false, error: 'Username is already taken' });
          return;
        }
      }
      updateFields.username = cleanUsername;
    }

    if (displayName) {
      const trimmed = displayName.trim();
      if (trimmed.length > 60) {
        res.status(400).json({ success: false, error: 'Display name cannot exceed 60 characters' });
        return;
      }
      updateFields.displayName = trimmed;
      updateFields.avatarLetter = trimmed.charAt(0).toUpperCase();
    }
    if (dateOfBirth !== undefined) {
      const trimmed = String(dateOfBirth).trim();
      if (trimmed.length > 60) {
        res.status(400).json({ success: false, error: 'Date of birth cannot exceed 60 characters' });
        return;
      }
      updateFields.dateOfBirth = trimmed;
    }
    if (gender !== undefined) {
      const trimmed = String(gender).trim();
      if (trimmed.length > 60) {
        res.status(400).json({ success: false, error: 'Gender cannot exceed 60 characters' });
        return;
      }
      updateFields.gender = trimmed;
    }
    if (bio !== undefined) updateFields.bio = String(bio).slice(0, 500);
    if (status) {
      const tier = req.user.tier || 'free';
      if (status === 'offline' && tier === 'free') {
        res.status(403).json({ success: false, error: 'Offline status requires Premium.' });
        return;
      }
      if (status === 'custom' && tier !== 'premium_plus') {
        res.status(403).json({ success: false, error: 'Custom status requires Premium Plus.' });
        return;
      }
      updateFields.status = status;
    }
    if (customStatus !== undefined) {
      if ((req.user.tier || 'free') !== 'premium_plus') {
        res.status(403).json({ success: false, error: 'Custom status requires Premium Plus.' });
        return;
      }
      updateFields.customStatus = String(customStatus).trim().slice(0, 80);
    }
    if (bannerIndex !== undefined) updateFields.bannerIndex = bannerIndex;
    if (bannerUrl !== undefined) {
      updateFields.bannerUrl = bannerUrl;
    }
    if (interests) updateFields.interests = interests.slice(0, 5);
    if (avatarUrl !== undefined) updateFields.avatarUrl = avatarUrl;
    if (isFriendCountPublic !== undefined) updateFields.isFriendCountPublic = isFriendCountPublic;
    if (isOnboardingCompleted !== undefined) {
      updateFields.isOnboardingCompleted = Boolean(isOnboardingCompleted);
    } else {
      updateFields.isOnboardingCompleted = true;
    }

    if (mongoose.connection.readyState === 1) {
      const updated = await UserModel.findByIdAndUpdate(req.user.id, updateFields, { new: true });
      if (!updated) {
        res.status(404).json({ success: false, error: 'User not found' });
        return;
      }
      if (['displayName', 'avatarLetter', 'avatarUrl', 'bannerUrl', 'bio', 'status', 'interests', 'username'].some((key) => key in updateFields)) {
        const activeRooms = await RoomModel.find({ 'participants.userId': req.user.id }).select('_id');
        await RoomModel.updateMany({ 'participants.userId': req.user.id }, {
          $set: {
            'participants.$.username': updated.username,
            'participants.$.displayName': updated.displayName,
            'participants.$.avatarLetter': updated.avatarLetter,
            'participants.$.avatarUrl': updated.avatarUrl,
            'participants.$.bannerUrl': updated.bannerUrl,
            'participants.$.bio': updated.bio,
            'participants.$.status': updated.status,
            'participants.$.interests': updated.interests,
          },
        });
        notifyRoomsParticipantProfileUpdated(activeRooms.map((room) => room._id.toString()), req.user.id);
      }
      res.json({ success: true, data: toPrivateUserDTO(updated) });
    } else {
      res.status(503).json({ success: false, error: 'Database unavailable' });
    }
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to update profile' });
  }
}

export async function checkUsername(req: Request, res: Response): Promise<void> {
  try {
    const rawUsername = (req.params.username || '').toLowerCase().trim().replace(/^@/, '');
    if (!rawUsername || rawUsername.length < 3 || rawUsername.length > 16) {
      res.status(400).json({ success: false, error: 'Username must be between 3 and 16 characters' });
      return;
    }

    if (!/^[a-z0-9](?:[a-z0-9_]{1,14}[a-z0-9])?$/.test(rawUsername)) {
      res.status(400).json({ success: false, error: 'Username must use lowercase letters, numbers, and underscores, without leading or trailing underscores' });
      return;
    }

    const cleanUsername = rawUsername;
    const existing = await UserModel.findOne({
      username: cleanUsername,
      ...(req.user ? { _id: { $ne: req.user.id } } : {}),
    }).select('_id');

    res.json({
      success: true,
      data: {
        username: cleanUsername,
        isAvailable: !existing,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to check username availability' });
  }
}

export async function getPublicProfile(req: Request, res: Response): Promise<void> {
  try {
    const rawUsername = req.params.username;
    if (!rawUsername) {
      res.status(400).json({ success: false, error: 'Username is required' });
      return;
    }

    const cleanUsername = rawUsername.toLowerCase().trim().replace(/[^a-z0-9_]/g, '');
    const user = await UserModel.findOne({ username: cleanUsername });
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }

    if (req.user && req.user.id !== user._id.toString()) {
      const blocked = await FriendshipModel.exists({
        status: 'blocked',
        $or: [
          { requesterId: req.user.id, recipientId: user._id.toString() },
          { requesterId: user._id.toString(), recipientId: req.user.id },
        ],
      });
      if (blocked) {
        res.status(404).json({ success: false, error: 'User not found' });
        return;
      }
    }

    // Determine relationship context
    let relationship = 'none';
    if (req.user && req.user.id !== user._id.toString()) {
      const friendship = await FriendshipModel.findOne({
        $or: [
          { requesterId: req.user.id, recipientId: user._id.toString() },
          { requesterId: user._id.toString(), recipientId: req.user.id },
        ],
      });
      if (friendship) {
        if (friendship.status === 'accepted') relationship = 'friends';
        else if (friendship.requesterId === req.user.id) relationship = 'request_sent';
        else relationship = 'request_received';
      }
    } else if (req.user && req.user.id === user._id.toString()) {
      relationship = 'self';
    }

    // Return sanitized public DTO (zero sensitive data leaked)
    const profile = toPublicUserDTO(user, relationship);
    if (req.user && req.user.id !== user._id.toString() && relationship === 'request_received') {
      const incoming = await FriendshipModel.findOne({ requesterId: user._id.toString(), recipientId: req.user.id, status: 'pending' }).select('_id');
      profile.friendRequestId = incoming?._id.toString();
    }
    if (user.isFriendCountPublic) {
      profile.stats.friendsCount = await FriendshipModel.countDocuments({
        status: 'accepted',
        $or: [
          { requesterId: user._id.toString() },
          { recipientId: user._id.toString() },
        ],
      });
    }
    res.json({ success: true, data: profile });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve public profile' });
  }
}

export async function getPublicProfileById(req: Request, res: Response): Promise<void> {
  const userId = req.params.id;
  if (!mongoose.isValidObjectId(userId)) { res.status(404).json({ success: false, error: 'User not found' }); return; }
  try {
    const user = await UserModel.findById(userId);
    if (!user) {
      res.status(404).json({ success: false, error: 'User not found' });
      return;
    }
    let relationship = 'none';
    if (req.user) {
      if (req.user.id === userId) relationship = 'self';
      else {
        const friendship = await FriendshipModel.findOne({ $or: [{ requesterId: req.user.id, recipientId: userId }, { requesterId: userId, recipientId: req.user.id }] });
        if (friendship?.status === 'accepted') relationship = 'friends';
        else if (friendship?.status === 'pending') relationship = friendship.requesterId === req.user.id ? 'request_sent' : 'request_received';
        const blocked = await FriendshipModel.exists({ status: 'blocked', $or: [{ requesterId: req.user.id, recipientId: userId }, { requesterId: userId, recipientId: req.user.id }] });
        if (blocked) { res.status(404).json({ success: false, error: 'User not found' }); return; }
      }
    }
    const profile = toPublicUserDTO(user, relationship);
    if (relationship === 'request_received' && req.user) {
      const incoming = await FriendshipModel.findOne({ requesterId: userId, recipientId: req.user.id, status: 'pending' }).select('_id');
      profile.friendRequestId = incoming?._id.toString();
    }
    if (user.isFriendCountPublic) profile.stats.friendsCount = await FriendshipModel.countDocuments({ status: 'accepted', $or: [{ requesterId: userId }, { recipientId: userId }] });
    res.json({ success: true, data: profile });
  } catch {
    res.status(500).json({ success: false, error: 'Failed to retrieve public profile' });
  }
}
