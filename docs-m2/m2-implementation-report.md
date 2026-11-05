# M2 Implementation Report — Admin CMS Foundation

**Milestone:** M2 · **Status:** implementation complete, awaiting owner review
**Baseline preserved:** M0 blueprint v0.2 (approved) + M1 `m1-platform-foundation-v0.1.1` (owner-accepted development basis)
**Date:** 2026-11-02 · **Tree:** `download/platform-dev/` · **Package:** `m2-admin-cms-foundation-v0.1.zip`

---

## 1. What M2 delivered

A working, secure Web Admin Content Management System on the approved stack (Spring Boot 3.5.6/Java 21/PostgreSQL/Flyway + React 18/TypeScript/Vite/Tailwind), backed entirely by PostgreSQL — no static demo:

- **Admin authentication** on the M1 identity foundation: email+password (Argon2id), 15-min JWT access tokens, rotating refresh tokens with reuse detection, session list/revocation, immediate suspension enforcement, protected routes, error and permission-denied states. OD-04 stays open — no OTP/social provider chosen; `/auth/password/**` remains the extension point (password reset not implemented in M2 — documented remainder).
- **Module A Dashboard:** KPIs from live SQL (users, content, review queues), pending-approval queue, recent administrative activity, quick actions, honest zeros, OD-05 gating flags.
- **Module B Users:** search/filter/pagination, masked PII for Support Manager, detail page with roles editor (Super Admin), suspend/unsuspend with audited reasons, deletion workflow (Super Admin schedules 7-day purge / Admin files for review), per-user activity feed.
- **Module C Subjects:** the fixed 15-position client reference; metadata/description/image edits without code changes; publish/unpublish; display-order permutation with deferrable-unique safety; category 06 locked in UI **and** API (rename/visibility/publish all refused — WORKFLOW-001).
- **Module D Books:** full metadata + authors + licences + copyright; draft-first lifecycle; scan-clean file attachment; management list with filters; detail page exposing only state-valid workflow actions; public library API serves published books only.
- **Module E Uploads:** validation (extension, MIME, magic bytes, size, quota, filename sanitisation) → private quarantine → scan → clean promotes; short-lived signed download URLs; test-only scanner clearly labelled; personal-library separation preserved (separate storage domain/table; platform CMS cannot publish user uploads).
- **Module F Review & publishing:** draft → editorial → scholarly (where required) → approved → published → archived, every transition state-validated, permission-checked, review-recorded and audited; central review queue with state tabs; **Fatwa separation of duties enforced server-side** (scholar decides; different authorised user publishes; every bypass route closed and negative-tested).
- **Module G Audit:** searchable append-only audit log (actor, action, entity, before/after hashes, IP, user-agent); denial events recorded in their own transaction; no edit/delete path at any layer.

## 2. Project structure (delta on the M1 tree)

```
platform-dev/
├── backend/src/main/java/app/ikp/backend/
│   ├── auth/        JwtService · TokenAuthenticationFilter · SecurityPrincipal · AuthService · LoginRateLimiter + dto/
│   ├── user/        User/Role/Permission entities, AdminUserController/Service, deletion workflow, dto/
│   ├── subject/     Subject admin+public controllers, service (category-06 rules, reorder), mapper, dto/
│   ├── book/        AdminBook/Author controllers, PublicLibraryController, BookService, entities, dto/
│   ├── storage/     MediaService pipeline, LocalStorageService, Scanner + TestOnlyScanner, DownloadTokenService, controllers
│   ├── workflow/    WorkflowService/Controller (books), ContentReviewRecord/ContentVersion
│   ├── fatwa/       FatwaService (separation-of-duties gate), FatwaAdminController, entities
│   ├── dashboard/   DashboardController/Service (live SQL metrics)
│   ├── audit/       AuditService (in-transaction + REQUIRES_NEW denials), AuditAdminController, entity
│   ├── common/      ApiException (approved error catalogue), GlobalExceptionHandler (problem+json), Pages envelope
│   └── config/      SecurityConfig (Argon2id, stateless chain), OpenApiConfig, DevDataSeeder (fixed)
├── backend/src/main/resources/db/migration/
│   ├── identity/V3__rbac_expansion.sql (new) · R__user_admin_role_grants.sql (expanded)
│   └── content/V4__admin_cms.sql (new) · R__seed_reference_licences.sql (new)
├── backend/src/test/java/app/ikp/backend/{auth,subject,book,fatwa,storage,dashboard,migration,web,support}/  (53 tests)
└── admin/src/
    ├── api/client.ts (envelope + problem+json + single-flight refresh) · api/types.ts
    ├── auth/AuthContext.tsx · routes/ProtectedRoute.tsx (RequireAuth + RequirePermission)
    ├── layouts/AdminLayout.tsx (permission-aware sidebar/topbar) · components/ui.tsx (states, dialogs, toasts, pagination)
    └── features/{auth,dashboard,users,subjects,books,uploads,review,audit,profile}/  (10 pages) + src/test/ (28 tests)
```

## 3. Verification (details in `m2-test-evidence.md`)

| Check | Result |
|---|---|
| Backend build + tests (real PostgreSQL) | **53/53 green** |
| Admin production build | **SUCCESS** (tsc -b + vite) |
| Admin tests | **28/28 green** |
| Flyway fresh-apply V1–V4 + repeatables | **green** (7-case migration test + E2E boot) |
| Whole-stack E2E smoke (real PG + jar + HTTP) | **22/22 PASS** incl. the full book publication journey and every Fatwa bypass refusal |
| API documentation | springdoc groups per family; `/swagger-ui` served |

## 4. RBAC & permissions verification

- Authority is **server-derived per request** (fresh DB lookup; client-asserted roles never trusted); `@PreAuthorize` on every admin family + service-layer state/permission gates for workflow actions.
- 11 approved roles seeded; V3/V4 grant EXACTLY the Permission-Matrix additions (documented inline); no established M1 grant altered; Fatwa permission disjointness re-asserted by a DB-level guard after every seed application.
- Negative-tested: content-manager vs user management, Support-Manager masked PII + no audit view, scholar-only Fatwa decisions, publisher ≠ reviewer, Super Admin cannot bypass missing scholarly approval, hybrid role-holder refused, registered user denied all admin APIs.
- UI mirrors the authority (permission-aware navigation, guarded routes) — hiding controls is treated as UX, never as the security boundary.

## 5. Publishing workflow & Fatwa security results

- Book journey E2E-verified over real HTTP: draft → submit → editorial approve → publish → public API → audited; invalid transitions refused (WORKFLOW-001) with the denial audited in its own transaction; publish additionally requires all attached media scan-clean.
- Fatwa: 9-case dedicated suite + E2E checks close **every** bypass route (draft publish, in-review publish, non-scholar decision, weak unified path, self-publication incl. hybrid role-holder, approval-invalidating edit, unqualified assignee, Super-Admin override attempt). No actual Fatwa content exists anywhere — synthetic fixtures only; public Fatwa surface stays OD-gated.

## 6. Deliverables & documents

| Artefact | Location |
|---|---|
| Source package | `m2-admin-cms-foundation-v0.1.zip` (repo root of the delivery) |
| Feature tracker | `14-feature-tracker-v1.1.xlsx` (9 sheets, 104 rows, 93 FR mappings unchanged; M2 rows updated to approved status vocabulary) |
| Pre-implementation inspection (M2.0) | `docs-m2/m2-inspection-report.md` |
| API change summary | `docs-m2/m2-api-change-summary.md` |
| Database migration summary | `docs-m2/m2-database-migration-summary.md` |
| Test evidence | `docs-m2/m2-test-evidence.md` |
| E2E smoke log | `docs-m2/m2-e2e-stack-smoke.log` |
| Updated installation instructions | `README.md` (§4–5 rewritten: backend env, dev seed accounts, admin dev server, smoke script) |

## 7. Known limitations / remaining work (documented, not hidden)

1. Docker Compose not executable in this sandbox — CI `stack-smoke` job wired; native whole-stack smoke performed instead (same verification points).
2. Scanner is test-only (magic bytes + EICAR); production AV engine = OD-06; publishing fail-closed regardless.
3. Password-reset endpoints whitelisted but not implemented (approved later phase); OD-04 provider decision still open.
4. Scholar profiles/credentials, daily-content management, settings/moderation UI, courses/research/quran/hadith management = later phases (tracker rows correctly Partial/Backlog).
5. Spring Boot 3.5.6 pinned by the approved M2 prompt; the 4.x upgrade remains a documented owner decision (M1 tech-stack review).
6. Content import untouched — OD-05/G-3 gate intact; no Quran/Hadith data anywhere in the system.
