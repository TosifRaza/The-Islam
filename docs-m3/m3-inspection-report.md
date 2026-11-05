# M3 Pre-Implementation Inspection Report — Native Android Application Foundation

Milestone: M3 · Date: 2026-11-02 · Inspector role: Senior Android Architect / Integration Engineer
Method: direct inspection of the actual repository at `platform-dev/` (no assumptions from documents), plus a live build of the existing Android module.

---

## 1. What was inspected

| # | Area (per M3 prompt §3) | Finding summary |
|---|---|---|
| 1 | M1 Android Gradle project | Present at `platform-dev/android/`; AGP 8.5.2 / Gradle 8.9 wrapper / JDK 17 toolchain; minSdk 24, targetSdk 34, provisional `applicationId com.ikp.dev.app` (OD-01 label preserved) |
| 2 | Android package structure | Single package `com.ikp.dev.app` with `MainActivity` (placeholder) + `VersionInfo`; no feature packages yet |
| 3 | Existing launcher Activity | `MainActivity extends android.app.Activity` — dependency-free placeholder that renders a static foundation text; no AndroidX |
| 4 | Existing dependencies | `junit:junit:4.13.2` only; no AndroidX, no networking, no Room, no WorkManager |
| 5 | Existing Gradle configuration | Root + app build files, `gradle.properties` (AndroidX on, non-transitive R), committed wrapper pinning Gradle 8.9; release buildType has R8 + default optimize rules |
| 6 | Existing Android JVM tests | `VersionInfoTest` (3 tests, pure JUnit). `make android-test` SDK-free path with vendored JUnit/Hamcrest jars (`test-lib/`) |
| 7 | M2 backend API implementation | Complete admin CMS backend at `platform-dev/backend/` — auth, users admin, subjects (admin+public), books (admin+public), media/storage, workflow, dashboard, audit. 53/53 backend tests green in M2 evidence |
| 8 | Authentication endpoints | `POST /api/v1/auth/login` (email+password+optional deviceName → `{accessToken, refreshToken, expiresIn, user{userId, displayName, roles}}`), `POST /auth/refresh` (rotating), `POST /auth/logout`, `GET /auth/me`, `GET /auth/sessions`, `DELETE /auth/sessions/{id}`. Access token 15 min; refresh 30 d, single-use rotation |
| 9 | User profile endpoints | `GET /api/v1/auth/me` returns `{userId, email, displayName, language, status, roles, permissions, createdAt}`. **No self-service profile-update endpoint exists** (only admin `PATCH /api/v1/users/{userId}`) |
| 10 | Admin and public API access conventions | Public whitelist: health, auth login/refresh/password/**, `GET /api/v1/subjects(+/{slug})`, `GET /api/v1/library/books(+/{id})`, `GET /api/v1/files/*` (signed). Everything else authenticated; per-endpoint `@PreAuthorize` method security |
| 11 | Backend error responses | RFC 7807 `application/problem+json` with stable codes: `AUTH-001` (invalid credentials), `AUTH-002` (missing/expired token), `AUTH-004` (suspended), `AUTH-005` (bad refresh), `AUTHZ-001/002`, `VAL-001`, `NF-001`, `RATE-001` (429), `LIC-001`, `UPLOAD-002`, `WORKFLOW-001`, `SERVER-001`. Success envelope `{"data": …, "meta": …}` |
| 12 | Database and authentication behaviour | Flyway forward-only (identity + content modules); subjects seeded at 15 stable `cat_*` slugs, position 06 `cat_unconfirmed_06` hidden server-side (`hidden_unconfirmed`) — public API never returns it. Password hashing Argon2id; login rate limit 10/5 min per IP; refresh tokens rotate + revocable per session |
| 13 | Current CI pipeline | `.github/workflows/ci.yml`: backend (JDK 21, mvn test), admin (npm test+build), android (JDK 17 + explicit SDK 34 packages + `assembleDebug testDebugUnitTest`), stack-smoke (compose). **Defect found:** `on.push.branches: ain]` is corrupted YAML — push trigger effectively broken. Fix planned in M3 (does not affect M1/M2 functionality) |

**Live build verification (executed in this environment):** Android SDK 34 + build-tools 34.0.0 installed; Temurin JDK 17.0.21 (matches CI); existing skeleton rebuilt from clean state → `BUILD SUCCESSFUL`, 38 tasks, APK emitted, `testDebugUnitTest` 3/3 pass. (Sandbox note: Maven Central rate-limited this environment with HTTP 429; builds here use an init script that prepends public Central mirrors — repository files untouched, CI keeps canonical repos.)

---

## 2. Existing components that can be reused

- Gradle project skeleton, committed wrapper, version-pinned toolchain (AGP 8.5.2 ↔ Gradle 8.9 ↔ JDK 17) — **kept unchanged**.
- Provisional applicationId/branding gates (OD-01 comments) — preserved.
- SDK-free JVM test path (`make android-test`, vendored JUnit) — retained and extended with more pure-Java classes.
- CI android job structure — extended (build + JVM tests + instrumentation-compile).
- Backend auth/public APIs as the integration target — no new endpoints invented.
- Approved design tokens from `docs/06-ui-ux-guidelines.md` (Deep Emerald #164638, Islamic Gold #C7A75A, Warm Ivory #F7F6F0, White) — mapped into Android resources.

## 3. Missing components (must be built in M3)

Everything Android-side beyond the placeholder: AndroidX app skeleton, MVVM architecture, Navigation + bottom nav (6 tabs), design system (XML themes/styles), Retrofit/OkHttp layer with token refresh, Room database, WorkManager foundation, secure token storage (Keystore-backed), auth/profile/library UI, state handling (loading/empty/error/offline), JVM + instrumentation tests, documentation.

## 4. API integration assessment

### Available and integrated (real backend contract)
| Capability | Endpoint(s) | Android usage |
|---|---|---|
| Login | `POST /auth/login` | Login screen (email+password, optional device name) |
| Session restore | `GET /auth/me` (+ refresh on 401) | Splash/startup validation |
| Token lifecycle | `POST /auth/refresh` (rotating), `POST /auth/logout` | OkHttp Authenticator single-flight refresh; logout clears local session |
| Profile read | `GET /auth/me` | Profile screen (real data) |
| Session management | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | Profile → Devices & sessions |
| Library categories | `GET /subjects`, `GET /subjects/{slug}` | Library tab (14 visible; position 06 stays hidden server-side) |
| Book browse/search | `GET /library/books?subject&language&q&format&limit&cursor` | Library search + subject drill-in |
| Book detail + download | `GET /library/books/{id}`, `POST /library/books/{id}/download-token` (auth) | Book detail; short-lived signed URL |

### Not implemented in the backend (integration blockers — documented, honest states, no fake data)
| Capability | Blocker | M3 treatment |
|---|---|---|
| Self-registration | No `POST /auth/register` endpoint exists | Registration screen shows a documented "not yet available" state; login is fully functional. Blocker recorded |
| Email verification / forgot / reset password | Path `/api/v1/auth/password/**` is whitelisted but **no controller implements it** | Screens show honest unavailable state; no fake flows |
| Profile self-update (name/language/password) | No user-facing PATCH endpoint | Profile is read-only from `/auth/me`; settings that work today are device-local (theme, reader prefs, language preference UI prepared per OD-02) |
| Home content (Ayah/Hadith of Day, prayer times, Qibla, Hijri service) | No endpoints (M4 scope) | Home renders approved layout with documented empty/unavailable states; no fabricated religious content |
| Academy courses / Research / AI assistant | No endpoints (M5/M6 scope) | Foundation screens with explicit development-status messaging |

## 5. Android SDK and emulator availability

- **SDK (build): available in this environment** — cmdline-tools, platform-tools, `platforms;android-34`, `build-tools;34.0.0` installed under the project tool directory; clean `assembleDebug testDebugUnitTest` verified against the untouched M1 skeleton.
- **Emulator/instrumentation execution: not available** — no KVM device (`/dev/kvm` absent), no Docker in the sandbox. Instrumentation tests will be **written and compiled** (`assembleDebugAndroidTest`) and executed on CI devices by the owner; this is recorded honestly in the test evidence. The Maven-Central 429 mirror workaround is also documented (§1).

## 6. Unresolved product decisions touched by M3 (respected, not resolved)

- **OD-01** final applicationId/branding — provisional `com.ikp.dev.app` kept, clearly labelled.
- **OD-02** UI language list — all strings in resources; `values-ar` provided for RTL proof; no language silently declared final.
- **OD-03** category 06 name — remains hidden; client must never hard-code it (and the public API does not return it).
- Registration/OTP/social providers — none added; backend contract is authoritative.

## 7. Constraints & dependencies for implementation

1. Build on the existing module; keep the pinned toolchain (AGP 8.5.2/Gradle 8.9/JDK 17); no Kotlin.
2. Reuse M1 auth design; single-flight token refresh; Keystore-backed storage; never log tokens.
3. Envelope `{"data","meta"}` + problem+json parsing with the stable code catalogue.
4. Room v1 with documented migration policy; WorkManager foundation only (full sync = M7).
5. Six bottom-nav sections; per-section destinations; protected routes; state preservation.
6. No fabricated content anywhere; every screen has loading/empty/error/offline handling.

## 8. Plan (matches prompt §26 sequence)

M3.1 architecture/Gradle → M3.2 design system → M3.3 splash/onboarding/navigation → M3.4 auth integration → M3.5 six sections → M3.6 profile/settings → M3.7 networking/session security → M3.8 Room → M3.9 WorkManager → M3.10 states → M3.11 accessibility/RTL → M3.12 tests, docs, tracker, ZIP, evidence report.

**Inspection conclusion: GO.** The existing Android module is a buildable placeholder (verified by live build), the backend contract is stable and sufficient for login/session/profile/library integration, and all gaps are documented above with honest M3 treatments.
