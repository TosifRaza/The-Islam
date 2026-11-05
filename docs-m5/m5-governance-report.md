# M5 Governance & Content-Licensing Report

Milestone: M5 — Learning & Research Modules · 2026-11-03
Companions: docs-m4/m4-content-licensing-report.md (source register), OD-08.

## 1. Fatwa governance (OD-08) — verified live

The M5 harness exercised the full chain with distinct role accounts:

1. draft created by an account holding `fatwa.create` (super_admin/admin/
   scholar_reviewer only — content_manager correctly 403);
2. `submit-review` → `assign-reviewer` (assignee must hold SCHOLAR_REVIEWER);
3. scholarly `decision` — **scholar_reviewer only**;
4. `publish` — blocked for the reviewing scholar (403; publisher ≠ reviewer;
   `fatwa.publish` and `fatwa.review.approve` can never share a role —
   migration guard re-asserted in `R__user_m5_role_grants.sql`);
5. public directory serves **published** rows only, with reviewing-scholar
   attribution resolved from the recorded scholarly approval
   (`content_review_records`) — never claimed without a record.

No AI path exists anywhere in M5 (the AI subsystem is deliberately absent
until M6 — `FlywayMigrationTest` still asserts `ai_chunks` does not exist).
When M6 arrives, the same gate applies: AI-generated rulings are never
auto-published.

## 2. Religious-content integrity

| Rule | Implementation |
|---|---|
| No fabricated scripture | M5 adds no scripture; courses/lessons/quizzes arrive only through the admin CMS |
| Citation verification | `quran_ayah` citations are resolved against the imported Quran (SR-01) at save time; unknown refs rejected (VAL-001, harness-verified with surah 999) |
| Source tiers | `research_articles.source_tier` ∈ primary/secondary/editorial — schema CHECK + explicit UI badge |
| No invented scholarship | scholar qualifications/biographies are admin-recorded and audited; instructor fields are display-only; nothing auto-generated |
| Question accuracy | versioned question bank; publish requires editorial approval of the current version; reviewer ≠ author; version history immutable |
| Answer integrity | quiz explanations stored server-side; exposed only after submission |

## 3. Licensing posture (unchanged invariants, M5 surfaces)

- M5 imports no third-party content. The only content consumed is the M4
  Quran import (Tanzil SR-01, verbatim + attribution) — used by the citation
  verifier and search, within the recorded licence terms.
- Course PDFs/videos attach through the existing `media_assets` pipeline
  (scan gate + signed URLs); admins remain accountable for the licence of
  anything they upload (`licences` registry + per-book/per-course fields).
- Fixtures regime untouched: `allow-fixtures=false` still hides every
  labelled fixture row; M5 serving paths follow the same
  `publish_state`-only public rule.

## 4. Audit trails

- Every M5 admin write and workflow transition writes `audit_logs`
  (actor, action, entity, before/after) via `AuditService`.
- Editorial + scholarly review decisions are persisted in
  `content_review_records` (stage, decision, notes, reviewer, decided_at) —
  the fatwa review block reads from this table, not from claims.

## 5. Open decisions respected

| OD | Status in M5 |
|---|---|
| OD-07 (no paid AI / no unauthorised ingestion) | untouched — no AI service contacted, no new ingestion |
| OD-08 (fatwa scholar approval) | enforced and harness-verified (§1) |
| OD-11 (certificates) | not implemented — excluded until approved |
