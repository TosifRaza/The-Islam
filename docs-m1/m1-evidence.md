# M1 Build & Test Evidence — v0.1.1 (corrective revision)

**Recorded:** 2026-11-02 · Sandbox: Debian 13, x64 · Revision of the v0.1 evidence after the owner's M1 review
**Rule:** the owner requires actual evidence of successful builds and tests. Everything below is a recorded run with the exact commands and exit codes, not a claim. Environment limitations and workarounds are listed honestly in §8. The item numbering maps 1:1 to the owner's review items.

## 1. Android test execution — owner item 1 (FIXED)

- Toolchain: Temurin JDK 21 (`javac 21.0.13`), vendored JUnit 4.13.2 / Hamcrest 1.3
- Command: `make android-test` (corrected Makefile — repo-root paths, JUnit on the classpath, tests actually executed, exit code propagated)
- Result (`evidence/android-jvm-test.log`): **`OK (3 tests)` — exit code 0**
  - `displayFormatsVersionMilestoneAndCode`, `rejectsEmptyVersionName`, `rejectsNonPositiveVersionCode` — all executed
- Negative control proving the exit semantics: a deliberately failing JUnit run exits **1** (`Tests run: 1, Failures: 1`) — the target therefore returns 0 **only when all tests pass**.

## 2. Android build through the committed Gradle wrapper — owner item 2 (FIXED)

- Toolchain: Temurin JDK 17 (`javac 17.0.21`), Gradle **8.9** (wrapper committed at `android/gradle/wrapper/`, jar sha256 `49849512…4f17`), Android SDK **platform-tools / platforms;android-34 / build-tools;34.0.0** (licenses accepted, installed explicitly — same packages the CI now installs)
- Commands & results:
  - `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL, exit 0** (`evidence/android-gradle-build.log`)
  - `./gradlew clean assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL in 16s, 39 actionable tasks, exit 0** (`evidence/android-gradle-build-clean.log`)
  - Artefacts: `app/build/outputs/apk/debug/app-debug.apk` produced; JUnit XML **`tests="3" skipped="0" failures="0" errors="0"`**
- **Build-breaking defect found and fixed during this correction**: `applicationId` was at the top level of `android{}` (DSL rejects it — configuration failed with `Could not find method applicationId()`), meaning no Gradle build of v0.1 could ever have succeeded. Moved into `defaultConfig{}` (OD-01 comment preserved).
- Transparency: the sandbox rate-limit workaround and the two failed attempts that led to the fixes are preserved (`evidence/android-gradle-build-attempt1-central429.log`, `…-attempt2-applicationid-fix-pending.log`, `…-attempt3-central429-initgap.log`).
- CI workflow (`.github/workflows/ci.yml`) now: explicit SDK package install → `./gradlew --no-daemon assembleDebug testDebugUnitTest` with the committed wrapper → prints the JUnit summary.

## 3. Admin dashboard — re-verified fresh in this cycle

- Toolchain: Node v24.22.2, npm 11.19.1
- `npm ci --no-audit --no-fund` → exit 0 (`evidence/admin-npm-ci.log`)
- `npm test` → **Test Files 1 passed · Tests 2 passed** (vitest; HealthBadge UP / unreachable states) (`evidence/admin-vitest.log`)
- `npm run build` → **`tsc -b && vite build` succeeded**, dist emitted (`index.html` 0.42 kB, CSS 8.00 kB, JS 145.95 kB) (`evidence/admin-build.log`)

## 4. Backend — re-verified fresh in this cycle

- Toolchain: Temurin JDK 21, Apache Maven 3.9.16
- Command: `mvn -B -ntp test` → **`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** (`evidence/backend-mvn-test.log`)
  - `FlywayMigrationTest` (5): migrations apply on a **real PostgreSQL** (Zonky embedded binaries — no Docker), repeatable seeds idempotent, exactly 11 roles, Fatwa publish/review duties disjoint with Scholar Reviewer as sole review authority, 15 category positions with stable slugs (`cat_unconfirmed_06` hidden at position 06, Arabic Language Academy at 07), **no religious-content tables exist** (G-3/OD-05), Flyway history recorded
  - `HealthEndpointTest` (2): full Spring context on migrated real PostgreSQL; `GET /api/v1/health` returns the approved envelope

## 5. Whole-stack verification — owner item 3 (VERIFIED, Docker-enabled test added)

- **Docker-enabled integration test delivered**: `scripts/compose-smoke.sh` (+ `make compose-smoke`, CI job `stack-smoke` on Docker-equipped GitHub runners). Boots the compose stack (postgres:16-alpine + backend + admin/nginx) and asserts the six owner checks, failing non-zero otherwise.
- **Executed in this sandbox without Docker** (no daemon — honest limitation, §8) via `scripts/stack-smoke-native.sh`, same six checks against real processes (`evidence/native-stack-smoke.log`):
  1. **PostgreSQL starts** — real PostgreSQL 14.19 binaries (Zonky, the same the backend tests use): `initdb` + `pg_ctl` start + readiness check → PASS
  2. **Flyway migrations execute** — Spring Boot 3.5.6 jar started against it; `flyway_schema_history` **4 successful rows** (V1 identity, V2 content spine + 2 repeatable seeds) → PASS
  3. **Approved seed content** — `SELECT count(*) FROM roles` = **11** → PASS
  4. **Spring Boot health responds** — `GET /api/v1/health` → `{"data":{"status":"UP","service":"ikp-backend","db":"UP","timestamp":"2026-11-02T09:10:21.006604555Z"}}` → PASS
  5. **React dashboard served through Nginx** — Nginx **1.28.0** (compiled locally; same `try_files $uri $uri/ /index.html` + `/api/` proxy semantics as `admin/nginx.conf`) serving the `admin/dist` build → PASS
  6. **Backend API proxy works** — `GET http://127.0.0.1:8090/api/v1/health` **through Nginx** → same UP envelope → PASS
  - Summary: **PASS: 6, FAIL: 0 — NATIVE STACK SMOKE TEST: SUCCESS, exit 0**
- The first scripted attempt failed 5/6 (Zonky binaries ship no `psql`/`createdb`/`pg_isready`); the script now bundles a minimal JDBC fallback (`scripts/JdbcRun.java`). This discovery is part of the honest record.

## 6. Phase 2 source register — owner item 4 (RECONCILED)

- Corrected records: **SR-05** (normalised to *Conditional — PD basis; per-file confirmation required*), **SR-11** (Conditional; was miscounted in the Markdown summary), **SR-19** (*Verified — reference data (no content license required)*; was wrongly under Unverified in the summary).
- Machine check `scripts/verify_source_register.py` (`evidence/source-register-check.log`): **ALL PASS** — 19 unique CSV records, status strings identical CSV ↔ Markdown for all 19, summary counts/lists match buckets (**Verified 5 · Conditional 6 · Unverified 8**), gates intact (all content rows bound to **OD-05 / G-3**; SR-17/SR-19 non-content), "No content has been imported" statement present.
- **No Islamic content was imported** — the register is identification/verification only, as authorized.

## 7. Technology version review — owner item 5 (DOCUMENTED)

- `docs-m1/tech-stack-version-review.md`: Spring Boot 3.5.6 status (3.5 OSS EOL 2026-06-30; latest 3.5.x = 3.5.16; recommended path = tested upgrade to 4.1.x early M2, springdoc 2.8.9 → 3.x), AGP 8.5.2 ↔ Gradle 8.9 ↔ JDK 17 ↔ compileSdk 34 verified set, AGP 9.x documented but not adopted. **No version change made in v0.1.1** (owner instruction honoured).

## 8. Feature tracker integrity — owner item 6

- `14-feature-tracker-v1.0.xlsx` was **not modified**: re-verified programmatically — 9 sheets, **104 requirement records, 93 FR↔tracker mappings**; sha256 `335af1c024f905db002358d9fd11543016ba908a69a3b5e76a9285b32a1792ad`.

## 9. Honest limitations of this evidence set

1. **GitHub Actions was not executed on GitHub.** This sandbox has no GitHub remote or hosted-runner access. The recorded Android and stack evidence comes from **executing the exact commands and SDK steps the workflow performs**, locally, on the same toolchain versions. The workflow file is valid YAML using only official actions (`actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-node@v4`, `actions/upload-artifact@v4`) and the pinned wrapper; it runs these verified commands verbatim on push.
2. **Docker daemon unavailable** in this sandbox → the compose stack itself was not container-executed here; the Docker-enabled integration test (`scripts/compose-smoke.sh`) is provided and wired into CI, and every check it makes was executed natively with real components (§5). No paid or cloud service was substituted.
3. **Maven Central rate-limits this sandbox IP (HTTP 429)** → backend used the previously documented Aliyun `settings.xml` mirror (outside the repo); the Gradle build used an equivalent init script (outside the repo). CI/GitHub runners resolve the original repositories directly — no repository file embeds a mirror.
4. No paid services, no paid AI providers, no production infrastructure, no Google Play artefact, no unlicensed or imported content — per the owner's standing restrictions.

## 10. Evidence file index (`docs-m1/evidence/`)

| File | What it proves |
|---|---|
| `android-jvm-test.log` | `make android-test`: compile + `OK (3 tests)`, exit 0 (item 1) |
| `android-gradle-build.log` | `./gradlew assembleDebug testDebugUnitTest` BUILD SUCCESSFUL, exit 0 (item 2) |
| `android-gradle-build-clean.log` | from-scratch build: 39 tasks, APK, tests=3/0/0/0, exit 0 (item 2) |
| `android-gradle-build-attempt*.log` | failed attempts kept for transparency (429 mirror workaround; applicationId defect) |
| `backend-mvn-test.log` | 7/7 backend tests, BUILD SUCCESS |
| `admin-npm-ci.log` / `admin-vitest.log` / `admin-build.log` | npm ci; vitest 2/2; production build |
| `native-stack-smoke.log` | six owner checks PASS 6/0, exit 0 (item 3) |
| `source-register-check.log` | 19 records CSV↔MD reconciled, ALL PASS (item 4) |
