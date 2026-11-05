# M3 Backend Compatibility Report

Milestone: M3 · 2026-11-02

## 1. Verdict

**COMPATIBLE.** The M3 Android app integrates with the M1/M2 backend exactly
as deployed in the repository — no backend code changes were required by M3,
and none were made (the only repo-level fix is the CI YAML corruption noted
below, which is build infrastructure, not API surface).

## 2. Verified compatibility evidence

| Check | Result |
|---|---|
| Backend regression in this environment | `mvn test` → **53/53 pass** (real PostgreSQL via zonky; identity + content migrations) |
| Live stack (PostgreSQL 14 + Spring Boot 3.5.x + Flyway) | healthy on :8080; integration harness **29/29** |
| Auth contract (login/refresh rotation/logout/me/sessions) | verified end-to-end (see harness log) |
| Public library contract (subjects/books/search/404 semantics) | verified end-to-end (14 categories, position 6 absent — OD-03 upheld server-side; cursor meta) |
| Error contract (problem+json + stable codes) | `AUTH-001`, `AUTH-002`, `AUTH-005`, `NF-001` observed live; full catalogue mapped in `ErrorMapper` |
| Envelope (`{"data","meta"}`) | parsed by `ApiResponse` incl. cursor-paged meta |

## 3. Repository-level findings (not API breaking)

1. **CI YAML defect (fixed in M3):** `.github/workflows/ci.yml` had
   `on.push.branches: ain]` — corrupted YAML from the M2 delivery, breaking
   the push trigger. Corrected to `branches: [main]`; android job extended
   (debug + R8 release + instrumentation compile + JVM tests + APK artifact).
2. **`/auth/sessions` always returns `current=false`** (M2 implementation
   detail). Not fixed by M3 (backend untouched per scope); recorded as a
   recommended improvement.
3. **Public book payloads lack file UUIDs** for `/download-token` — contract
   gap documented with a proposed M4 fix (see API integration report §3).
4. **No user self-service endpoints** (registration, verification, password
   reset, profile update, data export, account deletion) — Android ships
   honest unavailable states; no fake flows.

## 4. Environment/toolchain compatibility

- App toolchain pinned and unchanged: AGP 8.5.2 ↔ Gradle 8.9 ↔ JDK 17 ↔
  compileSdk 34 ↔ minSdk 24. Backend stays JDK 21 / Spring Boot 3.5.x —
  independent processes, no shared JVM requirements.
- JWT: HS256 with the backend's configured secret (dev default documented in
  `application.yml`; production secret supplied via `IKP_JWT_SECRET`) — the
  app treats tokens as opaque bearer strings.
- Clock: access-token lifetime (15 min) handled via refresh, not client
  clock math (no server-time skew sensitivity in M3 paths).
