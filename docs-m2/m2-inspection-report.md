# M2 Pre-Implementation Inspection Report — M1 Foundation

**Milestone:** M2 — Admin CMS Foundation
**Inspected:** 2026-11-02 · Tree: `download/platform-dev/` (= packaged `m1-platform-foundation-v0.1.1.zip`)
**Method:** direct file inspection of the actual M1 sources — backend, admin, migrations, Docker, CI, tests, evidence. No module was modified during inspection (except where explicitly noted as a pre-existing defect below).

---

## 1. What already exists (and works)

### 1.1 Backend — Spring Boot 3.5.6 / Java 21 (`backend/`)

| Element | State | Notes |
|---|---|---|
| `pom.xml` | ✅ Working | Boot 3.5.6, Java 21, web/validation/actuator/**jdbc**/flyway/postgres/springdoc 2.8.9, Zonky embedded-postgres 2.1.1 (test). **No Spring Security, no Spring Data JPA yet** — both arrive in M2 (approved stack). |
| `BackendApplication` | ✅ Boots | Flyway applies migrations on startup from two classpath locations. |
| `application.yml` | ✅ | Forward-only Flyway (identity + content folders), `validate-on-migrate`, no undo; envelope conventions documented; `app.api.base-path=/api/v1`. |
| `HealthController` | ✅ | `GET /api/v1/health` with `{"data": …}` envelope; 503 when DB down. |
| `GlobalExceptionHandler` | ✅ (partial) | `VAL-001` validation + `IllegalArgumentException`. M2 extends it to the full approved `application/problem+json` error catalogue (AUTH-*, AUTHZ-*, NF-001, UPLOAD-*, WORKFLOW-001, QUOTA-001, LIC-001, RATE-001). |
| Tests (7) | ✅ Green | `FlywayMigrationTest` (5 — real PostgreSQL via Zonky: 11 roles, disjoint Fatwa duties, 15 category positions, no religious-content tables, Flyway history) + `HealthEndpointTest` (2 — full context on migrated real PostgreSQL). |

### 1.2 Flyway migrations (forward-only, per-module folders)

| Migration | Provides |
|---|---|
| `identity/V1__identity_and_access.sql` | `roles`, `permissions`, `users` (public_id UUID, status `active/suspended`, deletion_state, language_pref, privacy_settings), `user_roles`, `role_permissions`, `refresh_tokens` (hashed, revocable), `email_verifications`, `password_resets`. `oauth_accounts` intentionally absent (OD-04/G-2). |
| `identity/R__seed_roles_permissions.sql` | 11 roles; 8 permission codes (`content.edit`, `content.publish`, `fatwa.publish`, `fatwa.review.approve`, `users.manage`, `roles.manage`, `academy.manage`, `support.view_users`); **disjoint Fatwa publish/approve grants with a DB-level guard**. |
| `content/V2__content_spine.sql` | `subjects` (slug = stable join key; `sort_order` 1–15 = display order ONLY; status `active/hidden_unconfirmed/archived`), `licences`, `media_assets` (storage_key, mime, sha256, `scan_status`), `content_versions` (snapshot history), `content_review_records` (editorial/scholarly stages, decisions, notes), **append-only** `audit_logs` (actor, action, entity, before/after hashes, ip, user_agent). |
| `content/R__seed_library_categories.sql` | The 15 client-reference positions with stable slugs; position 06 = `cat_unconfirmed_06`, `hidden_unconfirmed`; idempotent upsert + guard (exactly 15; only 06 may be hidden). |

### 1.3 Admin dashboard — React 18 + TS + Vite + Tailwind (`admin/`)

- Approved design tokens already in `tailwind.config.js`: `emerald.deep #164638`, `gold.islamic #C7A75A`, `ivory.warm #F7F6F0`.
- Foundation shell: placeholder login (no backend auth — declared "OD-04 pending"), foundation dashboard, `HealthBadge` (UP/unreachable states), `/api` dev-proxy + nginx `/api/` proxy, Dockerfile (build → nginx:alpine), vitest + testing-library wired (2 tests).
- **No router, no react-query, no auth context, no feature modules** — M2 adds these on the existing structure.

### 1.4 Infrastructure & CI

- `docker-compose.yml`: postgres:16-alpine (healthcheck) + backend (build) + admin (nginx, `/api/` proxy) — dev only.
- `Makefile` (v0.1.1-corrected): `backend-test`, `admin-install/test/build`, `android-test` (JVM tests executing 3 tests via vendored JUnit, exit-code-verified), `compose-up/down/smoke`.
- `.github/workflows/ci.yml` (v0.1.1): 4 jobs — `backend` (mvn test), `admin` (npm ci/test/build), `android` (explicit SDK install + committed wrapper `assembleDebug testDebugUnitTest` + JUnit XML summary gate), `stack-smoke` (Docker-enabled compose integration test, needs Docker runner).
- Android app: untouched by M2 (no mobile-facing API change; M2 endpoints are admin-only).

### 1.5 Phase 2 source register (docs-m1)

19 CSV records (SR-01…SR-19) reconciled with the Markdown status summary in the v0.1.1 cycle (SR-05/SR-11/SR-19 alignment verified by `scripts/verify_source_register.py`). **No Islamic content imported; every content import row remains gated at OD-05/G-3.** M2 does not touch the register; book records created in M2 are empty containers/synthetic dev fixtures — never religious text.

### 1.6 Tracker & evidence

- `14-feature-tracker-v1.0.xlsx` — 9 sheets, 104 records (93 FR + 11 cross-cutting), Permission Matrix (11 roles × capability areas) = the RBAC authority for M2.
- `docs-m1/m1-evidence.md` + `tech-stack-version-review.md` (v1.0): Spring Boot **3.5.6** OSS support ended 2026-06-30; **recommended** tested upgrade to 4.1.x *early in M2 — subject to owner approval*. The M2 master prompt pins "Spring Boot 3.5.x as established in M1" and forbids unapproved stack changes → **M2 stays on 3.5.6**; the upgrade remains a documented owner decision (§6 below).

## 2. What needs extension (M2 work)

| Area | Extension |
|---|---|
| Security | Spring Security 6 filter chain, Argon2id password hashing, HS256 JWT access tokens (15 min), rotating refresh tokens with reuse detection (table already exists), server-side session registry, per-request authority resolution from the DB (immediate suspension enforcement). |
| RBAC | Permission seed covers only 8 codes; the approved Permission Matrix requires `users.view`, `users.suspend`, `users.delete`, `audit.view`, `files.upload`, `fatwa.create` and additional role grants (Admin/Support Manager user actions; Content Manager subject rights). New **V3** migration — no established grant is altered. |
| Persistence | Spring Data JPA + Hibernate (`ddl-auto: none` — Flyway remains the sole schema authority) on top of the existing JDBC-only stack. |
| Schema | New **V4** migration: `authors`, `books`, `book_authors`, `book_files`, `fatwas`, `account_deletion_requests`; `media_assets` gains purpose/original_filename/uploaded_by/scanned_at/scanner columns; indexes for admin lists. Existing migrations untouched. |
| API | The admin families from the approved spec: `/auth/**`, `/admin/users/**`, `/admin/dashboard`, `/subjects` + `/admin/subjects/**`, `/admin/files/**`, `/library/books` (public) + `/admin/books/**`, unified workflow `/admin/{entity}/{id}/{action}`, `/admin/fatwas/**` (dedicated strict family), `/admin/audit-logs`. Envelope + problem+json per spec. |
| Storage | M1 has **no** storage implementation. M2 adds the dev object-storage service (private local filesystem buckets, `quarantine/` → `content/` promotion, short-lived authorised download tokens) + scanner interface with an explicitly test-only scanner (fail-closed). |
| Frontend | Router, auth context + protected routes, permission-aware navigation, 8 CMS feature modules, shared component library (loading/empty/error/confirm/toast states), upload progress, real API integration for every control. |
| Error handling | Full approved error-code catalogue in `GlobalExceptionHandler` (problem+json). |
| OpenAPI | GroupedOpenApi groups mirroring the implemented families; springdoc already present. |

## 3. What is missing entirely (built in M2)

Admin authentication UX · dashboard KPIs · user management · subject CRUD UI · book management + uploads · review queues · publishing workflow with Fatwa separation-of-duties enforcement · searchable audit log · dev seed data (synthetic, labelled, dev-profile-only).

## 4. Existing technical limitations

1. **No Docker daemon in the development sandbox** — the compose stack cannot be container-executed here (same honest limitation as M1 v0.1.1 §8). Verification strategy: Zonky **real PostgreSQL** for all DB-backed tests + the native whole-stack smoke script (real processes, same six checks); `compose-smoke.sh` remains CI-wired for Docker runners.
2. **No real malware scanner** in dev (no ClamAV daemon) → scanner **interface** + test-only scanner labelled as such; quarantine fail-closed; publishing requires `scan_status = clean`. No file is ever reported clean without an actual scan verdict from the configured scanner.
3. **OD-04 (auth provider) still open** → M2 implements the approved *baseline*: email + password (Argon2id) + JWT/refresh. No OTP/social provider is chosen; `oauth_accounts` remains unbuilt; the design keeps the V1 extension point.
4. **Spring Boot 3.5.6** is pinned by the M2 prompt; OSS-EOL status is documented, upgrade pending owner approval (no unapproved version change).
5. Object storage is the **development** configuration (local filesystem, private-by-default); S3/MinIO + production scanning are OD-06-gated later phases.

## 5. Pre-existing defects found during inspection (fixed in M2 batch M2.1)

| # | Defect | Impact | Fix |
|---|---|---|---|
| D-1 | `admin/src/components/HealthBadge.tsx` line 6: `const ealth, setHealth] = useState…` — the literal `const [h` characters are missing (corruption introduced after the last recorded M1 build; the file cannot type-check). | `npm run build` / `npm test` fail on the current tree. | Restore the destructuring line; re-run the full admin build + tests as part of M2. |

No other corruption found: all backend sources, migrations, workflows and the Makefile parse/compile as delivered in v0.1.1.

## 6. M2 implementation dependencies (resolved)

| Dependency | Resolution for M2 |
|---|---|
| Auth foundation | M1 schema (users/roles/permissions/refresh_tokens) + approved spec §3.1 + security plan §2 → email+password baseline, Argon2id, 15-min JWT, rotating refresh, session registry. OD-04 stays open (no provider chosen). |
| RBAC authority | Tracker `Permission Matrix` sheet (11 roles) — implemented via new permission codes in V3; M1 grants preserved verbatim. |
| Content model | V2 spine (subjects/licences/media_assets/review records/audit) + V4 additions below it (books/fatwas) with FKs into the existing tables. |
| Category rules | Stable slugs; `sort_order` display-only; 06 hidden & unnamed; admin API sees it, public API never returns it (enforced by query filters + tests). |
| Fatwa gate | Permission disjointness (V1 seed) + state machine (draft→in_review→approved→published→archived) + publish-time server re-validation (state, scholar approval record, publisher ≠ reviewer) + audit of every denied attempt + negative tests for every bypass route. |
| Tracker | v1.0 structure preserved (104 records); M2-relevant rows updated to actual statuses with evidence pointers. |
