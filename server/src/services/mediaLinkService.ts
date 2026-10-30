import { extractYoutubeId } from '../modules/media/mediaController';

/**
 * Server-side playability verification for room media links.
 *
 * A room may be pointed at anything that actually plays:
 *
 *  - a public YouTube video (played by the in-app YouTube surface, which is the only
 *    surface with full host/guest clock control);
 *  - a public Vimeo, Dailymotion, xHamster or Google Drive video, which is normalised
 *    to that host's embeddable player URL;
 *  - a Prime Video title page (the viewer signs in inside the room's web surface);
 *  - a direct media file that answers with a video content type;
 *  - any other http(s) page, which plays in the in-room web surface.
 *
 * Image files and unavailable direct files are rejected with one clear line so the
 * client can show it verbatim.
 */

export type PlayableKind = 'youtube' | 'vimeo' | 'direct' | 'web';

export interface PlayableLink {
  kind: PlayableKind;
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

const DIRECT_VIDEO_EXTENSIONS = ['.mp4', '.webm', '.m3u8', '.mov', '.m4v', '.mkv', '.ts'];
const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.avif', '.heic'];

/** Web fetch budget. A slow page must not hold a room-create request open. */
const FETCH_TIMEOUT_MS = 5000;

export function extractVimeoId(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input
    .trim()
    .match(/vimeo\.com\/(?:video\/)?(\d{6,12})(?:$|[/?#])|player\.vimeo\.com\/video\/(\d{6,12})/i);
  return match ? match[1] || match[2] : null;
}

export function extractDailymotionId(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input.trim().match(/dai\.ly\/([a-zA-Z0-9]+)|dailymotion\.com\/(?:video|embed\/video)\/([a-zA-Z0-9]+)/i);
  return match ? match[1] || match[2] : null;
}

export function extractDriveFileId(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input
    .trim()
    .match(/drive\.google\.com\/(?:file\/d\/|open\?id=|uc\?id=)([\w-]{10,})/i);
  return match ? match[1] : null;
}

export function extractXhamsterKey(input: string): string | null {
  if (!input || typeof input !== 'string') return null;
  const match = input.trim().match(/xhamster\.com\/(?:videos|embed)\/([\w-]+)/i);
  return match ? match[1] : null;
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

function titleFromPath(url: URL): string {
  const last = url.pathname.split('/').filter(Boolean).pop() || 'Video';
  const cleaned = decodeURIComponent(last).replace(/\.[a-z0-9]{2,5}$/i, '').replace(/[-_+]/g, ' ').trim();
  return cleaned.slice(0, 120) || 'Video';
}

/** Best-effort page title so the room header names the source instead of a URL. */
async function fetchPageTitle(url: URL): Promise<string | undefined> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(url.toString(), {
      redirect: 'follow',
      signal: controller.signal,
      headers: { 'User-Agent': 'Mozilla/5.0 (Linux; Android 14) Nebula/1.0' },
    });
    if (!response.ok) return undefined;
    const html = (await response.text()).slice(0, 200_000);
    const og = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i);
    if (og?.[1]) return og[1].trim().slice(0, 120);
    const title = html.match(/<title[^>]*>([^<]+)<\/title>/i);
    if (title?.[1]) return title[1].trim().slice(0, 120);
    return undefined;
  } catch {
    return undefined;
  } finally {
    clearTimeout(timer);
  }
}

async function verifyDirectVideo(url: URL): Promise<PlayableLink> {
  const extension = extensionOf(url.pathname);
  if (IMAGE_EXTENSIONS.includes(extension)) {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link points to an image, not a playable video.');
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(url.toString(), { method: 'GET', headers: { Range: 'bytes=0-1' }, signal: controller.signal });
  } catch {
    // A file that only fails from this server (blocked egress, referer rules) is
    // still worth playing on the device, which has its own network path.
    return {
      kind: 'direct',
      sourceUrl: url.toString(),
      embedUrl: url.toString(),
      title: titleFromPath(url),
      mimeType: 'video/*',
    };
  } finally {
    clearTimeout(timer);
  }

  if (response.status === 404 || response.status === 410) {
    throw new LinkRejectedError('UNAVAILABLE', 'That video link is unavailable.');
  }
  if (!response.ok && response.status !== 206) {
    throw new LinkRejectedError('UNAVAILABLE', 'That video link is unavailable.');
  }

  const contentType = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
  const isHls = contentType === 'application/vnd.apple.mpegurl' || contentType === 'application/x-mpegurl';
  if (!contentType.startsWith('video/') && !isHls && contentType !== 'application/octet-stream') {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link is not a playable video source.');
  }
  if (!isHls && DIRECT_VIDEO_EXTENSIONS.length && !DIRECT_VIDEO_EXTENSIONS.includes(extension) && contentType !== 'application/octet-stream') {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link is not a supported video file.');
  }

  return {
    kind: 'direct',
    sourceUrl: url.toString(),
    embedUrl: url.toString(),
    title: titleFromPath(url),
    mimeType: contentType,
  };
}

async function verifyVimeo(videoId: string): Promise<PlayableLink> {
  const oembedUrl = `https://vimeo.com/api/oembed.json?url=${encodeURIComponent(`https://vimeo.com/${videoId}`)}`;
  try {
    const response = await fetch(oembedUrl);
    if (!response.ok) {
      throw new LinkRejectedError('UNAVAILABLE', 'That Vimeo video is private or unavailable.');
    }
    const data = (await response.json()) as any;
    if (data?.type && data.type !== 'video') {
      throw new LinkRejectedError('NOT_A_VIDEO', 'That Vimeo link is not a playable video.');
    }
    return {
      kind: 'vimeo',
      sourceUrl: `https://player.vimeo.com/video/${videoId}`,
      embedUrl: `https://player.vimeo.com/video/${videoId}`,
      title: data?.title || 'Vimeo video',
      thumbnailUrl: data?.thumbnail_url,
      mimeType: 'video/vimeo',
    };
  } catch (err: any) {
    if (err instanceof LinkRejectedError) throw err;
    // oembed blocked from this server: the embed player still plays the id.
    return {
      kind: 'vimeo',
      sourceUrl: `https://player.vimeo.com/video/${videoId}`,
      embedUrl: `https://player.vimeo.com/video/${videoId}`,
      title: 'Vimeo video',
      mimeType: 'video/vimeo',
    };
  }
}

async function verifyYoutube(input: string): Promise<PlayableLink> {
  const videoId = extractYoutubeId(input);
  if (!videoId) throw new LinkRejectedError('INVALID_LINK', 'That is not a valid YouTube video link.');
  try {
    const oembedRes = await fetch(`https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`);
    if (!oembedRes.ok) {
      throw new LinkRejectedError('UNAVAILABLE', 'That video is private, removed, or unavailable.');
    }
    const data = (await oembedRes.json()) as any;
    return {
      kind: 'youtube',
      sourceUrl: `https://www.youtube.com/watch?v=${videoId}`,
      embedUrl: `https://www.youtube.com/embed/${videoId}`,
      title: data?.title || 'YouTube video',
      thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
      mimeType: 'video/youtube',
    };
  } catch (err: any) {
    if (err instanceof LinkRejectedError) throw err;
    return {
      kind: 'youtube',
      sourceUrl: `https://www.youtube.com/watch?v=${videoId}`,
      embedUrl: `https://www.youtube.com/embed/${videoId}`,
      title: 'YouTube video',
      thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
      mimeType: 'video/youtube',
    };
  }
}

/** A web page (any host) that plays in the room's in-app web surface. */
async function verifyWebPage(url: URL, embedUrl?: string): Promise<PlayableLink> {
  const title = (await fetchPageTitle(url)) || titleFromPath(url);
  return {
    kind: 'web',
    sourceUrl: url.toString(),
    embedUrl: embedUrl || url.toString(),
    title,
    mimeType: 'text/html',
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
    if (!videoId) {
      // vimeo.com/<user>/<name> style links still open as a web page.
      return verifyWebPage(url);
    }
    return verifyVimeo(videoId);
  }

  if (host === 'dai.ly' || host.endsWith('dailymotion.com')) {
    const id = extractDailymotionId(input);
    if (id) return verifyWebPage(url, `https://geo.dailymotion.com/embed/video/${id}`);
    return verifyWebPage(url);
  }

  if (host === 'xhamster.com' || host.endsWith('.xhamster.com')) {
    const key = extractXhamsterKey(input);
    const embed = key ? `https://xhamster.com/xembed.php?video=${key}` : undefined;
    return verifyWebPage(url, embed);
  }

  if (host === 'drive.google.com' || host === 'docs.google.com') {
    const fileId = extractDriveFileId(input);
    const embed = fileId ? `https://drive.google.com/file/d/${fileId}/preview` : undefined;
    return verifyWebPage(url, embed);
  }

  if (host === 'primevideo.com' || host.endsWith('.primevideo.com') || host === 'amazon.com' || host.endsWith('.amazon.com')) {
    return verifyWebPage(url);
  }

  if (host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0' || host === '10.0.2.2') {
    throw new LinkRejectedError('UNSUPPORTED_SOURCE', 'That host cannot be played in a room.');
  }

  const extension = extensionOf(url.pathname);
  if (IMAGE_EXTENSIONS.includes(extension)) {
    throw new LinkRejectedError('NOT_A_VIDEO', 'That link points to an image, not a playable video.');
  }

  // A path that names a media file is treated as a stream; every other page plays
  // in the web surface instead of being rejected for not being one of three hosts.
  if (DIRECT_VIDEO_EXTENSIONS.includes(extension)) {
    return verifyDirectVideo(url);
  }

  return verifyWebPage(url);
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
