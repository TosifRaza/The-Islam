# M3 Authentication Integration Report

Milestone: M3 · 2026-11-02
Scope: the Android authentication module against the ACTUAL M1/M2 backend auth
design (reused — no duplicate system, no new endpoints, no provider choices).

## 1. Integration summary

| Capability | Backend endpoint | Android implementation | Verification |
|---|---|---|---|
| Sign in | `POST /api/v1/auth/login` (email, password, optional deviceName) | `AuthViewModel.login` → `AuthRepository.login`; client-side validation mirrors VAL rules; loading/error/success states | Harness: 200 + token pair + roles; wrong password → 401 `AUTH-001` |
| Session restore | `GET /auth/me` (+ explicit single refresh on 401) | `SplashViewModel.start` → `AuthRepository.restore` → pure `StartupPolicy` decision | Harness + 19-test `CoreLogicTest` (incl. offline path) |
| Token refresh | `POST /auth/refresh` (single-use rotation) | OkHttp `TokenAuthenticator`: synchronized single-flight, stale-token reuse detection, rotated pair persisted before retry | `TokenRefreshTest` (3 tests incl. 8-way concurrency) + harness rotation/replay checks |
| Logout | `POST /auth/logout` (revokes presented refresh) + local clear | Confirmation dialog → revoke best-effort → `TokenStore.clear()` mandatory | Harness: 204 + refresh-after-logout rejected |
| Session expiry | refresh failure → `AUTH-005`/401 | `SessionManager.onSessionExpired()` clears credentials → global navigation to sign-in with explanation dialog | TokenRefreshTest failure path |
| Sessions list/revoke | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | Devices & sessions screen (list + confirm-revoke) | Harness: list contract verified; ownership enforced server-side (`AUTHZ-002`) |

## 2. Security posture (prompt §8 requirements → implementation)

- **Keystore-backed storage**: `EncryptedTokenStore` —
  `EncryptedSharedPreferences` + AES-256-GCM `MasterKey`; corrupted Keystore
  state fails closed to a signed-out store (delete + recreate), never to
  plaintext.
- **No password storage**: passwords exist only transiently in the login
  request; never persisted, never logged.
- **No token logging**: the HTTP logging layer redacts any message containing
  token material; no token value appears anywhere in reports/evidence.
- **Concurrent refresh races**: single-flight lock + stale-token detection
  (proven by the concurrency test: 8 threads → exactly 1 refresh call).
- **Refresh revocation handling**: rotation replay and post-logout replay both
  rejected by the backend → the client clears state and routes to sign-in.
- **Timeout/retry rules**: connect 15 s / read 20 s / write 30 s; no blind
  retries (only the authenticator's single authorized retry); offline mapped
  to `NET-OFFLINE` with retry affordances.
- **No production credentials** anywhere in the app or repository; base URLs
  are environment-injected (debug emulator default 10.0.2.2, release injected
  via gradle property).

## 3. Provisional baseline honoured

Email + password only (the backend's implemented surface). **No** Google /
phone-OTP / social providers were added, silently or otherwise. Registration,
email verification and forgot/reset password have no backend endpoints — the
UI exposes explicit, truthful "not yet available" screens instead of fake
flows (blockers recorded in the API integration report §3 and the tracker).

## 4. Failure-state matrix (all user-facing states implemented)

| Failure | User sees | Session state |
|---|---|---|
| Invalid credentials (401 `AUTH-001`) | "Incorrect email or password" | untouched |
| Suspended (403 `AUTH-004`) | "This account is suspended…" | untouched |
| Rate limited (429 `RATE-001`) | "Too many attempts…" | untouched |
| Expired access token | transparent single-flight refresh | rotated pair persisted |
| Dead refresh token | "Your session has expired…" dialog → sign-in | cleared (secure state) |
| Offline at startup | guest-browse home + offline banner | kept for later re-validation (never rendered authenticated) |
| Offline during use | inline error + retry | untouched |
