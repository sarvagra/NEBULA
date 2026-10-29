# Nebula — Implementation Plan

Baseline: 2026-10-29. Derived from the repository audit and the Rave 9.0.58
static inspection. Ordered by dependency, not by ambition.

Guiding rule: no dependent feature is built on a broken foundation. Each step
ends with a build + test result, not a promise.

---

## Phase 0 — Audit and baseline (complete)

Delivered:

- `docs/ARCHITECTURE.md`, `docs/FEATURE_MATRIX.md`, `docs/IMPLEMENTATION_PLAN.md`,
  `docs/DEVICE_TEST_PLAN.md`, `docs/KNOWN_LIMITATIONS.md`.
- Static reference-APK review: manifest, permissions, provider assets, dex class
  inventory, default-locale string table.
- Baseline measurements: server `tsc --noEmit` clean; `npm test` 53/53 pass.
- Environment findings: no physical device attached; only AVD is a non-GMS
  `default` image, so Google sign-in cannot be exercised locally.

## Phase 1 — Foundation and Google-only authentication (in progress)

| ID | Task | Status |
|---|---|---|
| 1.1 | Remove `POST /auth/otp/request`, `POST /auth/otp/verify`, `POST /auth/dev-login` | Done |
| 1.2 | Remove the email/OTP sign-in UI and client plumbing from the auth gate | Done |
| 1.3 | Remove the hardcoded admin/premium escalation for specific email addresses | Done |
| 1.4 | Match accounts by Firebase UID only (never by unverified email claim) | Done |
| 1.5 | Replace test-suite dependence on dev-login with in-suite signed sessions | Done |
| 1.6 | Remove committed keystore passwords; require env/Gradle properties | Done |
| 1.7 | Remove hardcoded attestation-secret fallback in server config | Done |
| 1.8 | Re-run server suite and Android build; install and launch on device | Done — 54/54 server tests, debug APK installed and launched on Realme RMX3842 |
| 1.9 | Verify Google sign-in end-to-end with real Firebase credentials | Done — account picker → consent → backend session → onboarding (see DEVICE_TEST_PLAN run log) |
| 1.10 | Verify session restore on device | Done — cold restart restored the session. Session *revocation* hand-off still needs a second device |
| 1.11 | Device-cap recovery: an account locked out by two stale sessions must be able to sign in again | Done — `POST /auth/google` accepts an explicit `replaceOldestDevice`, which retires the least recently used session instead of refusing; the client retries once after the refusal and tells the user which outcome happened. Suite-asserted (refusal by default, exactly one eviction on recovery) and device-verified against a working-tree server (DEVICE_TEST_PLAN §4e). Requires a backend redeploy to be live in production |

Acceptance: the only authentication entry point in code is Google → Firebase;
no endpoint can mint a session without a verified Firebase ID token; server
suite green; app launches to the auth gate.

## Phase 2 — Profiles and social graph

| ID | Task | Depends on |
|---|---|---|
| 2.1 | Add Android unit tests for `parseUserProfileFromBackendJson` and session restore | — |
| 2.2 | Finish public-profile entry points from room participants and chat | 1.8 |
| 2.3 | Privacy matrix (public / friends / hidden) for bio, connections, activity | 2.2 |
| 2.4 | Reporting: moderator queue + resolution states (currently one-way) | 2.2 |
| 2.5 | Screen-level loading/empty/error audit across profile screens | 2.2 |

## Phase 3 — Persistent DMs

| ID | Task | Depends on | Status |
|---|---|---|---|
| 3.1 | Extract `NebulaChatDetailScreen` (1,429 LOC) into message list, composer, attachment, and history modules | — | Not started |
| 3.2 | Conversation-level caching with explicit staleness (last-sync token) | 3.1 | Not started |
| 3.3 | Outgoing-message queue with pending/failed states and idempotent retry on reconnect | 3.1 | Done — client sends a per-message idempotency key, the bubble carries `SENDING`/`SENT`/`FAILED`, an unsent message survives server syncs, and tapping a failed bubble retries with the original key. Device-verified (3 runs) |
| 3.3a | Automatic retry when connectivity returns, and unsent messages surviving process death | 3.3 | Done — unsent messages are mirrored to `SharedPreferences`, restored on launch, and re-sent from a repository-owned scope once the server answers again. Device-verified across a force-stop with the radio off |
| 3.4 | Missed-message recovery after socket reconnect (cursor-based backfill) | 3.3 | Done — `GET /chat/room/:roomId?since=` and `/chat/conversation/:id?since=` return only the window after a cursor (ascending, bounded, `400` on an unparseable cursor); the Android room client keeps a chat cursor and backfills on every socket reconnect. Suite-verified server-side; client path compile-verified |
| 3.5 | Two-account delivery test on real devices | 3.3, 1.9 | Blocked on a second signed-in account (2-device session cap) |
| 3.6 | Friend-request row reflects server state immediately (`Add` / `Requested` / `Accept`) | — | Done — device-verified against a fresh server search in both directions |

## Phase 4 — Watch rooms

| ID | Task | Depends on |
|---|---|---|
| 4.1 | Split `NebulaRoomScreen` (2,297 LOC) into player, chat, participants, controls | — |
| 4.2 | Room discovery ranking (recency + participants) and filter chips — **Done:** discovery ranks by participants, activity recency and whether media is playing, instead of newest-first; the candidate window is bounded (≤150) and ties fall back to most recently active. Suite-asserted (busier room outranks a newer empty one) | — |
| 4.3 | Room queue model (add/vote/skip) matching reference behaviour | 4.1 |
| 4.4 | Shareable invite codes and resolved deep links | — | Partially done — rooms now carry a non-guessable 8-character code (ambiguous characters excluded) exposed in the room DTO, `GET /rooms/invite/:code` resolves it for a viewer who may see the room, and a refused private room answers byte-identically to an unknown code. Suite-asserted. **Remaining:** the Android share sheet and the `nebula://room/<code>` intent handler |
| 4.5 | Host-disconnect / host-transfer UX and room lifecycle edge cases | 4.1 |
| 4.6 | Moderation surface for reports inside rooms | 2.4 |

## Phase 5 — Playback synchronisation

| ID | Task | Depends on |
|---|---|---|
| 5.1 | Version playback state server-side; reject stale/out-of-order client commands — **Done:** every accepted host command increments a room version that rides on every playback event; a command built on an older version is refused with an authoritative `media:state` correction, and an out-of-order host sample cannot rewind the playhead (4 new suite assertions) | — |
| 5.2 | Non-YouTube player path (direct HLS/MP4) so validated links are actually playable | 5.1 |
| 5.3 | Reconnect/late-join drift measurement harness (two clients, scripted seeks) | 5.1 |
| 5.4 | Buffering/error states and host hand-off of stalled media | 5.2 |

## Phase 6 — Social integration and notifications

| ID | Task | Depends on |
|---|---|---|
| 6.1 | Share room into DM and open invitation from conversation | 3.x, 4.4 |
| 6.2 | Verified FCM delivery on two devices; dead-token pruning | 1.9 |
| 6.3 | Presence reconciliation on abrupt disconnect (server-side TTL sweep) — **Done:** `sweepStalePresence()` reclaims users whose heartbeat stopped (120 s) while never touching a user with a live socket; runs every 60 s, suite-verified both directions | — |
| 6.4 | Room context banner inside DM without losing the thread | 6.1 |

## Phase 7 — Release hardening

| ID | Task |
|---|---|
| 7.1 | Rotate the release keystore and all secrets exposed in git history |
| 7.2 | Dependency audit (`npm audit`, Gradle dependency check) — **Done:** `npm audit` reports 5 advisories (3 moderate, 2 high). The zod advisory (memory exhaustion on oversized constrained collections) is bounded by the 100 KB `express.json` limit on every non-upload route; the uuid advisories are transitive inside the server-only Firebase Admin client. No dependency was force-upgraded (zod 4 is a breaking change) — the upgrade is tracked below |
| 7.3 | Android instrumentation tests for auth gate, DM send, room join |
| 7.4 | Data-deletion verification end-to-end including object storage |
| 7.5 | Cost/quota review of Render, MongoDB Atlas, S3, FCM |
| 7.6 | Signed release build + Play Console internal track |

---

## Deliberate non-goals

- Reproducing DRM/OTT provider playback or provider OAuth.
- WebRTC voice/video rooms and AR filters.
- Face-scan age verification.
- Multi-region/distributed realtime before single-instance limits are measured.

## Credentials and external actions still required

| Item | Why | Owner |
|---|---|---|
| Physical Android device (or GMS Play Store AVD) | Only way to exercise Google sign-in and FCM | User |
| SHA-1 of the signing key registered on the Firebase Android OAuth client | Google sign-in fails `This android application is not registered` otherwise | User (console) |
| `MONGODB_URI`, `FIREBASE_*` service account, `JWT_*` | Server startup requires them | Already in `server/.env`; must be re-provisioned per environment |
| S3-compatible bucket credentials | Avatar/banner/attachment uploads | Already configured locally |
| `YOUTUBE_API_KEY` | Room media search | Optional |
