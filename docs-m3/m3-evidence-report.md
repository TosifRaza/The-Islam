# M3 Evidence Report (consolidated)

Milestone: M3 — Native Android Application Foundation · 2026-11-02
Every claim below traces to an executed command or generated artefact in this
milestone. Nothing is claimed that was not run.

## 1. Builds (executed, real artefacts)

| Command | Outcome | Artefact size |
|---|---|---|
| `./gradlew clean assembleDebug` | BUILD SUCCESSFUL | `app-debug.apk` 8,389,722 B |
| `./gradlew assembleRelease` (R8) | BUILD SUCCESSFUL | `app-release-unsigned.apk` 2,798,076 B |
| `./gradlew assembleDebugAndroidTest` | BUILD SUCCESSFUL | `app-debug-androidTest.apk` 3,853,525 B |

Toolchain: Temurin JDK 17.0.21 / AGP 8.5.2 / Gradle 8.9 wrapper / SDK
platform 34 + build-tools 34.0.0 + platform-tools. Sandbox note: Maven
Central rate-limited this environment (HTTP 429); builds here used an
init-script that prepends public Central mirrors — **repository files
untouched, CI keeps canonical repos** (recorded in the inspection report §1).

## 2. Tests (executed)

- **JVM unit tests: 28/28 pass, 0 failures, 0 errors**
  (`docs-m3/evidence/jvm-test-summary.txt`, generated from JUnit XML).
- **API-contract integration: 29/29 checks pass** against a live stack
  (PostgreSQL 14 + Spring Boot + Flyway + dev seed)
  (`docs-m3/evidence/api-contract-harness.log`; runner
  `scripts/m3-api-contract/run-m3-integration.sh`).
- **Backend regression: 53/53 pass** (`mvn test`, embedded PostgreSQL) —
  M1/M2 functionality intact after M3.

## 3. Compiled but not executed here (honest status)

- Instrumentation suite (`AppLaunchTest`, `RoomDaoTest`) — **compiled**
  (assembleDebugAndroidTest), **not executed**: no emulator/KVM/Docker in this
  environment. Execution: `./gradlew connectedDebugAndroidTest` on CI or a
  developer device (checklist in `m3-device-testing-report.md`).

## 4. Code inventory

- 79 Java source files (main) + 4 JVM test classes + 2 androidTest classes.
- 70 XML resources (layouts, design system, nav graph, drawables, night & ar/hi/ur variants).
- 11-table Room database v1; WorkManager foundation; Keystore-backed token store.

## 5. Documents produced by M3

| Document | Path |
|---|---|
| Pre-implementation inspection | `docs-m3/m3-inspection-report.md` |
| Implementation report | `docs-m3/m3-implementation-report.md` |
| Navigation verification | `docs-m3/m3-navigation-verification.md` |
| Auth integration | `docs-m3/m3-auth-integration-report.md` |
| API integration | `docs-m3/m3-api-integration-report.md` |
| Room/DB summary | `docs-m3/m3-database-room-summary.md` |
| WorkManager foundation | `docs-m3/m3-workmanager-foundation.md` |
| Backend compatibility | `docs-m3/m3-backend-compatibility.md` |
| Test evidence | `docs-m3/m3-test-evidence.md` |
| Device testing | `docs-m3/m3-device-testing-report.md` |
| Final structure | `docs-m3/m3-final-structure.txt` |
| Updated tracker | `docs-m3/14-feature-tracker-v1.2.xlsx` |
| Changelog | `docs-m3/m3-changelog.md` |
| Developer guide | `android/README.md` (rewritten) |

## 6. Downloadable deliverables

- `m3-android-source-v0.1.zip` — complete M3 Android source (buildable).
- `14-feature-tracker-v1.2.xlsx` — updated tracker (also copied to `docs-m3/`).

## 7. Milestone boundary

M3 delivers the **mobile foundation**. It does not claim completion of M4–M7
features (content modules, Academy, Research/AI, offline sync). Per the
approved sequence, **M4 does not start until the owner reviews and approves
M3** — the owner remains the final decision-maker.
