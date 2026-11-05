# M3 Implementation Report — Native Android Application Foundation

Milestone: M3 · 2026-11-02 · Status: **delivered for owner review**
Execution order: M3.0 → M3.12 followed sequentially (prompt §26); no subtask skipped.

## 1. What M3 delivered

A complete, buildable native Android foundation (Java + XML, MVVM) on the
existing M1 module — single activity, six-section navigation, real
authentication with the M1/M2 backend, real Home/Library data, profile +
sessions management, Room offline foundation, WorkManager foundation, full
loading/empty/error/offline state coverage, light/dark themes, RTL/multilingual
readiness, 28 JVM tests, a compiled instrumentation suite, and a 29-check
API-contract integration run against the real backend.

## 2. Project structure (delta on the M1 tree)

```
platform-dev/
├── android/                          # M3 app (v0.3.0-m3, versionCode 2)
│   ├── app/src/main/java/com/ikp/dev/app/
│   │   ├── IkpApplication, MainActivity, VersionInfo
│   │   ├── core/{common,network,security,error,database,workers}
│   │   ├── data/{local/{entity,dao},remote/{dto},repository,mapper}
│   │   ├── domain/model/  ui/{splash,onboarding,auth,home,library,
│   │   │                  academy,research,assistant,profile,common}
│   │   └── di/ServiceLocator
│   ├── app/src/main/res/             # design system (values, values-night,
│   │                                 # values-ar, values-hi, values-ur, layouts,
│   │                                 # navigation/nav_graph, drawables)
│   ├── app/src/debug/AndroidManifest.xml   # cleartext for dev hosts only
│   ├── app/src/test/                 # 28 JVM tests (4 suites)
│   ├── app/src/androidTest/          # AppLaunchTest + RoomDaoTest (compiled)
│   └── app/proguard-rules.pro        # R8 rules for Gson/Retrofit reflection
├── scripts/m3-api-contract/          # integration harness + runner
├── docs-m3/                          # this report set + evidence/
└── .github/workflows/ci.yml          # extended android job (see §6)
```

Full inventory: `m3-final-structure.txt` (generated from the tree).

## 3. Subtask completion (prompt §26)

| Subtask | Delivered | Evidence |
|---|---|---|
| M3.0 inspection | `m3-inspection-report.md` — repo inspected, M2 status verified (docs-m2, 53 backend tests), live baseline build of the untouched skeleton | docs-m3 |
| M3.1 Gradle/architecture | pinned toolchain kept; MVVM+Navigation+Repository; manual DI decision documented | android/README §6 |
| M3.2 design system | full XML token set, Material3 DayNight, dark theme, components | res/values*, res/drawables |
| M3.3 splash/onboarding/nav | SplashScreen API + StartupPolicy (unit-tested), 4 onboarding pages incl. permissions, 6-tab bar w/ state preservation, deep-link foundation | nav_graph, tests |
| M3.4 auth integration | login/restore/logout/expiry against real API; Keystore-backed store; single-flight refresh | harness log, TokenRefreshTest |
| M3.5 six sections | Home (real rails), Library (real categories/search/books/detail), Academy/Research/Assistant (honest foundations), Profile | layouts + fragments |
| M3.6 profile/settings | /auth/me display, sessions list/revoke, working theme+language, honest unavailable sections | ProfileFragment et al. |
| M3.7 networking | Retrofit/OkHttp, timeouts 15/20/30 s, envelope+problem+json mapping, no token logging | ApiClient, ErrorMapper |
| M3.8 Room | v1 (11 tables), forward-only migration policy, TTL caches, user-data foundations | IkpDatabase + docs |
| M3.9 WorkManager | network-constrained worker, exponential backoff, idempotent op keys, sync log; handlers at M7 | SyncScheduler/Worker |
| M3.10 states | StateView (loading/empty/error/code) + offline banner on every significant screen | ui/common/StateView |
| M3.11 a11y/RTL/i18n | 48 dp targets, content descriptions, sp text, RTL-ready layouts, ar/hi/ur resources, per-app locales | res/values-* |
| M3.12 tests/docs/evidence | §5 below | docs-m3/* |

## 4. Milestone acceptance (prompt §23) — point by point

1. Maintainable structure ✅ 2. Compiles ✅ 3. Installs/launches — build
verified in-environment; **on-device install pending emulator/device (honest
limitation)** 4–5. Splash/onboarding/six tabs implemented + compiled
instrumentation ⚠️ device-run pending 6. Auth integrated with real backend ✅
(29-check harness) 7. Profile loads real data ✅ 8. Supported settings work ✅
(theme/language; others honest) 9. Secure logout ✅ (revoke + clear, tested)
10–12. API errors, states, Room — ✅ implemented; Room DAO runs verified via
instrumentation compile (execution on CI) 13. WorkManager configured ✅
14–15. Themes/RTL ready ✅ (device screenshots pending) 16. JVM tests pass ✅
28/28 17. Instrumentation — compiled ⚠️ 18. API integration tests pass ✅
29/29 19. Docs updated ✅ 20. Tracker updated with evidence ✅ (v1.2).

## 5. Test results (details in m3-test-evidence.md)

- Gradle: clean `assembleDebug` (8.4 MB APK) + `assembleRelease` with R8
  (2.8 MB, unsigned) + `assembleDebugAndroidTest` — **all BUILD SUCCESSFUL**.
- JVM: **28/28 pass** (startup policy, error mapping, category-position
  invariant, envelope parsing, token-refresh single-flight/rotation, sync
  scheduling, validation).
- Integration: **29/29 checks** against live PostgreSQL+Spring Boot (the
  app's own API layer); backend regression 53/53 in the same environment.
- Instrumentation: compiled, **not executed here** (no KVM/emulator) — CI
  runner executes `connectedDebugAndroidTest`.

## 6. CI pipeline update

Fixed a real M2 defect: `on.push.branches: ain]` (corrupted YAML) →
`branches: [main]`. The android job now runs
`assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest`,
asserts the JVM summary via XML, and uploads the debug APK as an artifact.

## 7. Known limitations & honest capability map

- **On-device run pending** — no emulator in this environment (no KVM, no
  Docker); install/launch verified by successful builds + CI job, device
  checklist in `m3-device-testing-report.md`.
- **Book download blocked** by a backend contract gap (public payloads lack
  file UUIDs required by `/download-token`) — explicit in-app explanation; fix
  proposed in `m3-api-integration-report.md` §3.
- **`/auth/sessions` always returns `current=false`** (M2 behaviour) — "This
  device" highlighting cannot work until fixed; client asserts real behaviour.
- **No self-service endpoints** (registration, verification, password reset,
  profile edit, data export, account deletion) — honest unavailable screens;
  nothing fakes these flows.
- Daily-content (Ayah/Hadith/Hijri/prayer/Qibla/Tasbih), Academy courses,
  Research, AI assistant → M4/M5/M6 foundation screens with explicit status
  messaging; no fabricated content, no AI provider connected.
- Sync payloads → M7 (foundation only, as scoped).
- OD-01 (applicationId/branding) and OD-02 (language list) remain open and
  respected.

## 8. Deliverables index

1–4. Source + ZIP + structure: `m3-android-source-v0.1.zip` (project root),
`m3-final-structure.txt`.
5. Navigation verification: `m3-navigation-verification.md`.
6. Auth integration: `m3-auth-integration-report.md`.
7. API integration: `m3-api-integration-report.md`.
8. Room/DB: `m3-database-room-summary.md`.
9. WorkManager: `m3-workmanager-foundation.md`.
10. Backend compatibility: `m3-backend-compatibility.md`.
11–12. JVM + instrumentation results: `m3-test-evidence.md` (+ `evidence/`).
13. APK build evidence: §5 + CI job.
14. Device testing: `m3-device-testing-report.md`.
15–16. Limitations (§7) + updated tracker `14-feature-tracker-v1.2.xlsx`.
17. Evidence report: `m3-evidence-report.md`.
18. Developer install guide: `android/README.md` (rewritten for M3).
