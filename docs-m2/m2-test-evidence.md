# M2 Test Evidence

**Milestone:** M2 — Admin CMS Foundation
**Execution date:** 2026-11-02 · **Environment:** sandbox (no Docker daemon) — Spring Boot 3.5.6 / Java 21 / Maven 3.9.16 / Zonky embedded **real PostgreSQL 14.19** for backend tests; user-local PostgreSQL 14.19 + packaged jar for the whole-stack E2E; Node 24 + Vite/Vitest for the admin.

## 1. Result summary

| Suite | Tests | Result |
|---|---|---|
| Backend (Maven `mvn test`) | **53** | **53 passed, 0 failed, 0 skipped — BUILD SUCCESS** |
| Admin (Vitest `npm test`) | **28** | **28 passed, 0 failed — 5 files** |
| Admin production build (`tsc -b && vite build`) | — | **SUCCESS** |
| Whole-stack E2E smoke (`scripts/m2_stack_smoke.sh`) | **22 checks** | **ALL PASSED** (log: `docs-m2/m2-e2e-stack-smoke.log`) |

## 2. Backend suites (real PostgreSQL, full security chain, random port)

| Suite (file) | Cases | Covers (M2 §14) |
|---|---|---|
| `subject/SubjectCmsIntegrationTest` | 9 | Admin sees all 15 incl. category 06; public list/detail exclude 06 (404); admin metadata edits without code change; 06 rename/activate refused (WORKFLOW-001); create/delete refused; reorder validation 1..15 + slug stability; RBAC denial; writes audited with state hashes |
| `fatwa/FatwaSecurityIntegrationTest` | 9 | **Negative test for every bypass route:** publish from draft / from in_review (no approval) → WORKFLOW-001 + `fatwa.publish_deny` audited; non-scholar decision → 403; unified weak path refuses fatwas (NF-001); publisher == approving reviewer refused (incl. a hybrid user holding both roles); edit-after-approval resets to draft and blocks publish; assignee without scholar role → 403 + `fatwa.assign_deny` audited; Super Admin cannot bypass missing approval; positive control: scholar approves → **different** admin publishes → 200 + per-fatwa audit trail |
| `book/BookWorkflowIntegrationTest` | 6 | **Full E2E publication journey** (author → cover+pdf upload → draft with metadata → attach scan-clean file → submit-review → approve-editorial → publish by authorised user → visible in public library + detail + download-token → `workflow.publish` audited); invalid transition refused + denial audited; reject requires notes; draft-only editing; scholarly path requires Scholar Reviewer and a `content.publish` publisher; review history recorded; quarantined media can never attach (UPLOAD-002); LIC-001 download restriction; registered user denied admin book APIs |
| `storage/MediaUploadIntegrationTest` | 8 | Clean upload promoted with `test-only-magic-scanner` verdict; EICAR fixture quarantined and never served; magic-byte mismatch → rejected; wrong extension → UPLOAD-001; oversize → UPLOAD-003; quota exhaustion → QUOTA-001; unsigned/forged media access denied (AUTH-002), signed URL serves bytes; inventory filtering |
| `auth/RbacIntegrationTest` | 8 | Backend-enforced RBAC over the real chain: content manager denied user list; Support Manager sees masked emails (admin sees unmasked); suspend permission split; role changes Super-Admin-only; self-suspend refused; scholar sees dashboard / registered user denied; audit view admin-only |
| `dashboard/DashboardAuditIntegrationTest` | 4 | Dashboard metrics from real records (incl. honest zeros + OD-05 gated flags); approval queue contains submitted book; recent activity lists real audit actions; review-queue state filter; audit search filters + Support denied; deletion workflow (Super Admin schedules / Admin files) + unsuspend round trip; user activity feed shows the user's own `auth.login` |
| `migration/FlywayMigrationTest` | 7 | Fresh-apply of V1–V4 + repeatables on real PostgreSQL: 11 roles; disjoint Fatwa duties (DB-level guard); 15 category positions with only 06 hidden; no religious-content tables; Flyway history integrity |
| `web/HealthEndpointTest` | 2 | Health endpoint on the migrated database |

**Fixed during M2 verification (all regression-covered by the suites above):** token-filter lazy-init crash masked as 401; PostgreSQL `inet`/`jsonb` binding type codes; nullable-parameter search queries converted to dynamic Specifications; dropped sort in the admin user search (page-1 heap-order bug); `BookAuthor` identity-PK vs composite table; `subjects.sort_order` UNIQUE made DEFERRABLE for permutation-safe reordering; missing `subjects.description` column (V4, forward-only); self-invocation bypassing `@Transactional` on the workflow entry point (state changes not persisting); dev seeder treating the password entry as a role code.

## 3. Admin suites (Vitest + Testing Library, jsdom)

| File | Cases | Covers |
|---|---|---|
| `test/auth.test.tsx` | 6 | Redirect of unauthenticated visitors; client-side login validation; shell renders with role-filtered navigation; registered user sees reduced nav; direct guarded URL → permission-denied page; scholar sees review queue but not user management |
| `test/dashboard.test.tsx` | 4 | KPIs/queue/activity render from backend payloads; honest zero sections + OD-05 gated metric; error state with retry; KPI navigation to users |
| `test/users-subjects.test.tsx` | 6 | Users list with roles/status/actions; guard blocks page without `users.view`; suspension dialog requires ≥10-char audited reason; roles editor offers the 11 approved roles; subjects table shows exactly 15 rows with slugs; category 06 locked (no edit/publish controls) |
| `test/books-review-audit.test.tsx` | 10 | Books list filters/status; empty state with create action; state-valid transitions only (draft → submit only; editorial review → approve/reject; publish hidden until approved); draft privacy notice; review-queue tabs + open links; scholar-only decision panel (admin sees none — separation of duties in the UI); approved Fatwa offers publish; upload inventory with scan verdicts and quarantined rows non-downloadable; test-only scanner disclosed; audit table with actor/entity/IP/state hashes |
| `components/HealthBadge.test.tsx` (M1) | 2 | UP / unreachable states |

## 4. Whole-stack E2E (`docs-m2/m2-e2e-stack-smoke.log`)

Real processes: user-local PostgreSQL 14.19 → packaged Spring Boot jar (Flyway V1–V4 fresh-apply) with `IKP_SEED_DEV_DATA=true` → REST over HTTP. 22/22 checks passed:

admin login (seeded synthetic account) · auth/me roles+permissions · author created · cover uploaded scan-clean · pdf uploaded scan-clean · book created as draft · scan-clean pdf attached · submit-review → in_editorial_review · approve-editorial → approved · publish → published · public library lists the book · public detail with metadata · publish audited · draft publish refused (WORKFLOW-001) · fatwa publish from draft refused (WORKFLOW-001) · scholar reviewer assigned · scholar approves (sole authority) · scholar cannot publish (separation) · different admin publishes approved fatwa · fatwa audit trail records review + publish · public subjects exclude reserved 06 (14 visible) · reserved 06 detail is 404 publicly.

The admin dashboard was also served against the running backend (Vite dev, `/api` proxy): UI HTTP 200 and a real login through the proxy returned a token.

## 5. Not executed / honest limitations

1. **Docker Compose execution** — no Docker daemon in this sandbox; the compose stack is verified by CI wiring (`stack-smoke` job, Docker-required runner) and by the equivalent native whole-stack smoke above (same six checkpoint areas: PostgreSQL up, Flyway migrations, Spring Boot up, health endpoint, admin UI served, API proxy).
2. **Malware scanning** — `test-only-magic-scanner` performs NO real AV analysis (labelled everywhere); publishing stays fail-closed; production ClamAV-class engine is an OD-06 requirement.
3. **Android** — M2 touches no mobile-facing API; Android tests were not re-run for M2 (M1 evidence stands: 3 JVM tests green, exit-code-verified).
4. **Browser E2E (Playwright-class)** — the UI is covered by component/integration tests plus the proxy-level check; a full browser automation suite is recommended for a later hardening milestone.
