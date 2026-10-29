# Nebula — Credential Rotation List

Reason: every credential below was shared in plain text in chat on 2026-10-29,
and the release keystore passwords were previously committed in
`gradle.properties`. All of them must be treated as compromised — exposed
credentials do not become safe again because the file that held them was pruned.

This file lists **names and actions only**. It contains no secret values.

Rotation order is by blast radius, highest first.

## 1. Rotate now (grants broad access)

| # | Credential | Where to rotate | Why it is critical | After rotating |
|---|---|---|---|---|
| 1 | `FIREBASE_PRIVATE_KEY` (+ `FIREBASE_CLIENT_EMAIL`) | Google Cloud Console → IAM → Service Accounts → `firebase-adminsdk-fbsvc@…` → Keys → delete the old key, create a new JSON key | A Firebase Admin service-account key can read/write Auth, FCM and App Check for the whole project. It is the single most dangerous item exposed. | Put the new key in `server/.env` and in Render's `FIREBASE_PRIVATE_KEY` / `FIREBASE_CLIENT_EMAIL`. Restart the API. |
| 2 | `MONGODB_URI` (Atlas user `builder`) | MongoDB Atlas → Database Access → edit/rotate that user's password (or replace it with a least-privilege user) | Full read/write on the `nebula` database: every user, message and room. | Update `MONGODB_URI`; restart. Confirm `/ready` returns `database: connected`. |
| 3 | `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | S3-compatible provider console → revoke the key pair, issue a new one scoped to the Nebula bucket only | Object read/write on `vpc-d026d246-user-data` (avatars, banners, DM attachments). | Update `STORAGE_*`; restart; upload one avatar and one DM image to confirm. |
| 4 | Brevo `BREVO_API_KEY` | Brevo → SMTP & API → API Keys → revoke, create new | Can send mail as the verified sender (phishing and quota abuse). | Update `BREVO_API_KEY`; trigger an account-deletion code to confirm delivery. |
| 5 | Resend `RESEND_API_KEY` | Resend → API Keys → revoke, create new | Same as above for the fallback provider. | Update `RESEND_API_KEY`, or drop it from `.env` if you rely on Brevo only. |

## 2. Rotate with coordination (invalidates sessions or builds)

| # | Credential | Action | Impact |
|---|---|---|---|
| 6 | `JWT_SECRET`, `JWT_REFRESH_SECRET` | Generate new random values (`openssl rand -hex 48`) | Every existing session is invalidated; all users sign in again. Do it deliberately, not during a demo. |
| 7 | `NEBULA_APP_CLIENT_SECRET` | Change only together with a new Android build, because the same value is compiled into the client | A mismatch returns `403 APP_CLIENT_REQUIRED` for every request from the old APK. |
| 8 | Release keystore (`app/nebula-release-v2.jks`) | Generate a new keystore and add its SHA-1 to the Firebase Android OAuth client | Re-signing changes the app signature. Users on the old signature cannot update in place. |
| 9 | `YOUTUBE_API_KEY` | Google Cloud Console → Credentials → regenerate, and add API + Android application restrictions | Quota theft only; low severity, but it is free to fix. |
| 10 | `FIREBASE_WEB_API_KEY` | Not a true secret; restrict it by Android package name + SHA-1 (and HTTP referrers for web) instead of rotating | Prevents use of your project's quota from other apps. |

## 3. No action needed

| Item | Why |
|---|---|
| `GOOGLE_CLIENT_ID` | Public OAuth client identifier, embedded in every Android build. |
| `GOOGLE_CLIENT_SECRET` | Removed: the current Google/Firebase flow is a public client with no secret. |
| `NODE_ENV`, `PORT`, `API_BASE_URL`, `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `MONGODB_DATABASE`, `EMAIL_FROM` | Non-secret configuration. |
| `PLAY_PACKAGE_NAME` | Public package name. |

## 4. Retired (removed from configuration)

These were deleted from `server/.env`, `server/.env.example` and
`server/src/config/env.ts` because no code path reads them:

- `OTP_PROVIDER`, `OTP_API_KEY` — login-by-OTP was removed; authentication is Google/Firebase only.
- `GOOGLE_CLIENT_SECRET` — unused public-client flow.
- `CAPTCHA_SITE_KEY`, `CAPTCHA_SECRET_KEY` — no captcha middleware exists.
- `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` — no server-side Play Integrity check exists.
- `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` — no ads in the app.
- `SENTRY_DSN` — no Sentry integration.
- `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_EMBEDDING_MODEL` — no AI features.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_SECURE`, `SMTP_USER`, `SMTP_PASS` — email goes through provider HTTPS APIs.
- `CLOUDFLARE_TUNNEL_TOKEN`, `CLOUDFLARE_DOMAIN` — no tunnel is configured.

`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` and `PLAY_PACKAGE_NAME` remain in code
because the billing module compiles against them, but billing is out of product
scope and refuses to operate in production without a service account. If the
module is deleted later, those variables go with it.

## 5. Post-rotation verification

1. `npm run build` and `npm test` in `server/` (54 assertions) pass.
2. `PORT=5001 npm start` → `GET /health` 200 and `GET /ready` returns
   `{"status":"ready","database":"connected"}`.
3. On a device: sign in with Google, open a room, send a DM with an image
   (exercises Firebase, Mongo, Socket.IO and object storage in one pass).
4. Trigger an account-deletion code to confirm email delivery.
5. Confirm `git status` shows no `.env`, keystore or service-account file.

## 6. Prevention

- Never paste real credentials into chat or issue trackers; write them directly
  into the gitignored `.env` or the platform's secret store.
- Keep production secrets only in Render environment variables (or `/etc/secrets/.env`).
- Use per-environment credentials; do not reuse the dev key in production.
- Prefer short-lived credentials where the provider supports them (workload
  identity, STS tokens) instead of long-lived access keys.
