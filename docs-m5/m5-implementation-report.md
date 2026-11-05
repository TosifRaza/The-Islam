# M5 Implementation Report

Milestone: M5 — Learning & Research Modules (Phase 12) · 2026-11-03

## What was implemented

### Backend (`app.ikp.backend` — 4 new modules, 29 controllers total)

| Module | Contents |
|---|---|
| `academy/` | `AcademyCourse/Chapter/Lesson/Enrolment/LessonProgress` entities + repos, `AcademyService` (state machine, reviewer ≠ creator, ownership-scoped progress, continue-rail), `AdminAcademyController` (CRUD + workflow actions), `PublicAcademyController` |
| `assessment/` | `AssessmentQuiz/Question/QuestionVersion/QuestionOption/Attempt/AttemptAnswer` + `FlashcardDeck/Flashcard`; `AssessmentService` (versioned question bank, review decisions, presented-set snapshot, server-side grading with time limit + grace, weak topics, deck separation); `AdminAssessmentController`, `PublicAssessmentController` |
| `research/` | `ResearchTopic/Article/ArticleCitation`; `ResearchService` (ten-topic taxonomy, three-tier classification, citation resolution against the imported Quran); `AdminResearchController`, `PublicResearchController` |
| `scholars/` | `Scholar` + `ScholarService` (admin-recorded credentials, audited verify/revoke, user linking); `AdminScholarController`, `PublicScholarController` |
| `fatwa/` (extended) | `FatwaPublicController` — published-only directory with reviewing-scholar attribution resolved from the approval audit trail |
| wiring | `SecurityConfig` M5 GET whitelists; `GlobalSearchController` live courses/lessons/research/scholars/fatwas groups; `DashboardService` real course/enrolment/attempt counts; `DevDataSeeder` harness accounts (incl. academy_manager); `R__user_m5_role_grants.sql` |

### Admin (`ikp-admin`)

- Features: `academy/` (courses, chapters/lessons, workflow actions),
  `assessment/` (quizzes, versioned question bank with review decisions,
  decks), `research/` (topics, articles, citations, review), `scholars/`
  (profiles + verification). Service modules `api/academy.ts`,
  `api/research.ts`. Nav + guarded routes in `AdminLayout`/`App.tsx`.
- 9 new vitest cases (`src/test/m5-pages.test.tsx`) — 40/40 total.

### Android (`com.ikp.dev.app` — M5 surface completed)

- Completed from the mid-flight state: real `ResearchFragment` (three
  directory modes + fatwa query), new `ResearchArticleFragment`
  (tier classification + citations), `ScholarDetailFragment`,
  `FatwaDetailFragment` (OD-08 disclaimer always visible), 8 nav graph
  destinations with args, `CourseRows` mapping util, 37 strings + Arabic
  core subset, missing imports/resources fixed, version → 0.5.0-m5 (code 3).
- New `M5LearningTest` (9 JVM tests): row mapping, Gson envelope contract,
  **pre-submission leak invariant** (QuestionPublicDto can never carry
  correctness data), submit-body shape (questionId + selected option only).
- Architecture unchanged: single-activity Navigation Component, MVVM with
  manual ServiceLocator, Retrofit `Call.execute()` + `Outcome`/`ErrorMapper`,
  `StateView` loading/empty/error(+retry), app-wide offline banner.

## Design decisions (recorded)

1. **Offline scope**: M5 content is online-first; Room stays at v2. Offline
   reading/caching + synchronisation belong to Phase 14 (M7) per the approved
   phase roadmap — declared here rather than silently absent.
2. **Certificates**: not implemented (OD-11 not approved). Course completion
   status exists (progress → completed); no certificate path anywhere.
3. **Instructor fields**: display-only, recorded by admins, audited; the
   platform never generates or validates credentials (no fabricated
   qualifications).
4. **Question integrity**: versioned bank — publishing requires an approved
   version; any edit invalidates approval and bumps the version; reviewer must
   differ from author.
5. **Options in the pre-submission payload** (fix found by the live-stack
   harness): options are included as id + text only. The M5 test suite missed
   this because its submit used a fabricated option id; the regression test
   `attemptPayloadIsAnswerableAndCorrectOptionGradesFullScore` now locks the
   real flow.
6. **Scholar–review attribution**: fatwa review blocks resolve the reviewing
   scholar via the linked scholar profile of the reviewer user; no attribution
   is claimed without a recorded approval.

## Known limitations (honest)

- Device/instrumentation tests compiled, not executed (no emulator in this
  environment) — checklist in `m5-device-testing-checklist.md`.
- Saved lessons / difficult-question / revision lists live through the
  user-content spine (bookmarks/notes) without a dedicated revision-centre UI.
- Research related-resources panel awaits the M6 citation graph.
- Scholar photo field exists; upload pipeline not wired (no licence/UX need
  yet).
