import { extractYoutubeId } from '../modules/media/mediaController';

/**
 * Server-side playability verification for room media links.
 *
 * Rooms may only be pointed at a source that actually plays: a public YouTube
 * video, a public Vimeo video, or a direct media file that answers with a video
 * content type. Image pages, HTML pages, and unavailable links are rejected with
 * one clear line so the client can show it verbatim.
 */

export interface PlayableLink {
  kind: 'youtube' | 'vimeo' | 'direct';
  sourceUrl: string;
  embedUrl: string;
  title: string;
  thumbnailUrl?: string;
  mimeType: string;
}

export class LinkRejectedError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

const DIRECT_VIDEO_EXTENSIONS = ['.mp4', '.webm', '.m3u8', '.mov', '.m4v'];
const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.avif', '.heic'];

export function extractVimeoId(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input
    .trim()
    .match(/vimeo\.com\/(?:video\/)?(\d{6,12})(?:$|[/?#])|player\.vimeo\.com\/video\/(\d{6,12})/i);
  return match ? match[1] || match[2] : null;
}

function extensionOf(pathname: string): string {
  const lower = pathname.toLowerCase();
  const dot = lower.lastIndexOf('.');
  return dot === -1 ? '' : lower.slice(dot);
}

function normalizeInput(input: string): URL {
  const raw = input.trim();
  const candidate = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
  try {
    const url = new URL(candidate);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw new Error('bad protocol');
    }
    return url;
  } catch {
    throw new LinkRejectedError('INVALID_LINK', 'That is not a valid video link.');
  }
}

async function verifyDirectVideo(url: URL): Promise<PlayableLink> {
  const extension = extensionOf(url.pathname);
  if (IMAGE_EXTENSIONS.includes(extension)) {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link points to an image, not a playable video.');
  }

  const response = await fetch(url.toString(), { method: 'GET', headers: { Range: 'bytes=0-1' } });
  if (response.status === 404 || response.status === 410) {
    throw new LinkRejectedError('UNAVAILABLE', 'That video link is unavailable.');
  }
  if (!response.ok && response.status !== 206) {
    throw new LinkRejectedError('UNAVAILABLE', 'That video link is unavailable.');
  }

  const contentType = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
  if (!contentType.startsWith('video/') && contentType !== 'application/vnd.apple.mpegurl') {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link is not a playable video source.');
  }
  if (DIRECT_VIDEO_EXTENSIONS.length && !DIRECT_VIDEO_EXTENSIONS.includes(extension) && contentType !== 'application/vnd.apple.mpegurl') {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link is not a supported video file.');
  }

  return {
    kind: 'direct',
    sourceUrl: url.toString(),
    embedUrl: url.toString(),
    title: decodeURIComponent(url.pathname.split('/').filter(Boolean).pop() || 'Video'),
    mimeType: contentType,
  };
}

async function verifyVimeo(videoId: string): Promise<PlayableLink> {
  const oembedUrl = `https://vimeo.com/api/oembed.json?url=${encodeURIComponent(`https://vimeo.com/${videoId}`)}`;
  const response = await fetch(oembedUrl);
  if (!response.ok) {
    throw new LinkRejectedError('UNAVAILABLE', 'That Vimeo video is private or unavailable.');
  }
  const data = (await response.json()) as any;
  if (data?.type && data.type !== 'video') {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That Vimeo link is not a playable video.');
  }
  if (data?.is_playable === false) {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That Vimeo video cannot be embedded.');
  }
  return {
    kind: 'vimeo',
    sourceUrl: `https://vimeo.com/${videoId}`,
    embedUrl: `https://player.vimeo.com/video/${videoId}`,
    title: data?.title || 'Vimeo Video',
    thumbnailUrl: data?.thumbnail_url,
    mimeType: 'video/vimeo',
  };
}

async function verifyYoutube(input: string): Promise<PlayableLink> {
  const videoId = extractYoutubeId(input);
  if (!videoId) throw new LinkRejectedError('INVALID_LINK', 'That is not a valid YouTube video link.');
  const oembedRes = await fetch(`https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`);
  if (!oembedRes.ok) {
    throw new LinkRejectedError('UNAVAILABLE', 'That video is private, removed, or unavailable.');
  }
  const data = (await oembedRes.json()) as any;
  return {
    kind: 'youtube',
    sourceUrl: `https://www.youtube.com/watch?v=${videoId}`,
    embedUrl: `https://www.youtube.com/embed/${videoId}`,
    title: data?.title || 'YouTube Video',
    thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
    mimeType: 'video/youtube',
  };
}

/** Resolve any link to a verified playable source or throw LinkRejectedError. */
export async function verifyPlayableLink(input: string): Promise<PlayableLink> {
  if (!input || typeof input !== 'string' || !input.trim()) {
    throw new LinkRejectedError('INVALID_LINK', 'Enter a video link to play in this room.');
  }

  const url = normalizeInput(input);
  const host = url.hostname.toLowerCase().replace(/^www\./, '');

  if (host === 'youtu.be' || host === 'youtube.com' || host === 'm.youtube.com' || host === 'music.youtube.com') {
    return verifyYoutube(input);
  }
  if (host === 'vimeo.com' || host === 'player.vimeo.com') {
    const videoId = extractVimeoId(input);
    if (!videoId) throw new LinkRejectedError('NOT_A_VIDEO', 'That Vimeo link is not a playable video.');
    return verifyVimeo(videoId);
  }
  if (host === 'drive.google.com' || host === 'docs.google.com') {
    throw new LinkRejectedError(
      'UNSUPPORTED_SOURCE',
      'Google Drive links cannot be played in rooms. Use YouTube, Vimeo, or a direct video file.'
    );
  }

  if (host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0' || host === '10.0.2.2') {
    throw new LinkRejectedError('UNSUPPORTED_SOURCE', 'That host cannot be played in a room.');
  }

  return verifyDirectVideo(url);
}

/** Non-throwing wrapper so controllers can return a single-line error message. */
export async function tryVerifyPlayableLink(
  input: string
): Promise<{ ok: true; link: PlayableLink } | { ok: false; code: string; error: string }> {
  try {
    return { ok: true, link: await verifyPlayableLink(input) };
  } catch (err: any) {
    if (err instanceof LinkRejectedError) return { ok: false, code: err.code, error: err.message };
    return { ok: false, code: 'UNAVAILABLE', error: 'That video link could not be verified.' };
  }
}
