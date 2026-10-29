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
    // Google is the only identity provider. The web client ID is used to check
    // the audience of Google-issued tokens; Firebase ID tokens are verified
    // separately with the Admin SDK.
    googleClientId: process.env.GOOGLE_CLIENT_ID || '',
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
  appCheck: {
    enforcement: (process.env.APP_CHECK_ENFORCEMENT || 'warn') as 'enforce' | 'warn' | 'off',
  },
  clientAttestation: {
    enforcement: (process.env.APP_ATTESTATION_ENFORCEMENT || 'enforce') as 'enforce' | 'warn' | 'off',
    // Shared with the shipped Android client, so treat this as a client
    // identifier rather than a real secret. Play Integrity (App Check) is the
    // control that actually resists a determined attacker.
    secret: process.env.NEBULA_APP_CLIENT_SECRET || 'nebula_android_client_attestation_2026_x89a',
  },
  // Google Play billing is not part of the current product scope; the module
  // refuses to operate in production without a service account.
  billing: {
    packageName: process.env.PLAY_PACKAGE_NAME || 'com.nebula.app',
    serviceAccountJson: process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '',
    razorpayKeyId: process.env.RAZORPAY_KEY_ID || '',
    razorpayKeySecret: process.env.RAZORPAY_KEY_SECRET || '',
  },
  media: {
    youtubeApiKey: process.env.YOUTUBE_API_KEY || '',
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
  email: {
    brevoApiKey: process.env.BREVO_API_KEY || '',
    resendApiKey: process.env.RESEND_API_KEY || '',
    fromEmail: process.env.EMAIL_FROM || 'Nebula <nebula.app.auth@gmail.com>',
  },
};
