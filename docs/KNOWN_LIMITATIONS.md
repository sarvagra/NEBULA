# Nebula — Known Limitations

Last updated: 2026-10-29. This file distinguishes verified facts from
unverified claims. Nothing here should be read as "working" unless it names
evidence.

---

## 1. Verification limitations (this environment)

| Limitation | Impact |
|---|---|
| Only one physical device (`Realme RMX3842`, Android 16) was available | Google sign-in and session restore were verified, but two-device concurrency, push delivery to a peer, audio focus and battery behaviour were not. |
| The only AVD is a `default` system image without Google Play services | The emulator cannot show the Google account picker, so emulator auth testing is impossible. |
| No release-signed build was produced in this session | The debug key is accepted by the Firebase project, but the release build path now requires signing credentials supplied from outside the repository (see `gradle.properties`). |
| Only one live account was signed in at a time | DM/room/invite authorisation was validated at API level (suite) and by the earlier recorded runs, not between two live clients in this session. |
| A test profile was created | Signing in with `24f2004922@ds.study.iitm.ac.in` created a new Nebula profile so the flow could be verified without disturbing existing sessions. It can be deleted through Settings → Account. |
| Reference APK inspected statically only | No runtime observation of Rave. All reference claims come from manifest, default-locale strings, dex class names and assets. Hidden/dynamic behaviour is unknown. |

## 2. Product limitations

| Area | Limitation |
|---|---|
| Playback | YouTube IFrame inside a WebView. No frame-accurate sync; ad breaks and buffering can diverge clients. Direct video URLs are validated server-side but only YouTube is rendered. |
| Provider playback | Netflix, Disney+, HBO Max, Crunchyroll, Prime Video, Tubi, Pluto, Twitch, X, SoundCloud, Rutube/VK are **not** implemented and will not be: they depend on provider OAuth plus DRM, which cannot be lawfully reimplemented. |
| Voice/video rooms | Rave's WebRTC voice chat and AR filters are out of scope; no microphone/camera capture is implemented. |
| Age verification | Rave's face-scan age check is not implemented. |
| Message expiry | Rave's "disappearing messages" feature is not implemented. |
| Auto-translate | Not implemented; would require a translation provider and a clear data-processing disclosure. |
| Room queue | Nebula holds one media item per room; there is no queue/vote-to-skip model yet. |
| Nearby rooms | Deliberately not implemented: no location permission is requested. |
| Offline sends | A message that cannot reach the server stays in the thread marked `Not sent · Tap to retry`; nothing is fabricated as sent. The queue survives process death and re-sends automatically once the server answers again, but only while the app is open — there is no background/WorkManager retry, and nothing is delivered while the app is closed. Messages the server rejects outright (4xx) stop auto-retrying and need an explicit tap. |
| Two-device session cap | `DEVICE_LIMIT_REACHED` is still the default when a third distinct device signs in. Lockout is no longer permanent: the client retries once with `replaceOldestDevice`, which retires the least recently used session and reports that it did so. The recovery is automatic rather than a picker — a user who still has both devices cannot choose *which* one is signed out from the sign-in screen; they must sign in first and use Settings → Account. |
| Dependency advisories | `npm audit`: 5 advisories (3 moderate, 2 high) as of 2026-10-30. The zod advisory is bounded by the 100 KB JSON body limit, and the uuid advisories are transitive in the server-only Firebase Admin client. Remediation (zod 4) is a breaking major upgrade and was deliberately not forced. |
| End-to-end encryption | Not implemented and not claimed anywhere in the UI or docs. |
| Presence | Single-instance Socket.IO without a Redis adapter; horizontal scaling would break presence fan-out. The 60 s reconciliation sweep is also per instance, so two instances would each only reclaim their own abandoned rows (harmless, but not a distributed lease). |
| Playback ordering on the client | The server refuses stale commands and out-of-order samples (suite-asserted), and the Android client echoes the playback version and drops older inbound events. The client half has not been exercised against a second live client, so it is compile-verified only. A command packet delayed by more than one command round-trip is corrected rather than applied — a client can therefore see a brief jump back to the authoritative position. |
| Room chat backfill | Recovery uses `createdAt` cursors. Two messages written in the same millisecond are returned together and de-duplicated by id client-side; there is no per-room monotonic sequence number, so a cursor cannot yet be expressed as a single integer. |
| Notifications | Server send paths exist; delivery was not verified without a GMS device. |

## 3. Security limitations

| Item | Detail |
|---|---|
| Client attestation HMAC | The shared value also ships inside the APK, so it is **not** a security boundary against a determined attacker. It is now configured from a single source and treated as an app identifier; Play Integrity (App Check) is the meaningful control. |
| Account deletion and object storage | The database cascade is complete and suite-asserted. Object deletion is best-effort: a storage outage does not block the database purge (so the account is genuinely gone), and the objects that could not be removed are reported for retry. Assets stored under a URL that is not a Nebula storage URL (for example a legacy provider host) cannot have their key derived and are skipped. |
| Exposed credentials | The development credentials were shared in plain text on 2026-10-29 and must be treated as compromised. See `docs/SECRETS_ROTATION.md` for the ordered rotation list; rotation has not been performed yet. |
| Keystore passwords in git history | `gradle.properties` previously contained release keystore passwords. They are removed from the working tree by this change but remain in git history; the keystore and those values must be rotated before a public release. |
| Hardcoded privileged identities | A previous build granted `admin`/`premium_plus` to two literal email addresses. Removed in Phase 1; any such grants must be made through an audited admin path instead. |
| Rate limiting | In-process limiters; they do not coordinate across instances. |
| Logging | Errors are redacted, but there is no structured JSON logger or centralised audit pipeline. |
| Object storage | Public read is served through a signed proxy route; bucket policy must be verified per environment before release. |

## 4. Maintainability limitations

- `NebulaRoomScreen.kt` (2,297 LOC), `HomeViewModel.kt` (1,617 LOC) and
  `NebulaChatDetailScreen.kt` (1,389 LOC) are monoliths.
- No Android unit or instrumentation tests.
- Manual dependency injection in `MainActivity`; repositories are singletons
  created per activity instance.
- No schema-migration strategy for MongoDB (index creation is implicit).

## 5. Infrastructure and cost limitations

- Render free tier sleeps on idle; MongoDB Atlas free tier has connection and
  storage caps; S3-compatible egress is billed.
- No CDN in front of media beyond the storage provider's own.
- No Redis, no queue, no multi-region deployment — intentional until measured
  load justifies them.

## 6. Explicitly unverified claims inherited from earlier notes

Earlier repository notes (`NEBULA_EXECUTION_CHECKLIST.md`, `QA_BASELINE.md`)
contain device-verification claims from a previous physical device. Those runs
cannot be reproduced here and are not treated as current evidence. This document
and `docs/FEATURE_MATRIX.md` record the state as measured today.
