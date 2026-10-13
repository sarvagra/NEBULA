# NEBULA Threat Model & Attack Vector Analysis

## 1. Overview & Trust Boundaries

The Nebula architecture separates untrusted user endpoints from authoritative application servers and databases:

```
[ Untrusted Zone: Android Client / Attackers ]
                     │  (HTTP / WebSocket / TLS)
                     ▼
[ Perimeter: Helmet / Strict CORS / Rate Limiter / Body Size Limit (100KB) ]
                     │
                     ▼
[ Auth Boundary: JWT Verification / Revocable Session Store ]
                     │
                     ▼
[ Authorization & Validation: Zod Schemas / Room Host Guard / DTO Scrubber ]
                     │
                     ▼
[ Trusted Data Store: MongoDB Replica Set / Private Atlas VPC ]
```

---

## 2. Threat Analysis & Mitigations

### 2.1 Modified APK / Reverse-Engineered Client
* **Threat**: Attacker unpacks and modifies the APK, removing client-side restrictions or injecting modified state.
* **Impact**: Potential feature unlocks or parameter manipulation.
* **Server Mitigation**: 
  * The server treats all client assertions as advisory or untrusted.
  * Server enforces subscription tier, room ownership, message rate limits, and membership.
  * Future Play Integrity / App Check integration will block non-certified APK signatures.

### 2.2 Forged Premium State & Subscription Fraud
* **Threat**: Attacker sends requests with `tier: "premium_plus"` or calls `/users/profile` with escalated parameters.
* **Impact**: Unauthorized access to premium themes, higher room participant limits, or cosmetics.
* **Server Mitigation**:
  * Profile update endpoint strictly ignores any client-supplied `tier` property.
  * `verifyGooglePlayPurchase` requires a cryptographically verifiable Google Play receipt token checked against Google Play Developer API.
  * Server-side entitlement endpoint (`/billing/entitlement`) derives perks directly from the database record.

### 2.3 Room Hijacking & Unauthorized Playback Control
* **Threat**: A participant in a room attempts to pause, play, seek, or change media playback without host permission.
* **Impact**: Disrupts communal watch/listen experience for all room members.
* **Server Mitigation**:
  * REST (`PUT /rooms/:id/media`) and WebSocket (`media:play`, `media:pause`, `media:seek`) handlers explicitly verify that `user.id === room.hostId`.
  * If non-host attempts playback commands, the request is rejected with `403 Forbidden` and logged.

### 2.4 Unauthorized Private Messaging & Spam
* **Threat**: Malicious user sends spam direct messages to arbitrary users across the platform.
* **Impact**: Harassment, spamming, and platform degradation.
* **Server Mitigation**:
  * Direct messages require an accepted friendship record (`FriendshipModel` status `'accepted'`) between the two users.
  * Outgoing message rate limiter restricts message frequency to 40 per minute.

### 2.5 OTP Abuse & Phone Brute Force
* **Threat**: Attacker targets phone authentication to incur high SMS costs (SMS pumping) or attempts brute-force guessing of the 6-digit OTP code.
* **Impact**: Financial loss from SMS charges, account takeover.
* **Server Mitigation**:
  * `otpRequestRateLimiter` restricts OTP requests to a maximum of 3 requests per 15 minutes per phone/IP.
  * `otpVerifyRateLimiter` allows a maximum of 5 attempts before invalidating the code.
  * OTP codes expire within 5 minutes.

### 2.6 NoSQL / Operator Injection
* **Threat**: Attacker sends JSON objects containing MongoDB operator keys (e.g. `{"phone": {"$gt": ""}}` or `{"$where": "..."}`).
* **Impact**: Authentication bypass or unauthorized data extraction.
* **Server Mitigation**:
  * `noSqlSanitizer` recursively inspects all request bodies, query strings, and route parameters.
  * Any key starting with `$` or containing `.` is immediately rejected with HTTP 400.
  * Parametric IDs are validated as 24-character hexadecimal strings via `isValidObjectId`.

### 2.7 WebSocket Abuse & Unauthenticated Sockets
* **Threat**: Attacker connects an unauthenticated WebSocket to eavesdrop on room chats or broadcast fake presence.
* **Impact**: Information leakage and unauthorized presence spoofing.
* **Server Mitigation**:
  * Socket.IO handshake strictly enforces token verification in `io.use`. Unauthenticated connections are dropped immediately with zero guest fallback.
  * Every room event verifies that the user is an active participant in `room.participants`.

### 2.8 Account & Username Enumeration
* **Threat**: Attacker scripts requests against `/users/check-username/:username` to harvest registered usernames or emails.
* **Impact**: User privacy loss, targeted credential stuffing.
* **Server Mitigation**:
  * `profileRateLimiter` restricts username lookups to 20 requests per 10 minutes.
  * Public profile DTOs never disclose emails, phone numbers, or account creation timestamps.

### 2.9 Malicious Media Metadata & Oversized Payloads
* **Threat**: Attacker posts massive JSON bodies (megabytes) or injects script tags into media titles.
* **Impact**: Server memory exhaustion (Denial of Service) or stored XSS on clients.
* **Server Mitigation**:
  * Express body size limit is capped at 100KB (returns HTTP 413).
  * Zod schemas enforce string length constraints (titles max 120 chars, descriptions max 200 chars).
  * Strict Content-Security-Policy (CSP) headers via Helmet.

### 2.10 Stolen Tokens & Session Management
* **Threat**: Token stolen via MITM or compromised device.
* **Impact**: Unauthorized account access.
* **Server Mitigation**:
  * Short-lived access tokens (7 days).
  * Revocable refresh token store in MongoDB (`RefreshTokenModel`). User can revoke specific or all sessions.
  * TLS/HTTPS enforced in production.

---

## 3. Remaining External Infrastructure Requirements

Before deploying to live production, the following external cloud configurations must be completed:

1. **Firebase Authentication / Google Cloud Console**:
   * Supply production `GOOGLE_CLIENT_ID` and `FIREBASE_PRIVATE_KEY` in production environment.
2. **Google Play Developer API**:
   * Generate and deploy `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` for real-time in-app billing verification.
3. **MongoDB Atlas Production Cluster**:
   * Configure a dedicated VPC peering connection or IP allowlist.
   * Create a least-privilege database user with `readWrite` access strictly scoped to the `nebula` database (never cluster admin).
4. **Play Integrity / App Check**:
   * Enforce Google Play Integrity cloud project verification to block modified APKs at the gateway.
5. **Production TLS / HTTPS Reverse Proxy**:
   * Deploy behind Cloudflare / AWS ALB / Nginx with TLS 1.3 certificates and automatic HTTP -> HTTPS redirection.
