# Nebula — Feature Matrix

Reference: Rave `com.wemesh.android` 9.0.58 (versionCode 2358), APKM bundle inspected
statically on 2026-10-29 (manifest, resource strings, dex class inventory, assets).
"Reference" rows describe only what is **observable statically**; no runtime
inspection of Rave was possible (see KNOWN_LIMITATIONS).

## Status legend

| Symbol | Meaning |
|---|---|
| ✅ | Implemented and verified (evidence recorded) |
| 🟡 | Implemented, not verified in this environment |
| 🟠 | Partially implemented |
| ❌ | Not implemented |
| ⚪ | Not observable in the reference APK |

"Verified" always names the evidence. API-level verification does not imply
on-device verification.

---

## A. Authentication and identity

| # | Capability | Reference (Rave) | Nebula | Evidence / gap |
|---|---|---|---|---|
| A1 | Google sign-in | Google, Facebook, X, Huawei ID, email link (`or_sign_in_with_email`) | ✅ Verified on device | Realme RMX3842, Android 16 (API 36): account picker → consent → Firebase user `kJNbYP…Fwd2` → backend session → onboarding. Evidence `qa_evidence/2026-10-29/signin_onboarding_step1.png` |
| A2 | Google-only policy | ❌ (Rave offers many providers) | ✅ Verified on device | Auth gate renders only "Continue with Google"; OTP/email/dev-login routes return 404 (asserted in the suite); `qa_evidence/2026-10-29/auth_gate_google_only.png` |
| A3 | Firebase ID token verified server-side | n/a | ✅ | `services/firebaseService.ts`, `authMiddleware` |
| A4 | Stable identity = Firebase UID | n/a | ✅ | `firebaseUid` unique sparse index; middleware matches UID only |
| A5 | Session refresh + revocation | ⚪ | ✅ | `POST /auth/refresh`, `RefreshToken` rows, 2-device cap, `SESSION_REVOKED` |
| A5a | Recovery from the 2-device cap | n/a | ✅ | `replaceOldestDevice` retires the least recently used session instead of a permanent lockout; default request is still refused. Suite-asserted: refusal by default, exactly one evicted, newest and surviving device retained |
| A6 | Session restore after restart | ⚪ (implied) | ✅ Verified on device | Force-stop → relaunch restored the authenticated profile straight to onboarding, no auth gate, no crash |
| A7 | Sign-out | `sign_out`, `sign_out_all` | ✅ | Firebase signOut + backend revoke + credential clear |
| A8 | Account deletion with grace period | 30-day deletion copy | ✅ API / 🟡 UI | `accountLifecycleController`; security suite asserts schedule, suspend, cancel, purge |
| A9 | Push notification permission | `notifications_permission_title` | 🟡 | `POST_NOTIFICATIONS` requested once after login in `MainActivity` |
| A10 | Anonymous access | n/a | ✅ none | Auth gate blocks all authenticated screens |

## B. Profiles

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| B1 | Unique `@handle` | `choose_handle`, cooldown copy | ✅ | Unique index; 6-month cooldown enforced and tested |
| B2 | Display name, bio, interests | `bio`, `bio_hint` | ✅ | `UpdateProfileSchema`, `NebulaEditProfileScreen` |
| B3 | Avatar + banner upload | `choose_avatar`, gallery | ✅ API / 🟡 UI | S3 presign, MIME/size validation, tier-capped history (1/3/5) |
| B4 | Public profile preview from chat/room | profile from participant | 🟡 | `NebulaPublicProfileScreen`, entry points in chat header + participants |
| B5 | Privacy: who sees connections | `set_all_privacy_*` tri-state | 🟠 | Only `isFriendCountPublic`; no per-field public/friends/hidden matrix |
| B6 | Profile gallery | `Gallery`, NSFW hiding | ❌ | Rave-only; not implemented |
| B7 | Presence (online/offline/custom) | `online`, `offline` | ✅ API / 🟡 device | Heartbeat + stale cutoff; presence DTO tested; custom status is tier-gated |
| B8 | Stats (rooms joined/hosted, hours) | `stats`, `Time Together` | ✅ | `UserSchema.stats`, shown on profile |

## C. Social graph

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| C1 | User search | `friends_search_hint` | ✅ | `GET /users/public/:username`, search UI in Friends |
| C2 | Friend request send/accept/decline/cancel | `friend_request_confirm`, `cancel_friend_request` | ✅ | `friends` module; notification cleanup asserted |
| C3 | Remove friend | `un_friend`, `un_friend_message` | ✅ | `DELETE /friends/*` |
| C4 | Block user | `block`, `block_title`, `unblock` | ✅ API / 🟡 UI | DM, history, invite and room join blocked server-side; `NebulaBlockedUsersScreen` |
| C5 | Report abuse | `report_abuse_body` | 🟠 | Route/UI exists for reporting a user; no moderator review queue surface |
| C6 | Online friends list | `ACTIVE NOW` concept | 🟡 | Friends tab shows presence; not re-verified |
| C7 | Request state reflected in every list | `cancel_friend_request` | ✅ | Rows show `Add` / `Requested` / `Accept` from the server's `relationship` field and flip on confirmation, not on the next refetch. Device-verified both directions |

## D. Direct messaging

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| D1 | Inbox with last message + time | `messages`, `recents_header` | ✅ | `getConversations`, `NebulaChatsScreen` |
| D2 | 1:1 conversation, persisted in MongoDB | `message_hint` | ✅ | `Message` model with `conversationId` |
| D3 | Real-time delivery | implied | ✅ / 🟡 device | The HTTP send path now broadcasts the stored message to both participants' private channels, and the app keeps a direct-message socket open while signed in (separate from the room socket), so a DM updates the thread and the inbox without waiting for a poll. Compile-verified; a two-account delivery test is still owed |
| D4 | Pagination / older history | implied | ✅ | `since` cursor on room and conversation history returns only the missed window; suite-asserted including an empty and an invalid cursor |
| D5 | Unread counts + read receipts | `read_time`, `Sent:` | ✅ | unread dot, `readAt`, read receipts UI |
| D6 | Delivery/read ticks | `read_time` | ✅ | double/filled tick indicators |
| D7 | Retry failed sends | `tap_retry_dm_send`, `resending_dm` | ✅ | Bubble carries `SENDING`/`SENT`/`FAILED`; a failed send is marked with a red `!` beside the bubble (tap the bubble or the mark to retry), never gets a delivery tick, and survives list refreshes and process death. Sending with no network fails in the same frame instead of hanging on a connect timeout, and the thread re-sends automatically when the network returns |
| D17 | Offline chat hint | `chat_offline_hint` ("Offline") | ✅ | The header shows `Offline` in red when the device has no network, a strip above the composer says messages will send on reconnect, and a picked photo/video is held with its own Retry instead of being uploaded into the void |
| D8 | Idempotent sends (no duplicates) | ⚪ | ✅ | Client now sends an `idempotencyKey` per message and reuses it on retry; unique partial index on `senderId+conversationId+idempotencyKey` |
| D9 | Edit message with history | `edit_dm`, `edited_at_dm` | ✅ | `PUT /chat/:id`, `editHistory`, tier edit limits |
| D10 | Unsend with tombstone (2 min) | `unsend_dm` | ✅ | `DELETE /chat/:id`, server-authoritative window |
| D11 | Image / GIF / video attachments | GIPHY picker, photos, videos, voice notes | ✅ images / 🟠 video+GIF / ❌ voice | 5 MB image, 30 MB video limits; voice notes not implemented |
| D12 | Spoiler media | implied (`PrivacyImageView`) | ✅ | `isSpoiler` + reveal UI |
| D13 | Message expiry | `message_expiry`, `expiry_when_seen` | ❌ | Rave feature; not implemented in Nebula |
| D14 | Auto-translate chat | `auto_translate_title` | ❌ | Not implemented (needs a translation provider) |
| D15 | Retention windows by tier | ⚪ | ✅ | 28/42/365 days, shorter of both participants; tested |
| D16 | E2E encryption | ⚪ | ❌ | Not implemented; not claimed |

## E. Watch rooms

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| E1 | Public room discovery | `public_rave`, `public_des` | ✅ | `GET /rooms`, Home/Rooms tabs |
| E2 | Private / invite-only rooms | `private_rave` | ✅ | `privacy: 'invite_only'` |
| E3 | Friends-only rooms | `FRIENDS ONLY`, `friend_des` | ✅ | `privacy: 'friends'` |
| E4 | Nearby rooms (geolocation) | `MapFragment`, `enable_location_permission_nearby` | ❌ | Deliberately not implemented (location permission not requested) |
| E5 | Search / filter rooms | `search_menu_title`, `trending` | ✅ | Search, category and tag filters plus discovery ranking (participants, activity recency, playback state). Busier-room-outranks-empty assertion in the suite |
| E6 | Create room (name, privacy, media, tags, limit) | `create_rave`, `pick_privacy_prompt` | ✅ | `CreateRoomSheet`, `CreateRoomSchema`, tier participant caps |
| E7 | Share/invite links | `copy_toast_msg`, `rave.watch/…` deep link | ✅ Verified on device | Rooms carry a shareable 8-character code (`GET /rooms/invite/:code`, refused rooms indistinguishable from unknown codes; suite-asserted). The header shares through the platform sheet and `nebula://room/<code>` / `https://nebula.app/room/<code>` resolve server-side and join (device-verified, run 4f). A room can also be sent into a friend's thread as a card |
| E8 | Host (leader) role + transfer | `give_leadership`, `transfer_leadership_title` | ✅ | `POST /rooms/:id/transfer-host` |
| E9 | Kick participant | `Are you sure you want to kick %s?` | ✅ | `DELETE /rooms/:id/participants/:id`, server-enforced |
| E10 | Mute / moderation states | `voice_mute`, `user_kicked` | 🟠 | `moderateRoomParticipant` + kick; mute affects voice only conceptually |
| E11 | Participant list + host badges | `%s joined`, `user_joined` | ✅ | room participants UI, join/leave system events |
| E12 | Room chat with system messages | `RaveChatFragment`, `user_joined` | ✅ | `chat:message`, `messageType: 'system'` |
| E13 | Emoji reactions / double-tap | `quick_reaction_title`, `reacted` | ✅ | `chat:reaction`, `TopReactionsSheet` equivalent |
| E14 | Room ended state / auto-destruct | ⚪ | ✅ | last participant leaves → room ended; asserted in sync test |
| E15 | Voice chat (VOIP) | `VOICE CHAT`, `libmediasoupclient` | ❌ | WebRTC SFU out of scope for the current build |
| E16 | Video/AR filters | `libmediapipe`, face models | ❌ | Out of scope |
| E17 | Queue / vote-to-skip | `add_queue`, `vote_to_skip`, `vote_queue` | 🟡 | Queue engine suite-asserted (add with link verification and a 20-item cap, strict-majority skip that withdraws on a second tap, host-only advance, host-or-adder removal, `queue:updated` broadcast). The in-room sheet and header entry are built and instrumented but not yet run on a device |
| E18 | Room polls | ⚪ | ✅ | `Poll` model, `poll:vote` socket, poll UI |
| E19 | Age verification (face scan) | `age_verification_iris_*` | ❌ | Not implemented; not attempted |

## F. Synchronised playback

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| F1 | Host-authoritative play/pause/seek | `paused_by_message`, `Now playing` | ✅ | `media:*` sockets; non-host rejected (sync test) |
| F1a | Ordered playback commands (no stale rewind) | ⚪ | ✅ | Every accepted host command increments a room version carried on every playback event; a command from an older generation is refused and the sender is corrected with `media:state`. Suite-asserted; Android client echoes the version and drops older inbound events (compile-verified only) |
| F2 | Shared position with timestamp estimate | implied | ✅ | `mediaState` + elapsed-time estimation |
| F3 | Drift correction without constant seeking | implied | ✅ | 0.65 s dead-band in WebView shim |
| F4 | Late-join sync | implied | ✅ | current `mediaState` embedded on join |
| F5 | Reconnect resync | implied | ✅ | Rejoin replays the live playhead (not the DB checkpoint); suite-asserted after a real socket disconnect/reconnect |
| F6 | Media end handling | `video_unavailable_not_leader` | ✅ | `media:end`, restart affordance |
| F7 | YouTube playback | `YoutubeGridFragment` | ✅ | IFrame API in WebView |
| F8 | Direct video URLs (Vimeo / mp4) | `WebGridFragment` | 🟡 | Server validates links; the room now plays `.m3u8`/`.mpd`/`.mp4`/`.webm` through Media3 with the same host-clock contract (500 ms samples, 700 ms guest dead-band). Compile- and unit-verified; not yet run on a device |
| F9 | Netflix / Disney+ / HBO / Crunchyroll / Prime / SoundCloud / Twitch / X / Drive | provider `.js` shims + provider OAuth | ❌ by design | Requires provider OAuth and DRM; explicitly out of scope, documented |
| F10 | Frame-accurate sync | ⚪ | ❌ | Not achievable with IFrame embedding; not claimed |
| F11 | Background/floating playback | `floating_video_player_title` | ❌ | Not implemented |

## G. Notifications and presence

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| G1 | FCM push for DMs | `notification_channel_chat` | 🟡 | Server send path, token registration, dead-token pruning and `notificationPrefs.directMessages` enforcement exist; a rotated token is now uploaded on next launch instead of being parked in preferences forever. Still no live delivery test between two devices |
| G2 | Push for friend requests / invites | implied | 🟡 | Same |
| G3 | Notification feed with actions | `friend_requests`, invitations | 🟡 | `NotificationsSheet`, swipe dismiss; not re-verified |
| G4 | Notification expiry (28 days) | ⚪ | ✅ | TTL index + 5-minute invite TTL |
| G5 | Presence heartbeat + stale cutoff | `online` offsets | ✅ | 25 s foreground pulse plus a 60 s server sweep that reclaims any account whose heartbeat stopped for 120 s, while never reclaiming a user holding a live socket (suite-asserted both ways) |
| G6 | Re-engagement messaging with consent | `review_message` style nudges | ✅ API | Consent + cadence + quiet hours asserted |
| G7 | Notification preferences | `notification_mesh_list_item` | 🟡 | Settings screen present |

## H. Platform, design, and quality

| # | Capability | Reference | Nebula | Evidence / gap |
|---|---|---|---|---|
| H1 | Dark-first themed UI | dark Rave UI | ✅ | 4 theme presets, token system |
| H2 | Bottom navigation (Home/Rooms/Chats/People) | bottom nav + profile | ✅ | `NABottomBar`, `NavSection` |
| H3 | Loading / empty / error states | `no_friends_found`, `please_wait` | 🟠 | Shared components exist but coverage per screen is uneven |
| H4 | Accessibility (contrast, touch targets, scaling) | ⚪ | 🟠 | No accessibility audit performed |
| H5 | Responsive layout (tablet/foldable) | ⚪ | 🟠 | Fixed 1-column Compose layouts |
| H6 | Offline read cache | ⚪ | ❌ | No offline message cache; requests fail visibly |
| H7 | Media caching (Coil + HTTP) | `Glide` | ✅ | Coil disk cache + signed URL TTL |
| H8 | Android automated tests | ⚪ | 🟡 | JVM specs cover media routing, invite parsing (including a spoofed card), drift tolerance and card selection; a Compose instrumented spec covers the queue sheet's vote/advance/remove semantics. The instrumented suite has not been run on a device yet |
| H9 | Secrets outside VCS | ⚪ | 🟠 | Keystore passwords were committed (Phase 1 fix); `.env` gitignored |
| H10 | Rate limiting | ⚪ | ✅ | Per-route limiters; asserted in suite |
| H11 | Server-side authorisation for every private resource | ⚪ | ✅ | Membership/ownership checks; IDOR tests in suite |
| H12 | Structured/redacted logs | ⚪ | 🟠 | Error handler redacts; no JSON logger |

## I. Reference features intentionally out of scope

Provider OAuth playback (Netflix, Disney+, HBO Max, Crunchyroll, Prime Video,
Tubi, Pluto, Twitch, X, SoundCloud, Rutube/VK), WebRTC voice/video rooms, AR
face filters, face-scan age verification, ad monetisation, provider-specific
DRM. These either require proprietary integrations we cannot lawfully
reimplement, are DRM-encumbered, or fall outside the product's stated scope.
