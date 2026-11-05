# M5 Database Migration Summary

Milestone: M5 — Learning & Research Modules · 2026-11-03
Migration: `V6__m5_learning_research.sql` (forward-only; V1..V5 untouched).
Repeatable additions: `R__user_m5_role_grants.sql` (identity folder).
Flyway applied **6 versioned + 6 repeatable** migrations in the harness stack.

## V6 tables (17)

| Group | Tables |
|---|---|
| Academy | `academy_courses` (state machine draft→in_editorial_review→approved→published→archived; display-only instructor fields — the platform never generates credentials), `academy_chapters`, `academy_lessons` (lesson_type text/video/pdf/quiz/flashcards; body_text / video_url / media link / quiz_id / flashcard_deck_id), `academy_enrolments` (unique per user+course), `academy_lesson_progress` (unique per user+lesson) |
| Assessment | `assessment_quizzes` (pass_score_pct, time_limit_seconds, optional course link), `assessment_questions` (version + review_state draft→pending_review→approved/rejected; current-version pointer), `assessment_question_versions` (immutable snapshots incl. options jsonb + change_note), `assessment_question_options` (correct flag server-side only), `assessment_attempts` (presented-set jsonb snapshot, status in_progress→submitted/expired, score), `assessment_attempt_answers` (selected option, correct flag) |
| Flashcards | `flashcard_decks` (owner_type admin|user — admin decks NULL owner + publish state, user decks owner-scoped; CHECK enforces the pairing), `flashcards` |
| Research | `research_topics` (10 seeded taxonomy rows — spec Module 11 labels only), `research_articles` (source_tier primary|secondary|editorial; same six-state lifecycle), `research_article_citations` (citation_kind quran_ayah/hadith/book/research_article/external + resolution columns) |
| Scholars | `scholars` (name/name_ar/bio/qualifications/expertise recorded by admins only; verification_status unverified|verified|revoked; optional user link for review attribution) |

## Guards & integrity (in-migration)

- Publish-state CHECKs mirror the books workflow; reviewer ≠ creator and
  publisher ≠ reviewer are service-enforced and audited (same pattern as M2).
- §6.9 **M4 latent-defect fix** (found by M5 tests): `user_notes` lacked
  `UNIQUE (user_id, ref_key)` while the M4 note upsert assumed it — every
  note write failed with a planner error. Fixed with
  `ux_notes_user_ref` (safe: the broken path never produced duplicates).
- FTS indexes: course title+description (gin).

## R__user_m5_role_grants (repeatable, idempotent)

- New permissions: `assessment.manage` (quizzes, versioned questions, admin
  decks), `scholars.manage` (directory CRUD + credential verification).
- Grants: `assessment.manage` → super_admin/admin/academy_manager;
  `scholars.manage` → super_admin/admin/scholar_reviewer.
- `academy.manage` already existed (M2 expansion); research articles reuse
  `content.edit` (research_editor holds it).
- OD-08 guard re-asserted unchanged: no role may hold both `fatwa.publish`
  and `fatwa.review.approve`.

## Android Room

No schema bump (stays v2): M5 repositories are pass-through to the API by
design; content caching + offline sync belong to Phase 14 (M7) per the
approved roadmap. The decision is recorded in `m5-implementation-report.md`.
