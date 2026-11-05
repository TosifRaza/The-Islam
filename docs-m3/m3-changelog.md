# M3 Changelog — Native Android Application Foundation

Version: android `0.3.0-m3` (versionCode 2) · repo milestone M3 · 2026-11-02

## Added (Android)
- MVVM application foundation: single-activity Navigation host, six-section
  custom bottom bar (BottomNavigationView's 5-item cap documented),
  per-tab state preservation, deep-link foundation (`ikp://library/book/{id}`).
- Design system: approved palette tokens, Material 3 DayNight + dark theme,
  typography/spacing/component styles, custom `StateView`
  (loading/empty/error/offline), launcher icons (adaptive + all densities).
- Splash + startup decisioning: SplashScreen API, unit-tested `StartupPolicy`
  (onboarding/login/home incl. offline-saved-session), 4-page onboarding with
  permission-introduction page, guest-browse entry.
- Authentication: login, single-flight rotating refresh (OkHttp
  Authenticator), logout w/ revocation, session-expiry handling, Keystore-backed
  `EncryptedTokenStore`, `SessionManager` state machine.
- Home: real subjects/books rails; honest unavailable states for M4 content
  (Ayah/Hadith/Hijri/prayer/Qibla/Tasbih) — no fabricated religious content.
- Library: real categories (position 06 server-hidden), debounced search,
  language chips, cursor-paged books, book detail, recent-viewed history.
- Profile: real `/auth/me` data, devices & sessions (list/revoke), working
  appearance (light/dark/system) and language (EN/AR) switches, honest
  states for backend-absent capabilities (profile edit, data export, account
  deletion, registration, password reset).
- Room v1 (11 tables) with forward-only migration policy + TTL caches.
- WorkManager foundation: network-constrained worker, exponential backoff,
  idempotent op keys, sync log; M7 registers handlers.
- Multilingual/RTL foundation: resources-only strings, ar/hi/ur variants,
  per-app locale switching, start/end layouts, 48 dp targets, content
  descriptions.
- Tests: 28 JVM tests; instrumentation suite (launch, sign-in validation,
  Room DAO incl. sync idempotency); API-contract harness (29 checks).
- Docs: docs-m3 report set + rewritten `android/README.md`.

## Fixed (repository)
- **CI**: `on.push.branches: ain]` (corrupted YAML introduced in the M2
  delivery) → `branches: [main]`; android job extended to build debug +
  R8 release + compile instrumentation + run JVM tests + upload the APK.

## Unchanged / preserved
- M1 skeleton files (VersionInfo, committed wrapper, toolchain pins) kept;
- M1/M2 backend and admin code untouched (53/53 backend tests green);
- all 15 library category positions (position 06 hidden via the server);
- OD-01 provisional applicationId labelling; OD-02 language-list gate.

## Known limitations
See `m3-implementation-report.md` §7 (on-device run pending, download
contract gap, `current=false` sessions flag, self-service endpoints absent,
M4–M7 scopes untouched).
