import express from 'express';
import path from 'path';
import fs from 'fs';
import cors from 'cors';
import helmet from 'helmet';
import mongoose from 'mongoose';
import { config } from './config/env';
import { noSqlSanitizer } from './middlewares/sanitizer';
import { globalApiRateLimiter } from './middlewares/rateLimiter';
import { errorHandler } from './middlewares/errorMiddleware';
import { authRouter } from './modules/auth/authRoutes';
import { userRouter } from './modules/users/userRoutes';
import { roomRouter } from './modules/rooms/roomRoutes';
import { chatRouter } from './modules/chat/chatRoutes';
import { friendRouter } from './modules/friends/friendRoutes';
import { pollRouter } from './modules/polls/pollRoutes';
import { notificationRouter } from './modules/notifications/notificationRoutes';
import { billingRouter } from './modules/billing/billingRoutes';
import { analyticsRouter } from './modules/analytics/analyticsRoutes';
import { mediaRouter } from './modules/media/mediaRoutes';
import { redirectToObject } from './modules/media/storageController';
import { securityLockdownMiddleware } from './middlewares/securityLockdown';
import { appCheckMiddleware } from './middlewares/appCheckMiddleware';
import { clientAttestationMiddleware } from './middlewares/clientAttestationMiddleware';

export function createApp(): express.Application {
  const app = express();

  // Trust Cloudflare and reverse proxies for accurate client IP and SSL termination
  app.set('trust proxy', 1);

  // 1. Production-Safe Security Headers
  app.use(
    helmet({
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: ["'self'"],
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", 'data:', 'https:'],
        },
      },
      crossOriginEmbedderPolicy: false,
      hidePoweredBy: true,
      noSniff: true,
      xssFilter: true,
    })
  );

  // 2. Strict CORS Configuration
  app.use(
    cors({
      origin: (origin, callback) => {
        // Mobile applications & curl requests send undefined origin
        if (!origin) return callback(null, true);

        if (!config.server.isProduction) {
          // Allow all origins in local development
          return callback(null, true);
        }

        if (config.server.corsAllowedOrigins.includes(origin)) {
          return callback(null, true);
        }

        return callback(new Error('CORS request blocked: Origin not allowed by Nebula security policy'));
      },
      methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
      allowedHeaders: [
        'Content-Type',
        'Authorization',
        'X-Requested-With',
        'X-Admin-Key',
        'X-Firebase-AppCheck',
        'X-Device-Id',
        'X-Device-Info',
        'X-Nebula-App-Signature',
        'X-Nebula-Timestamp',
        'X-Nebula-Client',
      ],
      credentials: true,
    })
  );

  // 3. Payload Limits
  // Keep ordinary API requests small while allowing the explicit media-upload
  // endpoints to accept their documented data URL payloads.
  app.use((req, res, next) => {
    const isMediaUpload =
      req.path === '/api/v1/chat/upload' ||
      req.path === '/api/v1/users/me/assets' ||
      req.path === '/api/v1/users/profile';
    // A 30 MiB binary video grows to roughly 40 MiB as a JSON base64 data URL.
    express.json({ limit: isMediaUpload ? '45mb' : '100kb' })(req, res, next);
  });
  app.use(express.urlencoded({ extended: true, limit: '100kb' }));

  // 3b. Marketing site. The landing page is served from this same origin so the
  //     app's "Visit our website" link and the APK download work without a second
  //     hosting service. It is registered before the API middlewares because a
  //     6 MB APK download must not be cut off by the 15 second API socket timeout.
  const landingDir = path.resolve(__dirname, '../../landing');
  app.use(
    express.static(landingDir, {
      index: 'index.html',
      setHeaders: (res, filePath) => {
        if (filePath.endsWith('.html') || filePath.endsWith('.json')) {
          res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
          res.setHeader('Pragma', 'no-cache');
          res.setHeader('Expires', '0');
        } else if (filePath.endsWith('.apk')) {
          res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
          res.setHeader('Content-Type', 'application/vnd.android.package-archive');
        } else {
          res.setHeader('Cache-Control', 'public, max-age=86400, must-revalidate');
        }
      },
    })
  );

  app.get('/', (_req, res) => {
    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
    res.sendFile(path.join(landingDir, 'index.html'), (err) => {
      if (err && !res.headersSent) res.status(404).type('text/plain').send('Nebula');
    });
  });

  // App version manifest for self-updating mobile clients
  const getAppVersionManifest = () => {
    const manifestPath = path.join(landingDir, 'version.json');
    if (fs.existsSync(manifestPath)) {
      try {
        return JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
      } catch {
        // Fallback to default
      }
    }
    return {
      success: true,
      versionCode: 6,
      versionName: '1.1.4',
      minSupportedVersionCode: 1,
      apkUrl: '/download/nebula-latest.apk',
      releaseNotes: '• Fixed photo sending with memory-safe downsampling and expanded 40MB pick limit\n• Authentic 3D banded Jupiter app icon and warm in-app branding\n• Silky smooth profile swiping with pre-warmed tabs and cached banner canvas\n• Quick settings gear on profile replacing the options menu',
      forceUpdate: false,
      publishedAt: new Date().toISOString(),
    };
  };

  app.get(['/api/v1/app/version', '/version.json'], (_req, res) => {
    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
    res.json(getAppVersionManifest());
  });

  // Serve latest APK download dynamically
  app.get(['/download/nebula-latest.apk', '/download/latest'], (_req, res) => {
    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    const downloadDir = path.join(landingDir, 'download');
    const specificLatest = path.join(downloadDir, 'nebula-latest.apk');
    if (fs.existsSync(specificLatest)) {
      return res.sendFile(specificLatest);
    }
    if (fs.existsSync(downloadDir)) {
      const apks = fs.readdirSync(downloadDir).filter((f) => f.endsWith('.apk')).sort();
      if (apks.length > 0) {
        return res.sendFile(path.join(downloadDir, apks[apks.length - 1]));
      }
    }
    res.status(404).type('text/plain').send('Nebula APK not found');
  });

  // 4. Emergency Security Circuit Breaker & Honeypot Defenses
  app.use(securityLockdownMiddleware);

  // Media uploads can be 30 MiB; allow time for mobile uplinks while retaining a bounded timeout for other APIs.
  app.use((req, res, next) => {
    const isMediaUpload =
      req.path === '/api/v1/chat/upload' ||
      req.path === '/api/v1/users/me/assets' ||
      req.path === '/api/v1/users/profile';
    const uploadTimeoutMs = isMediaUpload ? 120000 : 15000;
    req.setTimeout(uploadTimeoutMs, () => {
      res.status(504).json({ success: false, error: 'Gateway timeout: Request processing exceeded 15 seconds' });
    });
    next();
  });

  // 5. Global NoSQL Operator Injection Sanitizer
  app.use(noSqlSanitizer);

  // 6. Health, readiness, and storage routes — registered BEFORE the global rate
  //    limiter so infrastructure probes and media token checks are never throttled.
  app.get('/health', (req, res) => {
    res.json({
      status: 'healthy',
      uptime: process.uptime(),
      timestamp: new Date().toISOString(),
      // Lets a running deploy be tied to the exact commit serving it, so "deployed"
      // can be checked instead of inferred from a successful push.
      revision: (process.env.RENDER_GIT_COMMIT || process.env.GIT_COMMIT || 'unknown').slice(0, 7),
    });
  });

  app.get('/ready', (req, res) => {
    // Verify core infrastructure dependencies (e.g. MongoDB connection)
    const dbState = mongoose.connection.readyState;
    // 1 = connected, 2 = connecting
    if (dbState === 1) {
      res.status(200).json({
        status: 'ready',
        database: 'connected',
      });
    } else {
      res.status(503).json({
        status: 'not_ready',
        database: dbState === 2 ? 'connecting' : 'disconnected',
      });
    }
  });

  // Private profile and chat objects must remain reachable regardless of load;
  // authorization is enforced by the signed token in the query string.
  app.get('/storage/:encodedKey', redirectToObject);

  // 7. Global Rate Limiter (applied only to /api/v1/* routes below)
  app.use(globalApiRateLimiter);

  // 8. Attestation Layer: App-Only Signature Attestation & Play Integrity
  app.use('/api/v1', clientAttestationMiddleware);
  app.use('/api/v1', appCheckMiddleware);

  // 9. Mount Protected API v1 Routers
  app.use('/api/v1/auth', authRouter);
  app.use('/api/v1/users', userRouter);
  app.use('/api/v1/rooms', roomRouter);
  app.use('/api/v1/chat', chatRouter);
  app.use('/api/v1/friends', friendRouter);
  app.use('/api/v1/polls', pollRouter);
  app.use('/api/v1/notifications', notificationRouter);
  app.use('/api/v1/billing', billingRouter);
  app.use('/api/v1/analytics', analyticsRouter);
  app.use('/api/v1/media', mediaRouter);

  // 9. Centralized Error Handler
  app.use(errorHandler);

  return app;
}
