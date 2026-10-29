# Nebula — Connected-Device Test Plan

Every case records: device, build SHA, steps, expected, actual, evidence path.
A case is PASS only if it was executed on the stated device. Compilation is not
a substitute.

## 1. Environment (recorded 2026-10-29)

| Item | Value |
|---|---|
| ADB | `/Users/sarvagra/Library/Android/sdk/platform-tools/adb` (1.0.41) |
| Physical device | **Realme RMX3842**, Android 16 (API 36), serial `ea8bd6e0`, two Android users (0 and 999/MultiApp) — attached mid-session |
| Emulator | `Pixel_PA_API34` — Android 14 (API 34), arm64-v8a, `default` image |
| Google Play services on emulator | **Absent** (`PlayStore.enabled=no`), so the emulator cannot run Google sign-in |
| JDK | OpenJDK 21.0.10 (Homebrew) — not on `PATH` by default |
| Build tools | 34.0.0 |
| App package | `com.nebula.app` |
| Test accounts | Two authenticated Google profiles previously used on the Realme; not reachable in this environment |

Consequence: authentication is executable on the physical Realme (which has
Google Play services) and was verified there. Push and two-account cases still
need a second signed-in device/profile.

## 2. Preconditions

1. Backend reachable from the device (`API_BASE_URL`; emulator uses `10.0.2.2`).
2. `google-services.json` matches the signing certificate SHA-1.
3. `adb reverse tcp:5001 tcp:5001` (or LAN URL) for local API.
4. Cleared logcat before each case: `adb logcat -c`.

## 3. Cases

### Authentication

| ID | Case | Steps | Expected |
|---|---|---|---|
| AUTH-01 | Auth gate is Google-only | Fresh install → launch | Only a Google sign-in action is offered; no email/OTP/password fields |
| AUTH-02 | Google account picker | Tap sign-in with GMS present | System account picker appears |
| AUTH-03 | Sign-in completes | Choose account, consent | Backend returns session; onboarding or Home opens |
| AUTH-04 | Backend rejects forged token | `POST /api/v1/auth/google` with junk token | 401, no session issued |
| AUTH-05 | Session restore | Force-stop, relaunch | Restores without re-prompt |
| AUTH-06 | Revoked session | Sign out on device B / revoke in settings | Device A receives 401 `SESSION_REVOKED` and returns to the gate |
| AUTH-07 | Sign-out | Profile → sign out | Firebase signed out, tokens cleared, gate shown |
| AUTH-08 | Account deletion | Request → confirm → sign in during grace | 30-day schedule, suspension message, account restored |
| AUTH-09 | Removed endpoints are gone | `POST /auth/otp/request`, `/auth/dev-login` | 404 |
| AUTH-10 | Device-cap recovery | Sign in on a third device while two stale sessions exist | First attempt refused with `DEVICE_LIMIT_REACHED`; the app retries with `replaceOldestDevice`, the least recently used session is retired, and the sign-in completes with a visible "signed out your oldest device" notice |

### Onboarding and profile

| ID | Case | Expected |
|---|---|---|
| PROF-01 | Complete onboarding (name, handle, DOB, gender, photo) | Profile persisted; `isOnboardingCompleted` true after restart |
| PROF-02 | Handle uniqueness | Taken handle rejected server-side with a clear message |
| PROF-03 | Handle cooldown | Second change within 6 months rejected with next-change date |
| PROF-04 | Avatar/banner upload | Upload succeeds; other account sees the new image |
| PROF-05 | Oversized/invalid image | Rejected with a specific error, no partial write |

### Social graph

| ID | Case | Expected |
|---|---|---|
| SOC-01 | Search user | Result opens public profile |
| SOC-02 | Friend request A→B | B sees request card + notification |
| SOC-03 | Accept | Both sides show friendship; request card clears |
| SOC-04 | Block | Blocked user cannot DM, invite, or join host's room (403) |
| SOC-05 | Report | Report recorded; no client-side fake success |
| SOC-06 | Row state after sending a request | Label flips to `Requested` at once, tapping it withdraws, and a fresh server search agrees |

### Direct messaging

| ID | Case | Expected |
|---|---|---|
| DM-01 | Send text A→B | Appears on B in real time |
| DM-02 | Persistence | Both clients show history after cold restart |
| DM-03 | Third-party access | Account C gets 403/404 for A↔B conversation |
| DM-04 | Retry after network drop | Message marked pending, then sent exactly once |
| DM-05 | Unread → read | Unread dot clears only after opening the thread |
| DM-06 | Edit + history | Recipient sees edited text and, on demand, prior version |
| DM-07 | Unsend inside/outside 2 min | Tombstone inside; rejection outside |
| DM-08 | Image/GIF/video attachment | Recipient renders media; oversize rejected |
| DM-09 | Pagination | Older page loads without duplicates or gaps |
| DM-10 | Reconnect backfill | Messages sent while offline appear after reconnect, no duplicates |

### Rooms

| ID | Case | Expected |
|---|---|---|
| ROOM-01 | Create public room | Appears in discovery for another account |
| ROOM-02 | Create invite-only room | Not discoverable; invitation required |
| ROOM-03 | Join/leave | Participant counts update for all members |
| ROOM-04 | Kick | Kicked user removed and cannot rejoin |
| ROOM-05 | Host transfer | New host controls playback; old host is a normal participant |
| ROOM-06 | Non-host playback command | Rejected; no state change |
| ROOM-07 | Room chat | Real-time delivery; system messages for join/leave |
| ROOM-08 | Last participant leaves | Room ends; no ghost room in discovery |
| ROOM-09 | Full room | Join rejected with a clear capacity message |

### Playback synchronisation

| ID | Case | Expected |
|---|---|---|
| SYNC-01 | Join mid-playback | Late joiner lands within the drift dead-band of the host |
| SYNC-02 | Play/pause | All clients change state within ~1 s |
| SYNC-03 | Seek | All clients seek to the same position |
| SYNC-04 | Media switch | All clients load the new media |
| SYNC-05 | Reconnect | Rejoining client resynchronises without forking state |
| SYNC-06 | Media end | End state shown once; host can restart |
| SYNC-07 | Unavailable media | Explicit error, not a silent black screen |
| SYNC-08 | Reordered playback command | A seek/pause built before a newer command must not rewind the room; the sending client is corrected to the authoritative position |
| SYNC-09 | Chat backfill after a socket drop | Cut the network for ~30 s while a peer sends messages; on reconnect the missed messages appear in order, exactly once |

### Notifications and presence

| ID | Case | Expected |
|---|---|---|
| NOTIF-01 | DM push (app backgrounded) | Status-bar notification; tap opens the thread |
| NOTIF-02 | Permission denied | App still functions; no repeated prompts |
| NOTIF-03 | Presence transition | Foreground → online, background → offline for the other account |
| NOTIF-04 | Crash/kill | Presence flips to offline within the 120 s stale cutoff even though no disconnect handler ran |
| NOTIF-05 | Invite expiry | Invite expires after 5 minutes |

### Stability

| ID | Case | Expected |
|---|---|---|
| STAB-01 | Cold start | No crash; no ANR; logcat free of fatal exceptions |
| STAB-02 | Rotation / process death | State restores; in-flight send not duplicated |
| STAB-03 | Airplane mode | Explicit offline states, no fabricated successes |
| STAB-04 | Low memory | No OOM from image/WebView caches |

## 4. Run log — 2026-10-29

Device: Realme RMX3842, Android 16 (API 36), user 0. Build: debug APK
`1b71d884d5372ddfc5491d5192321339cd95533541b526c05c8f82c15c12bc0c`.

| Case | Result | Evidence / observation |
|---|---|---|
| AUTH-01 Auth gate is Google-only | **PASS** | UI dump shows only `Continue with Google` and `Google sign-in · encrypted in transit`; `qa_evidence/2026-10-29/auth_gate_google_only.png` |
| AUTH-02 Google account picker | **PASS** | GMS `Choose an account for NEBULA` sheet listed four real accounts |
| AUTH-03 Sign-in completes | **PASS** | Consent → Firebase user `kJNbYP…Fwd2` → backend profile → `STEP 1 OF 7 · Welcome to Nebula`; `qa_evidence/2026-10-29/signin_onboarding_step1.png` |
| AUTH-05 Session restore | **PASS** | `am force-stop` then cold start landed on onboarding without the auth gate; no `FATAL EXCEPTION` in logcat |
| AUTH-09 Removed endpoints are gone | **PASS (API)** | Suite asserts `/auth/otp/request`, `/auth/otp/verify`, `/auth/dev-login` → 404 |
| AUTH-04 Forged token rejected | **PASS (API)** | Suite asserts 401 for an unverifiable Firebase ID token |

Observation during the run: signing in with the first account
(`sarvagras977@gmail.com`) was refused with `DEVICE_LIMIT_REACHED` because two
older sessions from previous installs are still active. The limit is intentional
and covered by the suite, so the run used a third account that had no session.
This does mean a user who reinstalls the app can be locked out until an old
device revokes a session — see KNOWN_LIMITATIONS.

Not executed in this run: AUTH-06/07/08 (need a second live device for the
revocation hand-off), all PROF/SOC/DM/ROOM/SYNC/NOTIF/STAB cases, and any
release-signed build.

## 4b. Run log — 2026-10-29 (run 2: friend-state reflection and DM send queue)

Device: Realme RMX3842, Android 16 (API 36), user 0, account `@alpha_raven`.
Backend: deployed Render instance (`nebula-73kb.onrender.com`). Build: debug APK
`c11eef2d4b98804fb9a80fdbf75404708114d03fdd5e42eeaedc6b2aec0c801a`.

| Case | Result | Evidence / observation |
|---|---|---|
| SOC-06 Send then reflect | **PASS** | Tapping `Add` on a search row flipped it to `Requested` within 1 s; the matching suggested-people row flipped too |
| SOC-06 Server agreement | **PASS** | Re-running the query (fresh `GET /friends/search`, not the cached list) still returned `request_sent`, so the row matched persisted state |
| SOC-06 Withdraw | **PASS** | Tapping `Requested` withdrew the request; a fresh search then returned `relationship: none` (`Add`) |
| DM-04 Send with no network | **PASS** | Message stayed in the thread as `Not sent · Tap to retry` instead of vanishing or pretending to send; `qa_evidence/2026-10-29/dm_failed_bubble.png` |
| DM-04 Unsent survives sync | **PASS** | Network restored for 12 s: the thread re-synced from the server (which did not have the message) and the unsent bubble remained; `dm_unsent_survives_sync.png` |
| DM-04 Retry | **PASS** | Tapping the marker went `Sending…` → delivered marker removed; reopening the thread from server history showed the message exactly once |

Defect found and fixed during this run: the delivery marker was first drawn on top of
the message text, because the bubble's content slot is a Box. It is now stacked in a
Column. The screenshot in `dm_failed_bubble.png` is from the fixed build.

Test artefacts left in the `@alpha_raven` ↔ `@zepetor` thread: `offline_probe_2`
and `offline_probe_3`, deliberately kept as evidence that both the retry path and
the queueing path persisted. A test friend request was withdrawn, not left pending.

Still not executed: DM-01/DM-03/DM-05–DM-10, SOC-02/03/04 on two accounts, all
ROOM/SYNC cases, and any release-signed build.

## 4c. Run log — 2026-10-29 (run 3: automatic retry and queue persistence)

Device: Realme RMX3842, Android 16 (API 36), user 0, account `@alpha_raven`.
Build: debug APK `b7877c9a1d14904aacc552962dc38d0090c7851b34400e336bf005411f86ac00`.

| Case | Result | Evidence / observation |
|---|---|---|
| DM-04 Send offline, reconnect, no tap | **PASS** | Bubble went `Not sent` → `Sending…` → delivered on its own within one sync tick (4 s) of the radio returning |
| DM-04 Unsent survives process death | **PASS** | Sent with Wi‑Fi and data off, `am force-stop`, relaunched with the radio still off: the thread reopened showing `restart_probe` as `Not sent · Tap to retry`; `dm_unsent_after_process_death.png` |
| DM-04 Auto-send after restore | **PASS** | Radio restored, no interaction: the restored message sent in ~2 s; `dm_auto_retry_after_reconnect.png` |
| DM-04 No duplicates | **PASS** | After a cold fetch from server history each probe message appears exactly once, including one whose first attempt was interrupted |

### Defects found and fixed during this run

1. **Unsent message could be stranded in `Sending…` forever.** A coroutine cancelled
   mid-send left the state at `SENDING`, and the retry filter only looked at `FAILED`.
   An attempt older than 20 s is now treated as dead and retried; cancellation is no
   longer converted into a failure.
2. **The retry ran on a screen-scoped job.** `refreshActiveConversation` is called from
   the thread's sync loop, which is cancelled whenever the screen is re-entered; the
   `POST /chat` was aborted mid-flight (`StandaloneCoroutine was cancelled` in logcat).
   The flush now runs on the repository's own scope.
3. **Permanent rejections retried forever.** A 4xx (other than 408/429) now stops
   automatic retries for that message; an explicit tap still forces one attempt.

Observation: the first run of this sequence looked like a hang (2 minutes at `Sending…`)
because the free-tier backend was cold-starting under repeated retries. Instrumented
logs separated that from defect 2 — the send itself failed in 2 ms with a cancellation,
not a timeout.

Not executed in this run: DM-01/DM-03/DM-05–DM-10, all SOC two-account cases, all
ROOM/SYNC cases, background delivery while the app is closed.

## 4d. Suite run — 2026-10-30 (playback ordering, chat backfill, presence TTL)

Scope note: these cases were exercised through the in-process realtime suite
(`npm run test:sync`, isolated in-memory MongoDB), **not** on a device. The
suite drives two authenticated socket clients against the real server code, so
the server behaviour is measured; the Android side of SYNC-08/SYNC-09 is
compile-verified only until a second live client exists.

| Case | Result | Evidence / observation |
|---|---|---|
| SYNC-08 Stale playback command refused | **PASS (suite)** | A seek at 30 s admitted (version incremented); a second seek built on the previous version was refused and the sender received `media:state` with `rejected: true` and the authoritative position; no rewind reached the other client |
| SYNC-08 Out-of-order sample | **PASS (suite)** | A host sample whose clock predated the accepted one was dropped; a late joiner still received a playhead at or after the newest accepted sample |
| SYNC-09 Chat backfill | **PASS (suite)** | Messages sent while the other client's socket was down were all returned by `?since=`, a future cursor returned none, and `since=not-a-date` returned 400 |
| NOTIF-04 Presence TTL | **PASS (suite)** | A user left `online` with a 10-minute-old heartbeat was reclaimed; a user holding an open socket with an equally old heartbeat was not |

Totals for this run: `npm run test:sync` 32 assertions passed (adds room-discovery
ranking), `npm test` 55/55 passed (adds device-cap recovery), `:app:assembleDebug`
BUILD SUCCESSFUL. `npm audit` reports 5 advisories, bounded as described in
KNOWN_LIMITATIONS.

## 4e. Run log — 2026-10-30 (device-cap recovery, on device)

Device: Realme RMX3842, Android 16 (API 36), user 0. Build: debug APK built from
the working tree (`:app:assembleDebug`, installed with `adb install -r`).
Backend: **local server from this working tree** on `127.0.0.1:5001` via
`adb reverse`, because the deployed instance still runs the previous code.

| Case | Result | Evidence / observation |
|---|---|---|
| AUTH-10 precondition | **PASS** | The account `sarvagras977@gmail.com` had exactly two active sessions (`android_29…` created 2026-10-28, `android_e8…` created 2026-10-29) and the app held a third, new device identity (`android_probe_3010301`) |
| AUTH-10 recovery | **PASS** | Signing in with the new device identity completed straight through to `Welcome back, Sarvagra Singh` on the first attempt reachable to the client; no `Device limit reached` surface was shown |
| AUTH-10 single eviction | **PASS** | Post-sign-in database state: `active sessions: 2` — `android_e8…` (kept) and `android_probe_3010301` (new). The least recently used `android_29…` session was revoked; nothing else was touched |
| AUTH-05 session still valid after restoring production URL | **PASS** | With the local override removed the app relaunched straight to Home as `@sarvagra_s` (no auth gate, empty crash buffer) |

Observation worth recording: the first sign-in attempt during this run appeared to
fail with `Device limit reached` because the app was still pointed at the deployed
instance, which did not yet accept `replaceOldestDevice`. Against the working-tree
server the same flow succeeded.

### 4e.1 Production re-run after deploy (same day)

The backend was then deployed (`main` = `ccaf7e5`, confirmed by
`GET /health → revision: ccaf7e5`, not by the push alone) and the same case was
repeated against production with the app on its default URL and a fourth new
device identity (`android_live_CCAF7E5`).

| Case | Result | Evidence / observation |
|---|---|---|
| AUTH-10 in production | **PASS** | Sign-in reached `Welcome back, Sarvagra Singh` with two sessions already active — no device-limit surface |
| AUTH-10 single eviction in production | **PASS** | Active sessions after: `android_probe_3010301` + `android_live_CCAF7E5`; `android_e8…` (created 2026-10-29T19:37Z, the least recently used) revoked at 19:51:33Z. Exactly one session retired |
| AUTH-05 after production sign-in | **PASS** | `Continue` → Home as `@sarvagra_s`; empty crash buffer |

## 5. Evidence convention

Store screenshots/UI dumps under `qa_evidence/<date>/` with the case ID in the
filename, and the installed APK SHA-256 in the run log.
