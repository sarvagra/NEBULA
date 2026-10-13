import { Request, Response } from 'express';
import { config } from '../../config/env';
import { MediaHistoryModel, MediaSourceType } from '../../models/MediaHistory';

// Helper regex to extract 11-character YouTube video ID
const YOUTUBE_ID_REGEX = /(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|watch\?.+&v=))([a-zA-Z0-9_-]{11})|^([a-zA-Z0-9_-]{11})$/;

export function extractYoutubeId(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input.trim().match(YOUTUBE_ID_REGEX);
  return match ? (match[1] || match[2]) : null;
}

export async function searchYoutube(req: Request, res: Response): Promise<void> {
  try {
    const query = typeof req.query.q === 'string' ? req.query.q.trim() : '';
    const maxResults = Math.min(Math.max(parseInt(req.query.maxResults as string, 10) || 10, 1), 25);

    if (!query) {
      res.status(400).json({ success: false, error: 'Query parameter q is required' });
      return;
    }

    const apiKey = config.media.youtubeApiKey;
    if (!apiKey) {
      res.status(503).json({
        success: false,
        error: 'YouTube search is unavailable until YOUTUBE_API_KEY is configured',
        results: [],
      });
      return;
    }

    const apiUrl = `https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=${encodeURIComponent(
      query
    )}&maxResults=${maxResults}&key=${apiKey}`;

    const apiRes = await fetch(apiUrl);
    const data = (await apiRes.json()) as any;

    if (!apiRes.ok) {
      res.status(apiRes.status === 429 ? 429 : 502).json({
        success: false,
        error: 'YouTube search request failed',
        results: [],
      });
      return;
    }

    const results = (data.items || []).map((item: any) => ({
      videoId: item.id?.videoId,
      title: item.snippet?.title,
      channel: item.snippet?.channelTitle,
      thumbnailUrl: item.snippet?.thumbnails?.high?.url || item.snippet?.thumbnails?.default?.url,
      publishedAt: item.snippet?.publishedAt,
    }));

    res.json({ success: true, results });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Internal error searching YouTube', results: [] });
  }
}

export async function resolveYoutube(req: Request, res: Response): Promise<void> {
  try {
    const input = (typeof req.query.url === 'string' ? req.query.url : req.query.videoId) as string;
    if (!input) {
      res.status(400).json({ success: false, error: 'url or videoId parameter is required' });
      return;
    }

    const videoId = extractYoutubeId(input);
    if (!videoId) {
      res.status(400).json({ success: false, error: 'Invalid YouTube URL or video ID format' });
      return;
    }

    const oembedUrl = `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`;
    const oembedRes = await fetch(oembedUrl);

    if (!oembedRes.ok) {
      res.status(oembedRes.status === 404 ? 404 : 422).json({
        success: false,
        code: 'VIDEO_NOT_EMBEDDABLE',
        error: 'Video is private, removed, or embedding is restricted by owner',
        isEmbeddable: false,
      });
      return;
    }

    const oembedData = (await oembedRes.json()) as any;

    res.json({
      success: true,
      data: {
        videoId,
        title: oembedData.title || 'YouTube Video',
        channel: oembedData.author_name || 'YouTube Creator',
        authorUrl: oembedData.author_url || '',
        thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
        embedUrl: `https://www.youtube.com/embed/${videoId}`,
        sourceUrl: `https://www.youtube.com/watch?v=${videoId}`,
        isEmbeddable: true,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to resolve YouTube video metadata' });
  }
}

export async function getMediaHistory(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const history = await MediaHistoryModel.findOne({ userId: req.user.id });
    if (!history) {
      res.json({ success: true, data: [] });
      return;
    }

    // Return items sorted by most recent
    const sorted = [...history.items].sort(
      (a, b) => new Date(b.selectedAt).getTime() - new Date(a.selectedAt).getTime()
    );

    res.json({ success: true, data: sorted.slice(0, 15) });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to retrieve media history' });
  }
}

export async function addMediaHistory(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const { mediaId, title, channel, thumbnailUrl, sourceUrl, sourceType } = req.body;
    if (!mediaId || !title || !sourceType) {
      res.status(400).json({ success: false, error: 'mediaId, title, and sourceType are required' });
      return;
    }

    const validSourceTypes: MediaSourceType[] = ['youtube_video', 'youtube_music', 'public_link'];
    if (!validSourceTypes.includes(sourceType)) {
      res.status(400).json({ success: false, error: 'Invalid sourceType' });
      return;
    }

    let history = await MediaHistoryModel.findOne({ userId: req.user.id });
    if (!history) {
      history = new MediaHistoryModel({
        userId: req.user.id,
        items: [],
      });
    }

    // Filter out duplicate mediaId if present to bring it to the top
    history.items = history.items.filter((item) => item.mediaId !== mediaId);

    // Keep max 5 per source type
    const sourceItems = history.items.filter((item) => item.sourceType === sourceType);
    if (sourceItems.length >= 5) {
      // Find oldest of this type and remove
      const oldestIndex = history.items.findIndex((item) => item.sourceType === sourceType);
      if (oldestIndex !== -1) {
        history.items.splice(oldestIndex, 1);
      }
    }

    history.items.push({
      mediaId: String(mediaId).trim(),
      title: String(title).slice(0, 150).trim(),
      channel: channel ? String(channel).slice(0, 100).trim() : '',
      thumbnailUrl: thumbnailUrl ? String(thumbnailUrl).slice(0, 500).trim() : '',
      sourceUrl: sourceUrl ? String(sourceUrl).slice(0, 500).trim() : '',
      sourceType,
      selectedAt: new Date(),
    });

    await history.save();
    res.status(201).json({ success: true, data: history.items });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to record media history' });
  }
}

export async function deleteMediaHistory(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'Authentication required' });
      return;
    }

    const mediaId = typeof req.query.mediaId === 'string' ? req.query.mediaId.trim() : null;

    if (mediaId) {
      await MediaHistoryModel.findOneAndUpdate(
        { userId: req.user.id },
        { $pull: { items: { mediaId } } }
      );
    } else {
      await MediaHistoryModel.findOneAndUpdate(
        { userId: req.user.id },
        { $set: { items: [] } }
      );
    }

    res.json({ success: true, message: 'Media history updated' });
  } catch (err: any) {
    res.status(500).json({ success: false, error: 'Failed to delete media history' });
  }
}
