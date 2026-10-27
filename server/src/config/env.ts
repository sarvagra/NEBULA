import dotenv from 'dotenv';
import path from 'path';

// Load environment variables from .env
// Render Secret Files are mounted at /etc/secrets. Keep the local file as a
// development fallback and never allow it to override deployment secrets.
dotenv.config({ path: '/etc/secrets/.env' });
dotenv.config({ path: path.resolve(__dirname, '../../.env'), override: false });

function requireSecret(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} must be configured; refusing to start with an insecure default`);
  }

  return value;
}

function requireExternal(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} must be configured; local or mock service fallbacks are disabled`);
  }
  return value;
}

function requireRemoteProvider(name: string): string {
  const value = requireExternal(name).toLowerCase();
  if (value === 'local' || value === 'mock' || value === 'memory') {
    throw new Error(`${name} must reference a remote provider; local and mock storage are disabled`);
  }
  return value;
}

export const config = {
  server: {
    nodeEnv: process.env.NODE_ENV || 'development',
    isProduction: process.env.NODE_ENV === 'production',
    port: parseInt(process.env.PORT || '5001', 10),
    apiBaseUrl: (() => {
      const configured = requireExternal('API_BASE_URL');
      try {
        const parsed = new URL(configured);
        const isLocalOrigin = ['localhost', '127.0.0.1', '0.0.0.0', '10.0.2.2'].includes(parsed.hostname);
        const renderOrigin = process.env.RENDER_EXTERNAL_URL?.trim();
        if (process.env.NODE_ENV === 'production' && isLocalOrigin && renderOrigin) {
          return renderOrigin.replace(/\/$/, '');
        }
      } catch {
        throw new Error('API_BASE_URL must be a valid absolute URL');
      }
      return configured.replace(/\/$/, '');
    })(),
    corsAllowedOrigins: (process.env.CORS_ALLOWED_ORIGINS || '').split(',').map((s) => s.trim()).filter(Boolean),
  },
  mongodb: {
    uri: requireExternal('MONGODB_URI'),
    database: requireExternal('MONGODB_DATABASE'),
  },
  auth: {
    googleClientId: process.env.GOOGLE_CLIENT_ID || '',
    googleClientSecret: process.env.GOOGLE_CLIENT_SECRET || '',
    otpProvider: requireExternal('OTP_PROVIDER'),
    otpApiKey: process.env.OTP_API_KEY || '',
    jwtSecret: requireSecret('JWT_SECRET'),
    jwtRefreshSecret: requireSecret('JWT_REFRESH_SECRET'),
    jwtExpiresIn: '7d',
    jwtRefreshExpiresIn: '30d',
  },
  firebase: {
    projectId: process.env.FIREBASE_PROJECT_ID || 'nebula-production-170fa',
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL?.trim() || '',
    privateKey: (process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n') || '').replace(/^"|"$/g, '').trim(),
    webApiKey: process.env.FIREBASE_WEB_API_KEY?.trim() || '',
  },
  captcha: {
    siteKey: process.env.CAPTCHA_SITE_KEY || '',
    secretKey: process.env.CAPTCHA_SECRET_KEY || '',
  },
  playIntegrity: {
    cloudProjectNumber: process.env.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER || '',
  },
  appCheck: {
    enforcement: (process.env.APP_CHECK_ENFORCEMENT || 'warn') as 'enforce' | 'warn' | 'off',
  },
  clientAttestation: {
    enforcement: (process.env.APP_ATTESTATION_ENFORCEMENT || 'enforce') as 'enforce' | 'warn' | 'off',
    secret: process.env.NEBULA_APP_CLIENT_SECRET || 'nebula_android_client_attestation_2026_x89a',
  },
  billing: {
    packageName: process.env.PLAY_PACKAGE_NAME || 'com.nebula.app',
    serviceAccountJson: process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '',
    razorpayKeyId: process.env.RAZORPAY_KEY_ID || '',
    razorpayKeySecret: process.env.RAZORPAY_KEY_SECRET || '',
  },
  media: {
    youtubeApiKey: process.env.YOUTUBE_API_KEY || '',
  },
  ads: {
    appId: process.env.ADMOB_APP_ID || '',
    bannerId: process.env.ADMOB_BANNER_ID || '',
    interstitialId: process.env.ADMOB_INTERSTITIAL_ID || '',
    rewardedId: process.env.ADMOB_REWARDED_ID || '',
  },
  storage: {
    provider: requireRemoteProvider('STORAGE_PROVIDER'),
    bucket: process.env.STORAGE_BUCKET || '',
    accessKey: process.env.STORAGE_ACCESS_KEY || '',
    secretKey: process.env.STORAGE_SECRET_KEY || '',
    endpoint: process.env.STORAGE_ENDPOINT || '',
    region: process.env.STORAGE_REGION || 'auto',
    publicBaseUrl: process.env.STORAGE_PUBLIC_BASE_URL || '',
  },
  monitoring: {
    sentryDsn: process.env.SENTRY_DSN || '',
  },
  ai: {
    // Deliberately empty per specification
    provider: process.env.AI_PROVIDER || '',
    apiKey: process.env.AI_API_KEY || '',
    model: process.env.AI_MODEL || '',
    embeddingModel: process.env.AI_EMBEDDING_MODEL || '',
  },
  email: {
    smtpHost: process.env.SMTP_HOST || '',
    smtpPort: parseInt(process.env.SMTP_PORT || '587', 10),
    smtpSecure: process.env.SMTP_SECURE === 'true',
    smtpUser: process.env.SMTP_USER || '',
    smtpPass: process.env.SMTP_PASS || '',
    fromEmail: process.env.EMAIL_FROM || process.env.SMTP_USER || 'Nebula <no-reply@nebula.app>',
  },
};
