import express from 'express';
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
      allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With'],
      credentials: true,
    })
  );

  // 3. Payload Limits
  // Keep ordinary API requests small while allowing the explicit media-upload
  // endpoints to accept their documented data URL payloads.
  app.use((req, res, next) => {
    const isMediaUpload =
      req.path === '/api/v1/chat/upload' ||
      req.path === '/api/v1/users/me/assets';
    express.json({ limit: isMediaUpload ? '30mb' : '100kb' })(req, res, next);
  });
  app.use(express.urlencoded({ extended: true, limit: '100kb' }));

  // 4. Request Timeout Middleware (15s)
  app.use((req, res, next) => {
    req.setTimeout(15000, () => {
      res.status(504).json({ success: false, error: 'Gateway timeout: Request processing exceeded 15 seconds' });
    });
    next();
  });

  // 5. Global NoSQL Operator Injection Sanitizer
  app.use(noSqlSanitizer);

  // 6. Global Rate Limiter
  app.use(globalApiRateLimiter);

  // 7. Health & Readiness Endpoints
  app.get('/', (req, res) => {
    res.json({
      name: 'Nebula API Server',
      status: 'online',
      version: '2.0.0',
    });
  });

  app.get('/health', (req, res) => {
    res.json({
      status: 'healthy',
      uptime: process.uptime(),
      timestamp: new Date().toISOString(),
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

      app.get('/storage/:encodedKey', redirectToObject);
    }
  });

  // 8. Mount Protected API v1 Routers
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
