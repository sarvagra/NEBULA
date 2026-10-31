import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { config } from '../config/env';

// Single source of truth: the secret is configured in one place so the client
// attestation check and the test harness can never drift apart.
export const NEBULA_APP_CLIENT_SECRET = config.clientAttestation.secret;

/**
 * Middleware that strictly verifies requests originate from the official Nebula Android app.
 * Direct access via curl, Postman, web browsers, or automated bots without the cryptographic
 * app signature is rejected immediately with 403 Forbidden.
 */
export function clientAttestationMiddleware(req: Request, res: Response, next: NextFunction): void {
  // Allow health check probes and public app update manifest checks
  if (req.path === '/health' || req.path === '/ready' || req.path === '/app/version' || req.path === '/version.json') {
    next();
    return;
  }

  const enforcement = config.clientAttestation?.enforcement || (process.env.NODE_ENV === 'test' ? 'off' : 'enforce');

  if (enforcement === 'off') {
    next();
    return;
  }

  const signature = req.header('X-Nebula-App-Signature');
  const timestampHeader = req.header('X-Nebula-Timestamp');
  const clientHeader = req.header('X-Nebula-Client');

  if (!signature || !timestampHeader || !clientHeader) {
    if (enforcement === 'enforce') {
      res.status(403).json({
        success: false,
        error: 'Access denied: Requests must originate exclusively from the official Nebula mobile application.',
        code: 'APP_CLIENT_REQUIRED',
      });
      return;
    }
    next();
    return;
  }

  const timestamp = parseInt(timestampHeader, 10);
  if (isNaN(timestamp)) {
    res.status(403).json({
      success: false,
      error: 'Invalid client timestamp format.',
      code: 'APP_SIGNATURE_INVALID',
    });
    return;
  }

  // Strict replay attack prevention: signature is only valid for 2 minutes
  const now = Date.now();
  if (Math.abs(now - timestamp) > 120000) {
    res.status(403).json({
      success: false,
      error: 'Request timestamp expired or out of sync.',
      code: 'APP_SIGNATURE_EXPIRED',
    });
    return;
  }

  // Normalize path to match client signing format (subpath under /api/v1 or raw path)
  const normalizedPath = req.originalUrl.replace(/^\/api\/v1/, '').split('?')[0];
  const method = req.method.toUpperCase();

  const payload = `${timestamp}:${method}:${normalizedPath}`;
  const expectedSignature = crypto
    .createHmac('sha256', NEBULA_APP_CLIENT_SECRET)
    .update(payload)
    .digest('hex');

  // Constant-time comparison to prevent timing attacks
  const isMatch = signature.length === expectedSignature.length &&
    crypto.timingSafeEqual(Buffer.from(signature, 'hex'), Buffer.from(expectedSignature, 'hex'));

  if (!isMatch) {
    if (enforcement === 'enforce') {
      res.status(403).json({
        success: false,
        error: 'Access denied: Invalid app signature. Tampered or unauthorized client detected.',
        code: 'APP_SIGNATURE_INVALID',
      });
      return;
    }
  }

  next();
}
