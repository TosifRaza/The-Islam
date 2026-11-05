# Islamic Knowledge Platform — Development Foundation (M1 v0.1.1) + Admin CMS (M2, v0.1)

**Version:** v0.1.1 + M2 — the M1 foundation (corrective revision, see `docs-m1/corrective-changelog.md`) extended with the **M2 Admin CMS Foundation**: admin authentication & RBAC, dashboard, user/subject/book management, secure uploads, review & publishing workflow (incl. Fatwa separation of duties) and the searchable audit log. Application scope is unchanged.
**Scope:** M1 = Phase 2 (content source register) + Phase 7 (development foundation). M2 = the admin CMS modules over that foundation (implementation report: `docs-m2/m2-implementation-report.md`).
**Baseline:** the M0-approved blueprint v0.2 (see `../islamic-knowledge-platform-baseline-v1.0.zip` / `BASELINE-APPROVAL.md`). Nothing here changes approved scope; standing owner restrictions apply (no paid services, no paid AI provider, no unlicensed content, no unreviewed religious content, no production infrastructure, no Google Play publishing, no silent feature reduction).
**Content policy:** the repository contains **synthetic/reference data only**. No Quran, Hadith or any religious text is stored in this codebase — content enters exclusively through the controlled import pipeline after licensing verification (see `docs-m1/phase2-source-register.md`, gate G-3 / OD-05).

## Repository layout

```
platform-dev/
├── backend/        Spring Boot 3.5 (Java 21) — REST API + admin CMS services
│   └── src/main/resources/db/migration/   Flyway migrations V1–V4 + repeatable seeds (forward-only)
├── admin/          React 18 + Vite + TypeScript + Tailwind — full admin CMS (M2)
│   └── src/features/{auth,dashboard,users,subjects,books,uploads,review,audit,profile}/
├── android/        Android (Java) app skeleton — committed Gradle 8.9 wrapper (unchanged in M2)
├── scripts/        compose-smoke.sh (Docker stack test) · stack-smoke-native.sh · verify_source_register.py
├── .github/workflows/ci.yml   CI: backend · admin · android (explicit SDK) · stack-smoke
├── docker-compose.yml         local stack: postgres 16 + backend + admin (dev only)
├── Makefile                   one-command test/build entry points
├── docs-m1/                   Phase 2 source register + evidence + version review
└── docs-m2/                   M2 inspection · implementation report · API/migration summaries · test evidence
```

## Admin development accounts (M2)

With `IKP_SEED_DEV_DATA=true` (compose + local dev only; **never production**) the
backend seeds clearly-labelled synthetic accounts (passwords are synthetic, not
secrets): `dev-superadmin@ikp.test / Dev#Super-1234`, `dev-admin@ikp.test /
Dev#Admin-1234`, `dev-content@ikp.test / Dev#Content-1234`, `dev-scholar@ikp.test
/ Dev#Scholar-1234`, `dev-support@ikp.test / Dev#Support-1234`,
`dev-academy@ikp.test / Dev#Academy-1234`, `dev-reader@ikp.test /
Dev#Reader-1234`. Every login and administrative action is audit-logged.

## Prerequisites

| Tool | Version | Required for |
|---|---|---|
| JDK | 21 (Temurin recommended) | backend, android JVM tests |
| Maven | 3.9+ | backend (or use the IDE) |
| Node.js + npm | 20+ (24 tested) | admin dashboard |
| Docker | any recent | optional — local composed stack |
| Android SDK | API 34 | android APK builds (NOT needed for its JVM tests) |

## Quick start (Option 1 — composed stack, needs Docker)

```bash
cp .env.example .env          # adjust if you like; defaults work
docker compose up --build -d  # postgres + backend (migrations auto-apply) + admin
# backend health:   http://localhost:8080/api/v1/health
# backend OpenAPI:  http://localhost:8080/swagger-ui
# admin dashboard:  http://localhost:5173
```

## Quick start (Option 2 — manual)

```bash
# 1. PostgreSQL 14+ running locally with database `ikp`, user `ikp` (see .env.example)
# 2. Backend (applies Flyway migrations V1–V4 on boot):
#    set IKP_SEED_DEV_DATA=true to create the labelled dev accounts
cd backend && mvn spring-boot:run
# 3. Admin dashboard (proxies /api to :8080):
cd admin && npm install && npm run dev   # → http://localhost:5173 → sign in
```

## M2 whole-stack smoke (no Docker required)

```bash
# boots a local PostgreSQL, the packaged jar (with dev seed) and drives the
# complete book publication journey + every Fatwa bypass refusal over HTTP;
# writes docs-m2/m2-e2e-stack-smoke.log — see script header for paths
/home/z/…/scripts/m2_stack_smoke.sh   # (delivery sandbox path; adapt ROOT)
```

## Tests (the commands the CI runs)

```bash
make backend-test     # Spring Boot context + Flyway V1–V4 on a REAL PostgreSQL
                      # (embedded, no Docker needed) — 53 tests
make admin-test       # vitest tests — 28 tests
make admin-build      # tsc + vite production build
make android-test     # compile + EXECUTE the Android JVM unit tests via vendored
                      # JUnit (no SDK needed; needs a JDK ≥ 17 on PATH) — 3 tests
# full Android build (needs Android SDK; wrapper is committed):
cd android && ./gradlew assembleDebug testDebugUnitTest
# Docker-enabled whole-stack integration test (needs Docker):
make compose-smoke    # = scripts/compose-smoke.sh (used by the CI stack-smoke job)
# same six checks without Docker (real postgres/nginx binaries):
scripts/stack-smoke-native.sh   # see script header for required env vars
```

Expected result: **all green** — see `docs-m1/m1-evidence.md` and
`docs-m2/m2-test-evidence.md` for the recorded sandbox runs (versions,
timings, outputs).

## Database migrations — approved policy

Flyway **Community, Option A (forward-only)**: versioned SQL per module folder
(`identity/`, `content/`, …) + repeatable `R__` seeds with data-quality guards.
Flyway Undo (paid) is not assumed; mistakes are fixed by forward/compensating
migrations. Details: `backend/db/migration/README.md`.
The foundation ships: identity & access (users/roles/permissions/sessions),
content spine (subjects with the 15 client-reference positions incl. hidden
`cat_unconfirmed_06`, licences, media assets, versions, review records,
append-only audit log) and the role/permission seeds enforcing the Fatwa
separation of duties (publish and approve are structurally disjoint).
M2 adds (forward-only, nothing applied was modified): `V3__rbac_expansion`
(permission catalogue + `users.last_login_at`), `V4__admin_cms` (authors,
books, book_authors, book_files, fatwas, account_deletion_requests,
media_assets extension, `subjects.description`, deferrable unique for
reordering) and the expanded grant/licence repeatables. Details:
`docs-m2/m2-database-migration-summary.md`.

## Android package-name note (OD-01)

`applicationId com.ikp.dev.app` is provisional. The final applicationId and
branding are frozen only at the branding/package-name freeze before Google Play
submission (gate G-1). Never publish a build with the provisional id.

## CI

`.github/workflows/ci.yml` runs on every push/PR:

- **backend** — `mvn test` (Temurin 21)
- **admin** — `npm ci && npm test && npm run build` (Node 20)
- **android** — explicit SDK package install (`platform-tools`, `platforms;android-34`, `build-tools;34.0.0`), then `./gradlew assembleDebug testDebugUnitTest` with the committed wrapper (Temurin 17)
- **stack-smoke** — `scripts/compose-smoke.sh`: boots the complete compose stack in Docker and asserts PostgreSQL health, Flyway execution, backend health, Nginx-served admin and the `/api/` proxy
