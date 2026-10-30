import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';

interface RateLimitRecord {
  timestamps: number[];
}

/**
 * Stable per-session identity taken from the bearer token.
 *
 * The global limiter runs before any route's auth middleware, so `req.user` is
 * still empty there and every request would otherwise be keyed by the caller's
 * network address. Mobile carriers put thousands of subscribers behind a handful
 * of CGNAT addresses, so at the scale this API targets a shared address would
 * exhaust one bucket and lock out every user on that carrier. Hashing the token
 * gives each signed-in session its own bucket without trusting the token (the
 * limiter never verifies or decodes it) and without storing the token itself as
 * a map key.
 */
function sessionIdentity(req: Request): string | undefined {
  const header = req.headers.authorization;
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) return undefined;
  const token = header.slice(7).trim();
  if (!token) return undefined;
  return 's_' + crypto.createHash('sha256').update(token).digest('hex').slice(0, 24);
}

export function createRateLimiter(options: {
  windowMs: number;
  max: number;
  prefix: string;
  message?: string;
  /**
   * Optional stable identifier derived from the request. Use it to scope a limit
   * to the account being acted on instead of the caller's network address, so a
   * shared NAT or a retry after a delivery failure cannot lock a user out.
   */
  keyResolver?: (req: Request) => string | undefined;
  /** Ceiling for requests that carry a session identity. Defaults to `max`. */
  sessionMax?: number;
}) {
  const store = new Map<string, RateLimitRecord>();

  // Cleanup expired entries every 60 seconds
  setInterval(() => {
    const now = Date.now();
    for (const [key, record] of store.entries()) {
      record.timestamps = record.timestamps.filter((ts) => now - ts < options.windowMs);
      if (record.timestamps.length === 0) {
        store.delete(key);
      }
    }
  }, 60000).unref();

  return (req: Request, res: Response, next: NextFunction): void => {
    const cfIp = req.headers['cf-connecting-ip'];
    const ip = (typeof cfIp === 'string' ? cfIp : Array.isArray(cfIp) ? cfIp[0] : null) ||
               req.ip ||
               req.socket.remoteAddress ||
               'unknown-ip';
    const userId = req.user?.id || '';
    const resolved = options.keyResolver?.(req)?.trim().toLowerCase();
    const session = userId ? `u_${userId}` : sessionIdentity(req);
    const key = `${options.prefix}:${resolved || session || ip}`;
    const limit = session ? (options.sessionMax ?? options.max) : options.max;

    const now = Date.now();
    let record = store.get(key);
    if (!record) {
      record = { timestamps: [] };
      store.set(key, record);
    }

    // Filter out timestamps outside window
    record.timestamps = record.timestamps.filter((ts) => now - ts < options.windowMs);

    if (record.timestamps.length >= limit) {
      const oldest = record.timestamps[0];
      const retryAfterSeconds = Math.ceil((options.windowMs - (now - oldest)) / 1000);
      res.setHeader('Retry-After', retryAfterSeconds.toString());
      res.status(429).json({
        success: false,
        error: options.message || 'Too many requests. Please try again later.',
        retryAfter: retryAfterSeconds,
      });
      return;
    }

    record.timestamps.push(now);
    next();
  };
}

// Pre-configured rate limiters

/**
 * Ceiling for the IP-wide limiter. The value only changes for the automated
 * suite, which issues a burst of deliberate 4xx probes from one address and would
 * otherwise start rate-limiting its own later assertions. Production keeps 120/min.
 */
const GLOBAL_LIMIT_MAX = Number(process.env.NEBULA_TEST_GLOBAL_LIMIT_MAX) || 120;

export const globalApiRateLimiter = createRateLimiter({
  prefix: 'global',
  windowMs: 60 * 1000, // 1 min
  // Anonymous traffic keeps the strict per-address ceiling (it is the only
  // signal available), while a signed-in session gets a roomier bucket because
  // it is already attributable to one account.
  max: GLOBAL_LIMIT_MAX,
  sessionMax: Number(process.env.NEBULA_SESSION_LIMIT_MAX) || 600,
  message: 'Global API rate limit exceeded.',
});

export const authRateLimiter = createRateLimiter({
  prefix: 'auth',
  windowMs: 15 * 60 * 1000, // 15 mins
  max: 15,
  message: 'Too many authentication attempts. Please try again later.',
});

export const refreshTokenRateLimiter = createRateLimiter({
  prefix: 'refresh-token',
  windowMs: 15 * 60 * 1000,
  max: 15,
  message: 'Too many token refresh requests.',
});

export const profileRateLimiter = createRateLimiter({
  prefix: 'profile',
  windowMs: 10 * 60 * 1000,
  max: 20,
  message: 'Too many profile update requests.',
});

export const roomCreateRateLimiter = createRateLimiter({
  prefix: 'room-create',
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: 'Room creation limit reached. Please wait before creating more rooms.',
});

export const roomJoinRateLimiter = createRateLimiter({
  prefix: 'room-join',
  windowMs: 5 * 60 * 1000,
  max: 30,
  message: 'Too many room join requests.',
});

export const chatRateLimiter = createRateLimiter({
  prefix: 'chat',
  windowMs: 60 * 1000, // 1 min
  max: 40,
  message: 'Message rate limit exceeded. Please slow down.',
});

export const friendRateLimiter = createRateLimiter({
  prefix: 'friend',
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: 'Too many friend actions. Please slow down.',
});

export const pollVoteRateLimiter = createRateLimiter({
  prefix: 'poll-vote',
  windowMs: 60 * 1000,
  max: 10,
  message: 'Too many poll votes.',
});

export const billingRateLimiter = createRateLimiter({
  prefix: 'billing',
  windowMs: 10 * 60 * 1000,
  max: 6,
  message: 'Too many billing verification requests.',
});

export const analyticsRateLimiter = createRateLimiter({
  prefix: 'analytics',
  windowMs: 60 * 1000,
  max: 60,
  message: 'Too many analytics events reported.',
});
