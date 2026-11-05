# M5 Changelog

Milestone: M5 — Learning & Research Modules · 2026-11-03
Baseline: M4 delivery (`m4-core-knowledge-modules-v0.1.zip`) + the mid-flight
M5 code found in the working tree (documented in the evidence report §0).

## Backend

- **Added** `V6__m5_learning_research.sql` — 17 tables (academy 5,
  assessment 6, flashcards 2, research 3, scholars 1) + M4 latent-defect fix
  (`user_notes` unique index, documented in the migration).
- **Added** `R__user_m5_role_grants.sql` — `assessment.manage`,
  `scholars.manage` + grants; OD-08 fatwa guard re-asserted.
- **Added** modules `academy`, `assessment`, `research`, `scholars` (entities,
  services, admin + public controllers, DTOs) and `FatwaPublicController`.
- **Changed** `SecurityConfig` (M5 public GET whitelist),
  `GlobalSearchController` (live groups replace placeholders),
  `DashboardService` (real counts), `DevDataSeeder` (+ academy/scholar dev
  accounts).
- **Fixed** (found by the M5 live-stack harness) pre-submission attempt
  payload now carries answerable options (id + text only, no correctness
  data); submitting a real option id previously crashed with HTTP 500.
- **Tests** +25: `M5AcademyIntegrationTest` (8), 
  `M5AssessmentSecurityIntegrationTest` (9 incl. the answerable-payload
  regression), `M5ResearchScholarsIntegrationTest` (8). Suite: 92/92.

## Admin

- **Added** Academy / Assessments / Research / Scholars pages (+ nav + guarded
  routes), service modules `api/academy.ts`, `api/research.ts`.
- **Tests** +9 (`m5-pages.test.tsx`) — 40/40; production build verified.

## Android

- **Completed** the M5 surface: real Research tab (articles / scholars /
  fatwas + fatwa search), `ResearchArticleFragment` (tier + citations),
  `ScholarDetailFragment`, `FatwaDetailFragment` (OD-08 disclaimer),
  8 nav destinations, `CourseRows`, 37 strings (+ Arabic core subset).
- **Fixed** non-compiling state from the mid-flight code: `NavHostFragment`
  import package, missing `ApiResponse`/`PUT` imports, missing strings /
  dimens (`item_two_line`), missing `tv_signin_hint`, dead `subtitle2()`
  call, invalid chapter `sortOrder` reference, `Outcome` generics.
- **Changed** versionName 0.3.0-m3 → 0.5.0-m5, versionCode 2 → 3.
- **Tests** +9 (`M5LearningTest`) — 49/49 JVM; debug/release/androidTest
  APKs built.

## Scripts / docs

- **Added** `scripts/m5-stack-harness.sh` (32 checks; real PostgreSQL +
  Flyway V1..V6 + admin-driven workflows + public/member verification), 
  `scripts/m5_update_tracker.py`, `docs-m5/` report set.
- **Updated** feature tracker → v1.4 (20 rows; AI-01..05 deliberately left
  for M6).
