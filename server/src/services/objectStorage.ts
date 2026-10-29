import crypto from 'crypto';
import { DeleteObjectsCommand, GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { config } from '../config/env';

const client = new S3Client({
  endpoint: config.storage.endpoint,
  region: config.storage.region,
  forcePathStyle: true,
  credentials: {
    accessKeyId: config.storage.accessKey,
    secretAccessKey: config.storage.secretKey,
  },
});

function decodeDataUrl(value: string): { body: Buffer; contentType: string } {
  const match = value.match(/^data:([^;,]+);base64,(.+)$/s);
  if (!match) {
    throw new Error('Only base64 data URLs are accepted for media upload');
  }
  return { contentType: match[1], body: Buffer.from(match[2], 'base64') };
}

export async function uploadObject(
  dataUrl: string,
  key: string,
  contentType: string
): Promise<string> {
  const decoded = decodeDataUrl(dataUrl);
  const resolvedType = contentType || decoded.contentType;
  await client.send(new PutObjectCommand({
    Bucket: config.storage.bucket,
    Key: key,
    Body: decoded.body,
    ContentType: resolvedType,
  }));

  if (config.storage.publicBaseUrl) {
    return `${config.storage.publicBaseUrl.replace(/\/$/, '')}/${key.split('/').map(encodeURIComponent).join('/')}`;
  }
  return createPrivateObjectUrl(key);
}

export function createPrivateObjectUrl(key: string): string {
  const encodedKey = Buffer.from(key, 'utf8').toString('base64url');
  const token = crypto.createHmac('sha256', config.auth.jwtSecret).update(key).digest('base64url');
  const origin = config.server.apiBaseUrl.replace(/\/api\/v1\/?$/, '').replace(/\/$/, '');
  return `${origin}/storage/${encodedKey}?token=${token}`;
}

/** Repair legacy local-origin references while keeping real provider URLs untouched. */
export function resolveStoredObjectUrl(value?: string): string | undefined {
  if (!value) return value;
  try {
    const parsed = new URL(value);
    if (!['localhost', '127.0.0.1', '0.0.0.0', '10.0.2.2'].includes(parsed.hostname)) return value;
    const origin = config.server.apiBaseUrl.replace(/\/api\/v1\/?$/, '').replace(/\/$/, '');
    return `${origin}${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return value;
  }
}

/**
 * Recovers the storage key from a stored asset URL. Uploads are referenced by URL,
 * so account deletion has to reverse that to know which objects to remove.
 */
export function objectKeyFromAssetUrl(url?: string): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    if (parsed.pathname.startsWith('/storage/')) {
      const encoded = parsed.pathname.slice('/storage/'.length).split('/')[0];
      return encoded ? Buffer.from(encoded, 'base64url').toString('utf8') : null;
    }
    if (config.storage.publicBaseUrl && url.startsWith(config.storage.publicBaseUrl.replace(/\/$/, ''))) {
      const relative = parsed.pathname.replace(/^\//, '');
      return relative ? decodeURIComponent(relative) : null;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Best-effort object removal. A storage outage must never leave a deleted account
 * half-purged in the database, so failures are reported and left for retry.
 */
export async function deleteObjects(keys: string[]): Promise<{ deleted: number; failed: string[] }> {
  const unique = [...new Set(keys.filter(Boolean))];
  if (unique.length === 0) return { deleted: 0, failed: [] };
  if (!config.storage.bucket) return { deleted: 0, failed: unique };
  let deleted = 0;
  const failed: string[] = [];
  for (let index = 0; index < unique.length; index += 1000) {
    const batch = unique.slice(index, index + 1000);
    try {
      await client.send(new DeleteObjectsCommand({
        Bucket: config.storage.bucket,
        Delete: { Objects: batch.map((Key) => ({ Key })), Quiet: true },
      }));
      deleted += batch.length;
    } catch {
      failed.push(...batch);
    }
  }
  return { deleted, failed };
}

export async function getPrivateObjectRedirect(key: string): Promise<string> {
  // Least-privilege short duration window: 15 minutes (900 seconds)
  return getSignedUrl(
    client,
    new GetObjectCommand({ Bucket: config.storage.bucket, Key: key }),
    { expiresIn: 900 }
  );
}
