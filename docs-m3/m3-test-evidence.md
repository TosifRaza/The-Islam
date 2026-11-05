# M3 Test Evidence — Native Android Foundation

Milestone: M3 · 2026-11-02 · Environment: Linux sandbox (2 vCPU, 4 GB RAM)

## 1. Executed in this environment (real outputs)

### 1.1 Gradle builds (Temurin JDK 17.0.21, AGP 8.5.2, Gradle 8.9, SDK 34)
| Task | Result | Artefact |
|---|---|---|
| `./gradlew clean assembleDebug` | **BUILD SUCCESSFUL** | `app-debug.apk` (8,389,722 bytes, versionCode 2 / `0.3.0-m3`) |
| `./gradlew assembleRelease` (R8 minify + the M3 ProGuard rules) | **BUILD SUCCESSFUL** | `app-release-unsigned.apk` (2,798,076 bytes) — unsigned by design; signing is a release-phase activity |
| `./gradlew assembleDebugAndroidTest` | **BUILD SUCCESSFUL** | `app-debug-androidTest.apk` (3,853,525 bytes) — instrumentation suite **compiles** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** | JUnit XML under `app/build/test-results/testDebugUnitTest/` |

### 1.2 JVM unit tests — 28/28 pass
| Suite | Tests | Result | Covers (prompt §21 JVM list) |
|---|---|---|---|
| `CoreLogicTest` | 19 | **pass** | StartupPolicy decisions (incl. offline-saved-session never renders authenticated), problem+json parsing, stable-code→message mapping, subject slug/position mapping + **position-6-absent invariant**, fail-safe `downloadAllowed` default, /auth/me mapping, envelope parsing (camelCase + snake_case alternates), books cursor meta, query-key stability, sync enqueue/backoff/max-attempts |
| `TokenRefreshTest` | 3 | **pass** | token refresh decisions: successful rotation persistence, failure → secure-state clear, **8 concurrent 401s → exactly 1 refresh** (single-flight proof, prompt §8) |
| `AuthValidationTest` | 3 | **pass** | client-side email/password rules mirroring backend VAL rules |
| `VersionInfoTest` (M1) | 3 | **pass** | regression: M1 utility still green |

Raw results: `evidence/jvm-test-summary.txt` (generated from JUnit XML).

### 1.3 API-contract integration — 29/29 checks pass (REAL backend)
Runner: `scripts/m3-api-contract/run-m3-integration.sh` — boots PostgreSQL 14
(zonky binaries), Spring Boot 3.5.x with Flyway (identity+content migrations)
and the labelled dev-data seed, then compiles the **app's own**
`AuthApi`/`LibraryApi`/DTOs/`Mappers` and executes them against
`http://localhost:8080`.

Log: `evidence/api-contract-harness.log`. Coverage: bad login (401
`AUTH-001`), login (200, `expiresIn=900`, roles), `/auth/me` (real profile),
sessions list, refresh rotation + old-token replay rejection (`AUTH-005`),
subjects (14 public, position 6 absent, ascending, `cat_*`), books browse +
empty-search (cursor meta), unknown-book 404 (`NF-001`), anonymous rejection
(`AUTH-002`), logout revocation (204) + refresh-after-logout rejection.

Backend regression baseline in the same environment: `mvn test` →
**53/53 pass** (M1/M2 suites untouched).

## 2. Compiled but NOT executed here (recorded honestly)

| Item | Status | Where it runs |
|---|---|---|
| Instrumentation tests (`AppLaunchTest`: launch→sign-in flow, sign-in validation errors, bottom-bar visibility; `RoomDaoTest`: subject ordering/primaryKey stability, preferences upsert, **pending-sync idempotency**, sync log pruning) | **compiles** (`assembleDebugAndroidTest` succeeded); **not executed** — this sandbox has no KVM/emulator (`/dev/kvm` absent, no Docker) | CI runner or any device: `./gradlew connectedDebugAndroidTest` (CI job ready; emulator provisioning note in the device-testing report) |
| On-device accessibility passes (TalkBack walkthrough, 200% font scale) | **not executed** (no device) | device checklist in `m3-device-testing-report.md` |
| RTL rendering screenshots (ar/ur), Devanagari rendering (hi) | **not executed** (no device); resources + `supportsRtl` + start/end layouts + per-app locale switch are in place and unit-verified where possible | device checklist |
| Light/dark theme switching on device | implemented (`AppCompatDelegate` + `values-night`); verified by build + code review, not screenshots | device checklist |

No test was skipped silently and no result was fabricated: every executed
command and its outcome is listed above; everything else is labelled
"not executed in this environment".

## 3. What the 28 JVM tests do NOT cover (and why)

- Room SQL behaviour → covered by the instrumentation suite (real SQLite),
  compiled here, executed on CI devices.
- Retrofit/OkHttp wire behaviour beyond the harness's synchronous path → the
  integration harness covers the real wire format against the real backend;
  the `TokenAuthenticator`'s OkHttp-facing `authenticate()` glue is covered by
  the JVM single-flight test using fakes, plus the rotation checks in the
  harness.
- Compose/animator visuals — n/a (XML UI).
