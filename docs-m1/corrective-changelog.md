# M1 Corrective Change Log — v0.1 → v0.1.1

**Date:** 2026-11-02 · **Trigger:** owner review "M1 Review — Required Corrections Before Formal Approval"
**Scope rule honoured:** no approved feature was removed or reduced; the feature tracker was **not** modified (re-verified: 9 sheets, 104 records, 93 FR mappings). No paid service, no paid AI provider, no production deployment, no Google Play artefact, and **no Islamic content of any kind** was introduced (OD-05 / G-3 untouched).

## 1. Fix Android test execution (owner item 1) — FIXED

- `Makefile` `android-test` rewritten. The old target `cd`-ed into `android/app/src/test` and then resolved `$(shell find android/...)` paths against the repo root — it could never work; it also never put JUnit on the classpath and only compiled, never executed.
- New behaviour: repo-root-relative paths only; compiles the pure-Java production class + all test sources with **vendored JUnit 4.13.2 / Hamcrest 1.3** (`android/test-lib/`, sha256-documented in `android/README.md`) on the classpath; **executes** all tests via `org.junit.runner.JUnitCore`; propagates JUnit's exit status.
- Evidence: `docs-m1/evidence/android-jvm-test.log` — `OK (3 tests)`, exit 0.
- Negative control (exit code is meaningful): a deliberately failing JUnit test run returns exit code 1 (`Tests run: 1, Failures: 1`) — so `make android-test` returns 0 **only** when all tests pass.

## 2. Fix Android CI (owner item 2) — FIXED, with a build-breaking defect found and removed

- **Gradle wrapper is now committed**: `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar` (sha256 `498495120a03b9a6…`), `gradle/wrapper/gradle-wrapper.properties` pinning `gradle-8.9-bin.zip`. CI no longer needs a preinstalled Gradle (`gradle wrapper …` step removed).
- `.github/workflows/ci.yml` android job now **explicitly installs the required SDK packages** (`platform-tools`, `platforms;android-34`, `build-tools;34.0.0`) instead of relying on the generic setup action, then runs `./gradlew --no-daemon assembleDebug testDebugUnitTest` and prints the JUnit summary.
- **Latent build defect fixed**: `applicationId "com.ikp.dev.app"` sat at the top level of the `android {}` block in `android/app/build.gradle` — the AGP DSL rejects that, so **no Gradle build of this project could ever have succeeded** (configuration failed with `Could not find method applicationId()`). Moved into `defaultConfig {}`; OD-01 provisional-id comment preserved.
- Execution evidence (Android SDK 34 + build-tools 34.0.0 installed in the sandbox):
  - `docs-m1/evidence/android-gradle-build.log` — `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL, exit 0**
  - `docs-m1/evidence/android-gradle-build-clean.log` — from-scratch `clean assembleDebug testDebugUnitTest` → 39 tasks, APK emitted (`app/build/outputs/apk/debug/app-debug.apk`), JUnit XML `tests="3" skipped="0" failures="0" errors="0"`, exit 0
  - Attempt logs kept for transparency: `android-gradle-build-attempt1-central429.log` (Maven Central rate-limited the sandbox), `…-attempt2-applicationid-fix-pending.log`, `…-attempt3-central429-initgap.log`
- Sandbox-only dependency mirror (NOT in the repo): Maven Central rate-limits this sandbox IP (HTTP 429); the build used an Aliyun-mirror Gradle init script outside the repository (`tools/sandbox-mirror.init.gradle`), the same workaround class as the previously documented `~/.m2/settings.xml` mirror. CI/GitHub runners resolve the original repositories directly.
- **GitHub Actions execution caveat (honest statement):** this sandbox has no GitHub remote or hosted-runner access, so the workflow could not be executed *on GitHub Actions* itself. The recorded evidence above is the **execution of the exact commands and SDK steps the workflow performs**, run locally on the same toolchain. The workflow file is valid YAML, uses only official actions, and runs the verified commands verbatim on push.

## 3. Docker Compose verification (owner item 3) — VERIFIED, plus a Docker-enabled integration test added

- **New Docker-enabled integration test**: `scripts/compose-smoke.sh` (and `make compose-smoke`). It builds and boots the complete compose stack, then asserts the owner's six checks — PostgreSQL healthy, Flyway migrations executed (4 successful rows), 11 roles seeded, Spring Boot `/api/v1/health` UP, admin dashboard served through Nginx, `/api/` proxy reaching the backend — and fails non-zero otherwise. It is wired into CI as the `stack-smoke` job (GitHub runners have Docker), with logs uploaded on failure.
- **Whole-stack execution in this sandbox (no Docker daemon available — honest note):** every component was executed as a real process instead, with the identical six checks, via `scripts/stack-smoke-native.sh`:
  - PostgreSQL **14.19** (the same embedded binaries the backend tests use) — started, healthy
  - Spring Boot 3.5.6 jar — started against it, **Flyway applied V1/V2 + both repeatable seeds** (4 successful rows), **11 roles** seeded
  - `GET /api/v1/health` direct → `{"data":{"status":"UP","service":"ikp-backend","db":"UP",…}}`
  - **Nginx 1.28.0** (compiled locally, same `try_files` + `/api/` proxy semantics as `admin/nginx.conf`) serving the built React dashboard
  - `GET /api/v1/health` **through the Nginx proxy** → same UP envelope
  - Result: `PASS: 6  FAIL: 0` — `docs-m1/evidence/native-stack-smoke.log`, exit 0
- The first scripted run FAILED (5/6) because the stripped Zonky PostgreSQL binaries ship no `psql`/`createdb`/`pg_isready`; the script now falls back to a bundled minimal JDBC runner (`scripts/JdbcRun.java`) when no psql client exists. The failing run is preserved in the evidence history of this session.

## 4. Content source register reconciliation (owner item 4) — DONE, no content imported

- All **19 CSV records** and the Markdown summary now agree 1:1; a machine check (`scripts/verify_source_register.py`, output in `docs-m1/evidence/source-register-check.log`) asserts ids, status strings, bucket counts, gate column and the "no import" statement — **ALL PASS**.
- Flagged records resolved:
  - **SR-05**: ambiguous "Verified-PD basis; confirm per file" normalised to **Conditional — PD basis; per-file confirmation required** in both files (the PD *basis* is verified, but the required per-file action still gates any use).
  - **SR-11**: was already Conditional in the CSV but was **miscounted in the Markdown summary** — the summary now lists it under Conditional (5 → now 6 conditional rows; counts corrected).
  - **SR-19**: CSV said "Verified — reference data", the summary wrongly listed it under Unverified ("SR-19-partial"). Normalised to **Verified — reference data (no content license required)** in both files; it contains no religious content and no content license is required.
- Final reconciled buckets: **Verified 5 (SR-01, SR-03, SR-08, SR-17, SR-19) · Conditional 6 (SR-02, SR-04, SR-05, SR-09, SR-11, SR-18) · Unverified 8 (SR-06, SR-07, SR-10, SR-12–SR-16)** = 19.
- **OD-05 / G-3 remains the import gate**: every content row still binds to `OD-05 / G-3`, the register still states "No content has been imported", and the repository still contains synthetic data only.

## 5. Technology version review (owner item 5) — DOCUMENTED, no upgrade performed

- New `docs-m1/tech-stack-version-review.md`: current versions, support timelines, recommended supported versions, migration implications, compatibility matrix, sources.
- Key findings: Spring Boot 3.5's OSS support ended 2026-06-30 (recommended path: tested upgrade to 4.1.x early in M2; springdoc must move 2.8.9 → 3.x with it); AGP 8.5.2 ↔ Gradle 8.9 ↔ JDK 17 ↔ compileSdk 34 is a verified-compatible set (committed wrapper pins Gradle 8.9); AGP 9.x (Gradle ≥ 9.6, build-tools 36) is documented but deliberately **not** adopted — no major-version upgrade without compatibility testing, per the owner's instruction.

## 6. Housekeeping (part of the corrective package)

- Removed leaked build metadata from the tree (`admin/*.tsbuildinfo`) and added ignore rules (`admin/*.tsbuildinfo`, `compose-logs/`).
- `android/README.md` and root `README.md` updated (wrapper usage, vendored-jar checksums, smoke-test entry points, v0.1.1 header).
- Backend tests re-run fresh in this cycle: `Tests run: 7, Failures: 0, Errors: 0` (`docs-m1/evidence/backend-mvn-test.log`); admin re-verified: `npm ci`, vitest **2/2**, `npm run build` (`admin-npm-ci.log`, `admin-vitest.log`, `admin-build.log`).
- **Feature tracker intentionally untouched** — re-verified programmatically: 9 sheets, 104 records, 93 FR mappings (`14-feature-tracker-v1.0.xlsx`, sha256 in the evidence file).

## 7. Deliverables of this corrective revision

1. `m1-platform-foundation-v0.1.1.zip` (this repository, repackaged)
2. Updated `docs-m1/m1-evidence.md` (v0.1.1 evidence set)
3. Corrected Phase 2 source register (`docs-m1/phase2-source-register.md` + `.csv`)
4. Execution evidence for every claim in `docs-m1/evidence/`
5. This change log
