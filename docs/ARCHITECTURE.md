# Nebula — Architecture

Status: Phase 0 audit baseline (2026-10-29).
Scope: describes what exists in the repository today, not an aspiration.

---

## 1. System overview

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│ Nebula Android (Kotlin)     │        │ nebula-api (Node/TS)         │
│ Compose + Material3         │ HTTPS  │ Express 4 + Zod              │
│ ViewModel + Repository      │───────▶│ REST /api/v1/*               │
│ Coil / DataStore / WebView  │        │                              │
│                             │ Socket │ Socket.IO (room + presence)  │
│                             │◀──────▶│                              │
└──────────┬──────────────────┘        └───────┬──────────────┬────────┘
           │ Firebase Auth (Google)            │              │
           │ Firebase App Check                │ Mongoose     │ S3 presign
           ▼                                   ▼              ▼
   ┌───────────────┐                  ┌──────────────┐  ┌──────────────┐
   │ Firebase Auth │                  │   MongoDB    │  │ S3-compatible│
   │ (identity)    │                  │ (all app     │  │ object store │
   │ FCM (push)    │                  │  records)    │  │ (+ private   │
   └───────────────┘                  └──────────────┘  │  signed URLs)│
                                                        └──────────────┘
```

Firebase Authentication is the only identity provider. MongoDB is the only
system of record for application data. Object storage holds binaries; MongoDB
holds metadata. Socket.IO delivers live events; it is never the source of truth.

## 2. Android client

| Concern | Implementation |
|---|---|
| Language / UI | Kotlin 1.9.23, Jetpack Compose (BOM 2024.05.00), Material3 |
| Build | Gradle 8.7, AGP 8.3.2, `compileSdk`/`targetSdk` 34, `minSdk` 26, JVM 17 |
| Architecture | Single `ComponentActivity`, Compose screens, `HomeViewModel` (≈1,520 LOC) as the shared state holder, repository interfaces in `domain/repository`, implementations in `data/repository` |
| DI | Manual construction in `MainActivity` (no Hilt/Koin) |
| Navigation | In-activity state machine (`FullScreenDestination`) plus `NavSection` tabs; a small `NavigationRepository` abstraction |
| Auth | `NebulaAuthManager` — Credential Manager → Google ID token → `FirebaseAuth.signInWithCredential` → Firebase ID token → backend session |
| Session | `SessionManager` (DataStore) stores backend JWT + refresh token + cached profile; `NebulaApiClient` holds the bearer token in memory |
| Realtime | `RoomSocketClient` (socket.io-client 2.1.1) |
| Media | WebView + YouTube IFrame API for watch rooms; WebView/shim HTML generated inline in `NebulaRoomScreen` |
| Images | Coil 2.6 (with GIF decoder), `NebulaAvatarImage`, custom cache headers |
| Push | `NebulaFirebaseMessagingService` + FCM tokens registered to the backend |
| Theme | Custom token system (`theme/DesignTokens`, `ThemePresets`, 4 presets) — not Material You |

### Screen inventory (existing)

`ui/auth` (auth gate, onboarding) · `ui/home` (Home, Rooms, Chats, Friends,
Profile, Room, ChatDetail) · `ui/profile` (edit, public) · `ui/settings`
(settings, account, blocked users, notifications, privacy, about, Google
account) · `ui/membership` · `ui/dialogs` (18 sheets) · `ui/components`
(design-system widgets).

Known structural debt: `NebulaRoomScreen.kt` (2,297 LOC), `NebulaChatDetailScreen.kt`
(1,389 LOC) and `HomeViewModel.kt` (≈1,520 LOC) are monolithic and are the main
maintainability risk.

## 3. Backend

| Concern | Implementation |
|---|---|
| Runtime | Node.js, TypeScript 5.4, Express 4 |
| Validation | Zod schemas (`utils/schemas.ts`) via `validateBody` |
| AuthN | `authMiddleware` accepts either a Nebula session JWT (HS256, revocable via `RefreshToken` rows) or a Firebase ID token verified with the Admin SDK |
| AuthZ | Controller-level ownership/membership/host checks; `roleMiddleware` for admin routes |
| Data | Mongoose 8, models: `User`, `Room`, `Message`, `Friendship`, `Notification`, `Poll`, `AnalyticsEvent`, `MediaHistory`, `ProfileAsset`, `RefreshToken` |
| Realtime | `realtime/socketServer.ts` (694 LOC) — room join/leave, playback commands, chat, reactions, polls, presence |
| Media | `services/objectStorage.ts` (S3-compatible, AWS SDK v3, presigned PUT/GET), `storageController` signed proxy route |
| Push | `services/pushNotificationService.ts` (FCM Admin) |
| Email | `services/emailService.ts` (Brevo HTTPS, Resend fallback) — used only for account-deletion codes after the Phase 1 change |
| Hardening | helmet, CORS allow-list, global + per-route rate limiters, Mongo operator sanitizer, body size caps, error handler with redaction, App Check middleware, HMAC client-attestation middleware |
| Deployment | Render web service (`render.yaml`), `npm ci && npm run build`, health check `/health` |
| Health | `GET /health`, `GET /ready` (DB readiness) |

### REST surface (`/api/v1`)

- `auth`: `POST /google`, `POST /firebase`, `POST /refresh`, `POST /logout`, `GET /me`, `GET /sessions`, `POST /sessions/revoke-others`, `DELETE /sessions/:id`
- `users`: profile update, presence, username check, public profile by username/id, account-deletion lifecycle, media assets (list/upload/apply/delete)
- `rooms`: list, get, create, join, leave, settings, transfer host, invite, media update, kick, moderation
- `chat`: room history, conversation history, send, upload, reactions, read receipts, edit, unsend, archive maintenance
- `friends`, `notifications`, `polls`, `billing`, `analytics`, `media`

### Socket events

Client→server: `room:join`, `room:leave`, `room:foreground`, `room:background`,
`presence:status`, `media:play|pause|seek|switch|restart|end`, `chat:message`,
`chat:reaction`, `poll:vote`.
Server→client: `media:sync`, `chat:message`, `room:removed`, `error`, plus
participant/presence fan-out.

## 4. Playback synchronisation model

Authoritative state lives on the room document (`mediaState`: media identity,
`playbackPosition`, `isPlaying`, version/timestamp). The Java/Compose client
renders YouTube through a WebView IFrame player:

- Host samples its own player every 250 ms via a `@JavascriptInterface` bridge;
  the client throttles before emitting `media:*`.
- The server rebroadcasts `media:sync` to room members.
- Non-host clients compute the expected position as
  `position + (now − timestamp)` when playing and only `seekTo` when the drift
  exceeds ~0.65 s, so a steady stream of seeks is avoided.
- Play/pause/seek/end are host-authoritative; the server rejects non-host
  commands (proven in `multiClientSyncTest`).

Limits: YouTube IFrame playback is inherently soft-synchronised (network and
ad/buffering differences); there is no frame-accurate guarantee and no DRM.

## 5. Identity and authorisation flow

1. Client obtains a Google ID token via Credential Manager.
2. Client signs into Firebase Auth (`GoogleAuthProvider`).
3. Client sends the Firebase ID token to `POST /api/v1/auth/google`.
4. Server verifies it with `firebase-admin` (`verifyIdToken`), upserts the
   profile keyed by Firebase UID, and issues a short-lived session JWT plus a
   revocable refresh token (max 2 device sessions).
5. Subsequent REST and Socket calls present the session JWT; the middleware
   re-checks the session row and the ban flag on every request.
6. The Firebase UID is the stable identity; no client-supplied user ID is
   trusted for authorisation.

## 6. Configuration and secrets

All server configuration is environment-driven (`server/src/config/env.ts`),
with required values failing fast rather than falling back to insecure
defaults. Placeholders live in `server/.env.example`; real values live in
`server/.env` (gitignored) or Render environment variables. Android build-time
values come from `app/google-services.json` and `BuildConfig`.

Required: `API_BASE_URL`, `MONGODB_URI`, `MONGODB_DATABASE`, `JWT_SECRET`,
`JWT_REFRESH_SECRET`, `STORAGE_PROVIDER` (must name a remote provider).
Used when present: `GOOGLE_CLIENT_ID`, `FIREBASE_*`, `STORAGE_*`, `BREVO_API_KEY`,
`RESEND_API_KEY`, `EMAIL_FROM`, `YOUTUBE_API_KEY`, `NEBULA_APP_CLIENT_SECRET`,
`APP_CHECK_ENFORCEMENT`, `APP_ATTESTATION_ENFORCEMENT`.

Phase 1 removed the committed release keystore passwords from `gradle.properties`
and deleted dead configuration groups (`CAPTCHA_*`, `PLAY_INTEGRITY_*`, `ADMOB_*`,
`SENTRY_DSN`, `AI_*`, `SMTP_*`, `CLOUDFLARE_*`, `OTP_PROVIDER`) that no code read.
The client-attestation HMAC value is a constant shipped inside the APK, so it is
treated as an app identifier rather than a secret; Play Integrity (App Check) is
the control that actually resists a determined attacker.

Credential rotation is tracked in `docs/SECRETS_ROTATION.md`.

## 7. Caching and local state

- Coil memory/disk cache for avatars, banners and attachments; HTTP cache
  headers from the signed storage route.
- DataStore holds session tokens, theme, backend URL and small preferences.
- No message or room content is stored locally as an authority; history is
  re-fetched from the API. This satisfies "cloud is the source of truth" at the
  cost of cold-start latency in chat.

## 8. Testing surface

- `server/src/tests/securityTests.ts` — 40+ isolated API assertions against
  `mongodb-memory-server` (auth, IDOR, injection, rate limits, retention,
  entitlements, account lifecycle).
- `server/src/tests/multiClientSyncTest.ts` — two-client Socket.IO playback and
  chat synchronisation.
- `server/src/tests/firebaseAuthFlowTest.ts`, `connectivityTest.ts`.
- No Android unit or instrumentation tests exist in the repository.

## 9. Risk register (current)

| Risk | Severity | Note |
|---|---|---|
| Email/OTP and dev-login authentication bypass Firebase identity | Critical | Removed in Phase 1 |
| Hardcoded admin email grants `admin` + `premium_plus` | Critical | Removed in Phase 1 |
| Account matched by email claim in middleware | High | Tightened to Firebase UID in Phase 1 |
| Release keystore passwords in version control | High | Rotate keystore; values removed from tracked file |
| Client-attestation HMAC secret shipped in APK | High | Not a real security boundary; documented, to be replaced by Play Integrity only |
| Monolithic ViewModel/screens | Medium | Refactor before adding large features |
| Single-instance realtime (no Redis adapter) | Medium | Acceptable for current scale; document before scaling |
| No Android automated tests | Medium | Add as features change |
