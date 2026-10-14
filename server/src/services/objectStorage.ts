import crypto from 'crypto';
import { GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
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

export async function getPrivateObjectRedirect(key: string): Promise<string> {
  return getSignedUrl(
    client,
    new GetObjectCommand({ Bucket: config.storage.bucket, Key: key }),
    { expiresIn: 3600 }
  );
}
