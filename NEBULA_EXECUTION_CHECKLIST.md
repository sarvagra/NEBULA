# Nebula execution checklist

This checklist tracks the supplied task list. `Final check with another account` means two separate authenticated app profiles on the connected Realme using the shared backend. Do not mark shared-state work PASS from compilation or same-account inspection alone.

| Task ID | Task | Edits done? | Final check with another account done? | Final check correct? | Status / priority | Modules / evidence |
|---|---|---:|---:|---:|---|---|
| N-001 | DM history is hidden by default; show an animated compact popup on explicit history tap; hide history for unsent messages | Yes | No | No | IN PROGRESS / P1 | Chat bubble; screenshot and tap/dismiss check |
| N-002 | Edit-message UI matches all four themes | In progress | No | No | IN PROGRESS / P1 | Chat detail; theme screenshots |
| N-003 | DM delivery is prompt and deduplicated across retries/reconnects | In progress | No | No | IN PROGRESS / P0 | API, repository, sockets; timestamped two-client delivery |
| N-004 | Retain basic-user DM history for four weeks | No | No | No | NOT STARTED / P1 | Message retention policy; date-boundary verification |
| N-005 | Retain Premium DM history for six weeks | No | No | No | NOT STARTED / P1 | Entitlement retention policy; authorization and date-boundary verification |
| N-006 | Retain Premium Plus DM history for twelve months | No | No | No | NOT STARTED / P1 | Entitlement retention policy; date-boundary verification |
| N-007 | Incoming sender avatar is visible in DM bubbles and correct after profile changes | Yes | No | No | IN PROGRESS / P0 | Chat API now prefers sender's current avatar; recipient screenshot pending |
| N-008 | Editing a message does not mark it read | In progress | No | No | IN PROGRESS / P0 | Read receipt API; unread count and persisted readAt checks |
| N-009 | DM edit limits: Basic once, Premium twice, Premium Plus twice; edits do not consume read state | Yes | No | No | IN PROGRESS / P0 | Server tier authorization; each-tier API checks |
| N-010 | Recipient can inspect prior edited text on demand; no history for unsent messages | Yes | No | No | IN PROGRESS / P1 | DTO and popup UI; cross-account interaction pending |
| N-011 | Sender can unsend only within two minutes; recipient sees tombstone and no attachment | Yes | No | No | IN PROGRESS / P0 | Server and app; before/after-window checks pending |
| N-012 | Tapping DM header name/avatar opens the correct public profile | In progress | No | No | IN PROGRESS / P0 | Chat header navigation; screenshot and profile identity check |
| N-013 | DM header reflects profile status (dynamic presence, offline, or custom) | Yes | No | No | IN PROGRESS / P1 | Profile DTO now carries configured status separately from presence; cross-account update pending |
| N-014 | DMs support GIF/image up to 5 MB and video up to 30 MB; reject larger/unsupported files | Yes | No | No | IN PROGRESS / P0 | GIF bytes preserved; MP4/WebM and size checks implemented; device boundary tests pending |
| N-015 | Image/video attachments render for the recipient with upload progress and spoiler reveal | Yes | No | No | IN PROGRESS / P0 | Media DTO/rendering updated; two-account transfer and screenshot pending |
| N-016 | Remove hard-coded GIF choices; device keyboard GIF/emoji input remains usable | Yes | No | No | IN PROGRESS / P1 | Composer; keyboard GIF test pending |
| N-017 | Attachment chooser stays anchored beside the composer and media actually sends | Yes | No | No | IN PROGRESS / P0 | Compose placement/upload updated; screen recording and recipient check pending |
| N-018 | Message sender avatar and bubble align consistently | In progress | No | No | IN PROGRESS / P2 | Compose layout; theme screenshots |
| N-019 | Profile banner applies to server immediately, updates own profile immediately, and appears to another account | In progress | No | No | IN PROGRESS / P0 | Asset upload/apply, profile DTO, cache/socket; both profiles |
| N-020 | Banner editor preview, aspect ratio, and status controls do not conflict | In progress | No | No | IN PROGRESS / P1 | Edit profile UI and persistence; device checks |
| N-021 | Profile asset history shows previews; retain latest one for Basic, three for Premium, five for Premium Plus | No | No | No | NOT STARTED / P1 | Asset API/storage/tier policy; tier matrix |
| N-022 | Removing an asset removes it from history and user-visible selection | No | No | No | NOT STARTED / P1 | Asset delete endpoint/UI; server and client checks |
| N-023 | Notifications can be swiped left/right to dismiss; invitation cards are minimal without an X button | Yes | No | No | IN PROGRESS / P1 | Fixed release-offset detection; sheet expanded to 88% height; gesture check pending |
| N-024 | Keep notifications for at most four weeks; room invites expire after five minutes unless dismissed sooner | Yes | No | No | IN PROGRESS / P0 | TTL and query expiry rules implemented; expiry boundary checks pending |
| N-025 | Notification list expands to full window on upward scroll and restores prior screen on back | No | No | No | NOT STARTED / P2 | Sheet/navigation; UI automation |
| N-026 | Notification list refreshes at top pull with theme-matched indicator; same pull-to-refresh works on Home, Rooms, Chats, Friends, Profile | No | No | No | NOT STARTED / P1 | Shared refresh component; all-tab interaction/screenshots |
| N-027 | DM, room invite, and friend request create Android status-bar notifications | Yes | No | No | IN PROGRESS / P0 | FCM token registration, channel/service, push send paths, and one-time Android 13+ permission prompt implemented; live delivery pending |
| N-028 | Catchy but controlled re-engagement notifications are scheduled only with user consent and appropriate rate limits | No | No | No | NOT STARTED / P2 | Notification scheduler/preferences; permission and cadence checks |
| N-029 | Public-profile relationship actions appear below tags in the same sequence as own profile | No | No | No | NOT STARTED / P1 | Profile composables; all relationship states/screenshots |
| N-030 | Profile status is distinct from app presence: dynamic is active only while app is active; otherwise offline; custom is shown as set | No | No | No | NOT STARTED / P0 | Presence service/API; original/clone and lifecycle checks |
| N-031 | Profile/entitlement controls remain isolated by Basic, Premium, and Premium Plus server rules | No | No | No | NOT STARTED / P0 | Server authorization and entitlement source; negative API tests |
| N-032 | Username can change only once per six months across all tiers | No | No | No | NOT STARTED / P1 | User schema/controller/index; concurrent and time-boundary checks |
| N-033 | Public profile removes centered @username header; profile spacing and avatar/boundary are polished | No | No | No | NOT STARTED / P2 | Profile UI; all themes/screenshots |
| N-034 | Public-profile settings/block UI is coherent in all four themes and blocking prevents DM/invite/contact | No | No | No | NOT STARTED / P0 | Profile settings, server authorization; two-account negative checks |
| N-035 | Accepting request in Friends updates notification state immediately and pending requests remain accessible | No | No | No | NOT STARTED / P1 | Friendship/notification state; two-account check |
| N-036 | Home profile overlay has translucent liquid-glass treatment and renders banner/avatar correctly | No | No | No | NOT STARTED / P2 | Overlay/image cache; screenshot across themes |
| N-037 | Welcome-back background matches selected theme instead of gray in dark theme | No | No | No | NOT STARTED / P2 | Auth UI; four-theme screenshots |
| N-038 | Blocked-users settings UI matches settings components and all four themes | No | No | No | NOT STARTED / P2 | Settings screen; theme screenshots |
| N-039 | Settings > Account supports OTP-verified deletion scheduled after 30 days; login during grace period suspends deletion and informs user | No | No | No | NOT STARTED / P0 | Lifecycle API, OTP, cleanup job, client; persistence/security checks |
| N-040 | Settings > Google Account logs out of Nebula without conflating account deletion | No | No | No | NOT STARTED / P1 | Navigation and auth; lifecycle check |
| N-041 | Home/Friends active-now status is consistent with app presence and not redundantly duplicated | No | No | No | NOT STARTED / P1 | Presence DTO and views; cross-account lifecycle |
| N-042 | Home, Rooms, Chats, Friends, Profile lists scroll to the last item and allow it to move into lower-middle viewport | No | No | No | NOT STARTED / P2 | Scroll containers; device interaction |
| N-043 | Profile tab banner sizing is appropriate; status label does not literally show “dynamic”; remove QR and avatar-pen artifact | No | No | No | NOT STARTED / P2 | Profile tab; screenshots |
| N-044 | Friend suggestions/request UI supports all pending relationship states consistently | No | No | No | NOT STARTED / P1 | Friends API/UI; accepted/pending/withdrawn checks |
| N-045 | Do not reset/delete MongoDB users until user confirms both new accounts are created (“okay done”) | Not applicable | Not yet authorized | No | BLOCKED / P0 | Explicit user sequencing; preserve DB until confirmation |
| N-046 | Create-room sequence is privacy → source → source selection → strict participant limit → tags → create | No | No | No | NOT STARTED / P1 | Create-room Compose; screenshot and limit checks |
| N-047 | Video links accept only playable video sources; reject image/non-video/unavailable links with a clear one-line error | No | No | No | NOT STARTED / P0 | Link resolver/probe/server validator; public Drive/Vimeo cases |
| N-048 | Drive/Vimeo supported playback stays synchronized to the host for join, play/pause, seek, switch, end, reconnect | No | No | No | NOT STARTED / P0 | Server and player sync; two-client drift checks |
| N-049 | Preserve Nebula identity, established API compatibility, and existing user data while completing the tasks | In progress | No | No | IN PROGRESS / P0 | Cross-cutting review; regression and migration evidence |
| N-050 | Research primary docs/reference workflows and record decisions/licenses without copying proprietary assets/code | No | Not applicable | No | NOT STARTED / P1 | Sources section below |
| N-051 | Build and test real Android/backend app, then complete opposite-direction two-account checks after user account setup | In progress | No | No | IN PROGRESS / P0 | Connected Realme; current sessions must remain untouched |

## Evidence log

| Date | Test / observation | Evidence | Result |
|---|---|---|---|
| 2026-10-14 | Current checkout and existing work reviewed; message/profile/notification implementation has local changes | Git HEAD `5786c1f`; release signer fingerprint matches the installed `nebula.apk` | In progress |
| 2026-10-14 | Previous requested APK install on Realme, launched original user 0 and clone user 999; DM screen displayed avatar, status, and edit UI; tapping header opened public profile with banner/avatar | `/tmp/nebula-dm-detail-original.png`, `/tmp/nebula-dm-profile-original.png`, `/tmp/nebula-dm-original.png`, `/tmp/nebula-dm-clone.png` | UI evidence only; not two-account behavior verification |
| 2026-10-14 | TypeScript build and Kotlin debug compilation passed for prior committed DM changes | `npm run build` in `server`; `./gradlew :app:compileDebugKotlin` | PASS for compilation only |
| 2026-10-14 | Render health after prior deployment responded HTTP 200 | `/tmp/nebula-health-deploy.json` | Healthy; does not prove latest uncommitted correction is deployed |
| 2026-10-14 | Server TypeScript build and Android Kotlin debug compilation passed after DM media/status and notification changes | `npm run build`; `./gradlew :app:compileDebugKotlin` | PASS for compilation only |
| 2026-10-14 | Server security suite passed | `npm test` in `server`; 18 passed, 0 failed | PASS; does not cover live FCM or DM media scenarios |

## Research sources

| Reference | Decision used |
|---|---|
| [Firebase Android FCM setup](https://firebase.google.com/docs/cloud-messaging/android/get-started) | Register installation tokens and create a notification channel; request Android 13+ notification permission once. |
| [Firebase Admin FCM sending](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk) | Send DM, friend, and room notifications through the Admin SDK and discard invalid device tokens. |
| [MongoDB TTL indexes](https://www.mongodb.com/docs/manual/core/index-ttl/) | Expire invite documents at `expiresAt`; queries also filter expiry so cleanup lag cannot show stale invites. |
| [Rave on Google Play](https://play.google.com/store/apps/details?id=com.wemesh.android) | Reference for combining supported shared playback and group messaging; no proprietary assets or code copied. |
| [Beacon source](https://github.com/Androtim/Beacon) | Reference for open-source Socket.IO watch-room workflows; patterns only, no copied code. |

## Data reset gate

No database reset, user deletion, or destructive data operation is authorized before the user creates a distinct account in each installed app and sends “okay done.” Continue implementation and non-destructive checks while waiting.
