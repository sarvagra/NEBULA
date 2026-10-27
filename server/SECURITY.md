# NEBULA Security Architecture & Policies

## 1. Security Architecture Principles

Nebula operates under a **Zero-Trust, Server-Authoritative** security architecture. The Android client application is treated as an untrusted client in an untrusted environment.

* **Server-Authoritative State**: Client requests cannot dictate user tiers (`tier`), roles (`role`), ban status (`isBanned`), room permissions (`isHost`), or stats. All identities, entitlements, and permissions are determined exclusively by server-side database records.
* **No Guest/Anonymous Access**: All protected REST API routes and Socket.IO connections strictly require authenticated, signed, and unexpired JWT access tokens.
* **Zero Secret Leakage**: Service-account keys, database connection strings, JWT signing secrets, and third-party API keys are strictly forbidden from client builds and version control.

---

## 2. Authentication & Multi-Device Session Management

### Access & Refresh Token Architecture
* **Access Tokens**: Short-lived (7 days), signed with HMAC SHA-256 (`JWT_SECRET`). Payload includes user ID, handle, display name, tier, role, persistent `deviceId`, and database `sessionId`.
* **Refresh Tokens**: Cryptographically generated 40-byte random tokens stored as SHA-256 hashes in MongoDB (`RefreshToken` collection).
* **Strict 2-Device Concurrency Cap**: Accounts are strictly limited to at most 2 active devices. Attempting to log into a 3rd device without revoking an existing session returns `403 DEVICE_LIMIT_REACHED`.
* **Stateful Real-Time Revocability**: When a session is logged out or remotely revoked from Settings > Account, `authMiddleware` validates `sessionId` against active records in `RefreshTokenModel`. Revoked sessions are rejected immediately with `401 SESSION_REVOKED`, ensuring instant termination across all devices.
* **Current Device Tracking**: Every Android client maintains a persistent UUID (`deviceId`) and passes `X-Device-Id` and `X-Device-Info`, allowing accurate "This device" identification in session management.
* **MongoDB TTL Index**: Expired refresh tokens are automatically purged by MongoDB background workers.

---

## 3. Server-Side Attestation & Network Security

### Firebase App Check & Play Integrity Attestation
* **Mobile Binary Verification**: The server verifies that incoming requests originate from legitimate, untampered Android binaries via Firebase App Check tokens (`X-Firebase-AppCheck`), backed by Google's Play Integrity API.
* **App Check Middleware**: Evaluates incoming tokens cryptographically with Firebase Admin SDK (`verifyAppCheckToken`). Configurable enforcement (`APP_CHECK_ENFORCEMENT=enforce|warn|off`) rejects tampered, rooted, or uncertified devices with `401 APP_CHECK_MISSING` / `403 APP_CHECK_INVALID`.

### Declarative Certificate Pinning
* **MITM Proxy Prevention**: Android `network_security_config.xml` enforces SHA-256 SPKI certificate pinning for the production backend (`onrender.com`).
* **Backup Pin Redundancy**: Configured with leaf pin, intermediate CA pin (`Google Trust Services WE1`), backup root pin (`GTS Root R4`), and fallback root pin (`ISRG Root X1`), preventing service outages during certificate renewal while blocking user-installed and corporate MITM certificates.

---

## 4. Backend & Data Least-Privilege Architecture

### S3 Object Storage Hardening
* **Short-Lived Presigned URLs**: Private object download and redirect URLs expire after **15 minutes (900 seconds)**, minimizing the window of exposure if a URL is captured.
* **Scoped IAM Role**: S3 storage credentials operate under a dedicated least-privilege IAM policy granting only `s3:GetObject` and `s3:PutObject` restricted to the application bucket prefix (`arn:aws:s3:::nebula-app-storage/*`), with administrative and bucket-deletion capabilities completely omitted.

### MongoDB Atlas Least Privilege
* **Scoped Database User**: Backend MongoDB connection uses credentials with `readWrite` permissions strictly scoped to the `nebula` database (`readWrite@nebula`). Administrative roles (`atlasAdmin`, `readWriteAnyDatabase`, `dbAdminAnyDatabase`) are strictly disallowed.

---

## 5. Defense-in-Depth Mechanisms

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
