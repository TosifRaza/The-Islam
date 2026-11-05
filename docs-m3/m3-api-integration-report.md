# M3 API Integration Report — Android ↔ Backend

Milestone: M3 · 2026-11-02
Source of truth: the **actual** M1/M2 backend (`backend/src/main/java/app/ikp/backend`) and the
executed integration harness (`scripts/m3-api-contract/`, evidence:
`docs-m3/evidence/api-contract-harness.log` — 29/29 checks green against a live
PostgreSQL 14 + Spring Boot + Flyway stack).

## 1. Contract conventions honoured

- Base path `/api/v1`; success envelope `{"data": …, "meta": …}`
  (`core/network/ApiResponse`, Gson `serializeNulls`).
- Errors are RFC 7807 `application/problem+json`; the stable `code` property
  drives user messaging via `core/error/ErrorMapper` (never raw server text).
  Verified codes end-to-end: `AUTH-001`, `AUTH-002`, `AUTH-005`, `NF-001`,
  `RATE-001` (handled), plus the catalogue in `ErrorMapper`.
- No client trusts roles/permissions — the Android app holds `roles` only for
  display; every protected action defers to the backend's enforcement.

## 2. Integrated endpoints (verified live)

| Endpoint (method, path) | Auth | Android usage | Harness evidence |
|---|---|---|---|
| POST `/auth/login` | public | Login screen; `deviceName` forwarded to the session registry | 200 + token pair + `expiresIn=900`; wrong password → 401 `AUTH-001` |
| POST `/auth/refresh` | public (rotating) | `TokenAuthenticator` single-flight + explicit restore path | rotation produces a new pair; **replaying the old refresh token → 401/400 `AUTH-005`** |
| POST `/auth/logout` | bearer | Logout (confirmation dialog → revoke + local clear) | 204; refresh after logout → rejected |
| GET `/auth/me` | bearer | Splash validation, Profile screen | 200 with real profile; anonymous → 401 `AUTH-002` |
| GET `/auth/sessions` | bearer | Devices & sessions screen | 200; rows carry deviceName/expiresAt |
| DELETE `/auth/sessions/{id}` | bearer | Revoke (own-session guard client-side; ownership enforced server-side `AUTHZ-002`) | (exercise covered by backend RBAC tests; UI wired) |
| GET `/subjects`, `/subjects/{slug}` | public | Library categories + detail | 200; **14 public categories; positions strictly ascending; position 6 never returned (OD-03)** |
| GET `/library/books` | public | Search/browse (subject, language, q, cursor) | 200 + cursor meta; no-match search → 200 empty |
| GET `/library/books/{id}` | public | Book detail | unknown id → 404 `NF-001` |
| POST `/library/books/{id}/download-token` | bearer | **NOT called by M3** — see §3 | — |

## 3. Contract gaps found during integration (honest findings)

1. **Download flow unusable by any client.** The public book payloads expose
   only format *names*; the `/download-token` endpoint requires a `fileId`
   (media UUID) that is never published. The Android download button shows an
   explicit explanation instead of a broken flow. **Recommended backend fix**
   (M4): include `files:[{fileId, format, sizeBytes}]` in the public detail
   payload (published + scan-clean files only).
2. **`/auth/sessions` always returns `current=false`.** The M2 implementation
   hardcodes the flag, so "This device" highlighting cannot work; revoking the
   current session is possible through the API. The harness asserts the real
   behaviour; the UI treats rows uniformly. **Recommended fix**: compare the
   presented access token's `jti` with each session row's token hash.
3. **No user self-service endpoints.** Registration, email verification,
   forgot/reset password (whitelisted path `/api/v1/auth/password/**` exists
   but no controller), profile self-update, data export and account deletion
   have no endpoints. The app ships honest, clearly-worded unavailable states
   rather than fake forms (recorded in the implementation report §7).

## 4. Security-relevant integration behaviour

- Access token 15 min / refresh 30 d single-use rotation — verified by the
  rotation + replay checks above.
- Concurrent 401s trigger **exactly one** refresh (JVM-tested: 8 parallel
  callers → 1 refresh call, all retries use the rotated pair).
- Refresh failure → `SessionManager.onSessionExpired()` clears Keystore-backed
  credentials and the UI returns the user to login with an explanation.
- Tokens are never logged (logging interceptor redacts/suppresses token
  material; no token values appear in any log or report).

## 5. Environment matrix (documented, no hardcoding)

| Target | Base URL | Transport |
|---|---|---|
| Emulator (debug) | `http://10.0.2.2:8080/` | cleartext allowed by the debug-only manifest overlay |
| Physical device (debug) | `IKP_BASE_URL` gradle property or `adb reverse tcp:8080 tcp:8080` + `http://127.0.0.1:8080/` | cleartext allowed (debug only) |
| Release build | `-PIKP_BASE_URL=https://…` injected at build time | cleartext disabled — HTTPS only |
| Production | decided by the owner at deployment; **not present in source** | HTTPS |
