# NEBULA Security Architecture & Policies

## 1. Security Architecture Principles

Nebula operates under a **Zero-Trust, Server-Authoritative** security architecture. The Android client application is treated as an untrusted client in an untrusted environment.

* **Server-Authoritative State**: Client requests cannot dictate user tiers (`tier`), roles (`role`), ban status (`isBanned`), room permissions (`isHost`), or stats. All identities, entitlements, and permissions are determined exclusively by server-side database records.
* **No Guest/Anonymous Access**: All protected REST API routes and Socket.IO connections strictly require authenticated, signed, and unexpired JWT access tokens.
* **Zero Secret Leakage**: Service-account keys, database connection strings, JWT signing secrets, and third-party API keys are strictly forbidden from client builds and version control.

---

## 2. Authentication & Session Management

### Access & Refresh Token Architecture
* **Access Tokens**: Short-lived (7 days), signed with HMAC SHA-256 (`JWT_SECRET`). Payload includes user ID, handle, display name, tier, and role.
* **Refresh Tokens**: Cryptographically generated 40-byte random tokens stored as SHA-256 hashes in MongoDB (`RefreshToken` collection).
* **Revocability**: Refresh tokens can be individually revoked on logout or universally revoked during security resets (`AuthService.revokeAllUserSessions`).
* **MongoDB TTL Index**: Expired refresh tokens are automatically purged by MongoDB background workers.

---

## 3. Defense-in-Depth Mechanisms

### Input Sanitization & NoSQL Injection Protection
* Global `noSqlSanitizer` recursively rejects any query, body, or param containing keys prefixed with `$` (e.g. `$gt`, `$ne`, `$where`, `$regex`) or object path separators (`.`), preventing NoSQL operator injection.
* Prototype pollution attempts (`__proto__`, `constructor`, `prototype`) are immediately rejected with HTTP 400.

### Strict DTO Transformation & Data Privacy
* Explicit response DTO transformers (`toPublicUserDTO`, `toPrivateUserDTO`, `toPublicRoomDTO`, `toMessageDTO`) guarantee that private identifiers (emails, phone numbers, Google subject IDs, auth hashes) are never returned in public API responses.

### Request Payload Limits & Timeouts
* Maximum HTTP JSON payload is strictly enforced at **100KB** via Express body-parser, preventing memory exhaustion attacks. Oversized payloads return HTTP 413.
* HTTP request timeout is enforced at **15 seconds** to prevent slow-loris connection holding.

### Security Headers & CORS
* **Helmet**: Enforces HSTS, MIME sniffing protection (`nosniff`), XSS filtering, frameguard, and CSP.
* **CORS**: In production, strictly validates origin against `CORS_ALLOWED_ORIGINS`. Wildcard `*` is prohibited in production.

---

## 4. Rate Limiting Matrix

| Endpoint Group | Window | Limit | Target Key |
| :--- | :--- | :--- | :--- |
| **Global API** | 1 min | 120 req | IP |
| **Authentication (Login/Google)** | 15 min | 15 req | IP + User |
| **OTP Request** | 15 min | 3 req | Phone + IP |
| **OTP Verification** | 15 min | 5 req | Phone + IP |
| **Session Refresh** | 15 min | 15 req | IP |
| **Room Creation** | 15 min | 5 rooms | User ID |
| **Room Join** | 5 min | 30 joins | User ID |
| **Chat Messages** | 1 min | 40 msgs | User ID + Room |
| **Friend Requests** | 15 min | 20 req | User ID |
| **Poll Voting** | 1 min | 10 votes | User ID |
| **Billing Verification** | 10 min | 6 req | User ID |

---

## 5. Vulnerability Disclosure & Reporting

If you identify a security vulnerability in Nebula, do not post publicly or open public GitHub issues. Please report responsibly to:
* **Email**: `security@nebula.social`
* **Response SLA**: Initial triage within 24 hours; remediation patch within 72 hours for critical vulnerabilities.
