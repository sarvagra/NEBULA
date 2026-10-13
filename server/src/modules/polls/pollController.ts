import { Request, Response } from 'express';
import { PollModel } from '../../models/Poll';
import { RoomModel } from '../../models/Room';

function pollDto(poll: any, userId?: string) {
  const options = poll.options.map((option: any) => ({
    id: option.id,
    text: option.text,
    votes: option.votes,
  }));
  const voted = userId
    ? poll.options.find((option: any) => option.voterUserIds.includes(userId))?.id || null
    : null;
  return {
    id: poll._id.toString(),
    roomId: poll.roomId,
    creatorName: poll.creatorName,
    question: poll.question,
    options,
    totalVotes: poll.totalVotes,
    isActive: poll.isActive,
    userVotedOptionId: voted,
    createdAt: poll.createdAt,
  };
}

export async function getRoomPoll(req: Request, res: Response): Promise<void> {
  try {
    const { roomId } = req.params;
    const room = await RoomModel.findById(roomId);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }
    if (room.privacy === 'invite_only' &&
        !room.participants.some((p) => p.userId === req.user?.id) && req.user?.role !== 'admin') {
      res.status(403).json({ success: false, error: 'This room is invite-only' });
      return;
    }
    const poll = await PollModel.findOne({ roomId, isActive: true }).sort({ createdAt: -1 });
    res.json({ success: true, data: poll ? pollDto(poll, req.user?.id) : null });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve active poll' });
  }
}

export async function createPoll(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { roomId, question, options } = req.body;
    const room = await RoomModel.findById(roomId);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }

    // AUTHORIZATION: Only room participants can create polls
    const isMember = room.participants.some((p) => p.userId === req.user!.id);
    if (!isMember && req.user.role !== 'admin') {
      res.status(403).json({ success: false, error: 'Forbidden: You must be in the room to create polls' });
      return;
    }

    // Deactivate previous active polls for this room
    await PollModel.updateMany({ roomId, isActive: true }, { isActive: false });

    const poll = await PollModel.create({
      roomId,
      creatorId: req.user.id,
      creatorName: req.user.displayName,
      question: question.trim(),
      options: options.map((opt: string, idx: number) => ({
        id: `opt_${idx + 1}`,
        text: opt.trim(),
        votes: 0,
        voterUserIds: [],
      })),
      isActive: true,
      totalVotes: 0,
    });

    res.status(201).json({ success: true, data: pollDto(poll, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to create poll' });
  }
}

export async function votePoll(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const { optionId } = req.body;

    const existingPoll = await PollModel.findById(id);
    if (!existingPoll || !existingPoll.isActive) {
      res.status(404).json({ success: false, error: 'Active poll not found' });
      return;
    }

    // Verify room membership before voting
    const room = await RoomModel.findById(existingPoll.roomId);
    if (!room) {
      res.status(404).json({ success: false, error: 'Room not found' });
      return;
    }
    if (!room.participants.some((p) => p.userId === req.user!.id)) {
      res.status(403).json({ success: false, error: 'Forbidden: You must be in the room to vote' });
      return;
    }

    if (!existingPoll.options.some((o) => o.id === optionId)) {
      res.status(404).json({ success: false, error: 'Selected poll option does not exist' });
      return;
    }

    // A conditional atomic update prevents two concurrent retries from both voting.
    const poll = await PollModel.findOneAndUpdate(
      {
        _id: id,
        isActive: true,
        $nor: [{ options: { $elemMatch: { voterUserIds: req.user.id } } }],
      },
      {
        $inc: { totalVotes: 1, 'options.$[selected].votes': 1 },
        $addToSet: { 'options.$[selected].voterUserIds': req.user.id },
      },
      { new: true, arrayFilters: [{ 'selected.id': optionId }] }
    );
    if (!poll) {
      res.status(409).json({ success: false, error: 'You have already voted on this poll' });
      return;
    }
    res.json({ success: true, data: pollDto(poll, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to record vote' });
  }
}

export async function closePoll(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { id } = req.params;
    const poll = await PollModel.findById(id);
    if (!poll) {
      res.status(404).json({ success: false, error: 'Poll not found' });
      return;
    }

    const room = await RoomModel.findById(poll.roomId);
    if (!room || (room.hostId !== req.user.id && poll.creatorId !== req.user.id && req.user.role !== 'admin')) {
      res.status(403).json({ success: false, error: 'Forbidden: Only the room host or poll creator can close this poll' });
      return;
    }

    poll.isActive = false;
    await poll.save();

    res.json({ success: true, message: 'Poll closed successfully', data: pollDto(poll, req.user.id) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to close poll' });
  }
}
