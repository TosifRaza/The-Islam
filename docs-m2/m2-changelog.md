# M2 Changelog — Admin CMS Foundation (v0.1)

Milestone delivered against the M1 v0.1.1 baseline. No approved scope changed; no already-applied migration was modified.

## Added — backend

- **Authentication family** (`/api/v1/auth/**`): Argon2id login, 15-min HS256 access tokens, rotating refresh tokens with family reuse detection, logout, `/me`, session list/revocation; per-request authority resolution from the database (immediate suspension enforcement, AUTH-004).
- **Admin CMS families**: dashboard metrics · users (search/suspend/unsuspend/roles/deletion workflow/activity, masked PII for Support) · subjects (metadata/reorder/publish, category-06 locks) · authors · books (draft-first, metadata/licences/files) · public library API · files (validation → quarantine → scan → signed download) · book workflow (submit/approve/reject/publish/unpublish/archive) · Fatwa family with the mandatory separation of duties · searchable audit log.
- **Security infrastructure**: `SecurityConfig` (stateless chain, CORS, CSP, problem+json entry/access-denied handlers), token filter with fetch-joined authorities, `LoginRateLimiter` (RATE-001).
- **Dev seeder** (`DevDataSeeder`): labelled synthetic accounts behind `IKP_SEED_DEV_DATA` (fixed during E2E: password entry is no longer treated as a role code).

## Added — migrations (forward-only)

- `identity/V3__rbac_expansion.sql` — permission catalogue + `users.last_login_at` + Fatwa guard re-assertion.
- `content/V4__admin_cms.sql` — authors, books, book_authors, book_files, fatwas, account_deletion_requests, media_assets extension, `subjects.description`, deferrable unique for reorder permutations.
- Repeatables: `R__seed_reference_licences` (new), `R__user_admin_role_grants` (expanded per approved matrix; no M1 grant altered).

## Added — admin frontend (React/TS)

- Router + guarded routes; auth context (login/refresh/logout, session storage); permission-aware navigation; shared UI kit (loading/empty/error states, confirm dialogs with mandatory audited reasons, toasts, pagination, upload progress).
- Feature modules: Login · Dashboard · Users (+detail/roles editor) · Subjects (category 06 locked) · Books (list/form/detail with workflow actions) · Files & uploads · Review queue · Fatwas (list/detail/form with scholar-only decisions) · Audit log · Profile & sessions.

## Fixed during verification (regression-covered)

1. Token filter `LazyInitializationException` masked as 401 → auth-specific fetch join.
2. PostgreSQL `inet`/`jsonb` binding (audit IP, fatwa source_refs, version snapshot) → JDBC type codes.
3. Nullable-parameter search queries (users/audit/books/media) → dynamic Specifications.
4. Admin user search dropped its sort → page 1 depended on heap order.
5. `BookAuthor` declared an identity PK on a composite-PK table → `@IdClass`.
6. `subjects.sort_order` UNIQUE → `DEFERRABLE INITIALLY DEFERRED` (V4) for permutation-safe reordering.
7. Missing `subjects.description` column → added in V4 (V2 untouched).
8. `WorkflowController` self-invocation bypassed `@Transactional` — status changes never persisted.
9. Corrupted `HealthBadge.tsx` line (pre-existing defect D-1 from the M2.0 inspection) restored.

## Verification

53/53 backend tests · 28/28 admin tests · admin production build · whole-stack E2E smoke 22/22 (`docs-m2/m2-e2e-stack-smoke.log`).
