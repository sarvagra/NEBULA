import crypto from 'crypto';
import { Request, Response } from 'express';
import { config } from '../../config/env';
import { getPrivateObjectRedirect } from '../../services/objectStorage';

export async function redirectToObject(req: Request, res: Response): Promise<void> {
  const encodedKey = req.params.encodedKey;
  const token = typeof req.query.token === 'string' ? req.query.token : '';
  let key: string;
  try {
    key = Buffer.from(encodedKey, 'base64url').toString('utf8');
  } catch {
    res.status(400).json({ success: false, error: 'Invalid media reference' });
    return;
  }

  const expected = crypto.createHmac('sha256', config.auth.jwtSecret).update(key).digest('base64url');
  const tokenBuffer = Buffer.from(token);
  const expectedBuffer = Buffer.from(expected);
  if (!token || tokenBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(tokenBuffer, expectedBuffer)) {
    res.status(403).json({ success: false, error: 'Invalid media authorization' });
    return;
  }

  try {
    res.redirect(302, await getPrivateObjectRedirect(key));
  } catch {
    res.status(404).json({ success: false, error: 'Media object unavailable' });
  }
}
