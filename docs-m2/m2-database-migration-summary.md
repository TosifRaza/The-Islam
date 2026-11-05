# M2 Database Migration Summary

**Milestone:** M2 — Admin CMS Foundation
**Strategy:** approved forward-only, per-module folders (`db/migration/identity`, `db/migration/content`); Flyway Community; **no Undo**; no already-applied migration modified; `validate-on-migrate: true`; Hibernate `ddl-auto: none` (Flyway is the sole schema authority).

## Applied order (verified on a fresh real PostgreSQL 14 — `FlywayMigrationTest` + E2E smoke)

| Version | File | Provides |
|---|---|---|
| 1 | `identity/V1__identity_and_access.sql` *(M1, unchanged)* | roles/permissions/users/refresh_tokens/email_verifications/password_resets |
| 2 | `content/V2__content_spine.sql` *(M1, unchanged)* | subjects (15-position spine), licences, media_assets, content_versions, content_review_records, append-only audit_logs |
| 3 | `identity/V3__rbac_expansion.sql` **(new, M2)** | Permission-catalogue expansion: `users.view`, `users.suspend`, `users.delete`, `audit.view`, `files.upload`, `fatwa.create` (catalogue rows only — grants live in the repeatable below); `users.last_login_at`; re-asserts the Fatwa separation-of-duties guard |
| 4 | `content/V4__admin_cms.sql` **(new, M2)** | See §1 below |

Repeatable seeds (order = description order): `R__seed_library_categories` *(M1)*, `R__seed_reference_licences` **(new)**, `R__seed_roles_permissions` *(M1)*, `R__user_admin_role_grants` **(new — expanded)**.

## 1. V4__admin_cms.sql contents

| Object | Notes |
|---|---|
| `authors` | public UUID key, name/name_ar/bio |
| `books` | full metadata (title/subtitle/description/language/publisher/edition/year/pages/isbn/copyright), FKs → subjects, licences, media (cover), users (created_by); `status` CHECK = draft → in_editorial_review → in_scholarly_review → approved → published → archived; `scholarly_review_required` flag; indexes for status/subject/language/lower(title) |
| `book_authors` | composite PK (book_id, author_id) + `sort_order` (display only) |
| `book_files` | pdf/epub attachments; `media_id UNIQUE` (1:1 attach); `download_allowed` per file |
| `fatwas` | dedicated strict-workflow table: draft/in_review/approved/published/archived CHECK; question/answer_draft/answer, `source_refs jsonb`, assigned reviewer, decision + publisher columns, version |
| `account_deletion_requests` | admin-started deletion workflow (pending_review/scheduled/cancelled/purged), `public_id`, purge_at, audit-linked |
| `media_assets` extension | additive columns: `public_id` (NOT NULL, backfilled), `purpose`, `original_filename`, `uploaded_by`, `scanned_at`, `scanner`; indexes on (scan_status, created_at) and owner |
| `subjects` extension | `ADD COLUMN IF NOT EXISTS description text` (admin-editable metadata; V2 itself untouched) |
| `subjects.sort_order` constraint | UNIQUE re-created as `DEFERRABLE INITIALLY DEFERRED` — still enforced at COMMIT; required because reordering permutes values inside 1..15 and an immediately-checked unique index would reject every intermediate statement. The V2 CHECK (BETWEEN 1 AND 15) remains immediate |

## 2. New/changed repeatable seeds

- **`R__seed_reference_licences` (new):** approved reference licence rows (public-domain style entries with attribution flags) used by the licence picker — reference data only, no content.
- **`R__user_admin_role_grants` (expanded):** adds ONLY the grants the approved Permission Matrix requires for M2 (users.view/suspend/delete, audit.view, files.upload, fatwa.create; `academy.manage` extended to Super Admin/Admin/Content Manager per matrix). **No established M1 grant is altered.** Idempotent upserts; the Fatwa disjointness guard (no role holds both `fatwa.publish` and `fatwa.review.approve`) is re-asserted after every re-application. Grant rationale documented inline per matrix row.

## 3. Compatibility and data-safety notes

1. **No destructive change** — no column drops, no type changes on applied tables; the only DDL on M1 tables is additive (`subjects.description`, `media_assets.*`, constraint deferral) plus the additive `users.last_login_at` (V3).
2. **Flyway history** — all 8 migrations report `success = true` in `flyway_schema_history` on the fresh-apply evidence run (E2E smoke log).
3. **No religious content** — migrations create containers and reference data only; content import remains gated at OD-05/G-3. Test/dev data is synthetic and clearly labelled.
4. **Rollback policy** — forward-only; a defective change would be corrected by a NEW versioned migration, never by editing applied history.
5. **Performance** — indexes added for the admin list/search paths (books status/subject/language/title, media scan-state/owner, deletion requests by user, fatwas state/reviewer).
