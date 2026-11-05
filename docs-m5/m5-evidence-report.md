# M5 Evidence Report (consolidated)

Milestone: M5 — Learning & Research Modules · 2026-11-03
Every claim below traces to an executed command or generated artefact. Nothing
is claimed that was not run. The M4→M5 gate was authorised by the owner's
instruction to proceed with M5 (baseline inspection + pre-existing test run
completed first, per the authorisation conditions).

## 0. Baseline inspection (condition 1–2, executed before any modification)

- M1–M4 source inspected (backend 29 controllers / 15 entities / migrations
  V1..V5; admin routes + API client; Android nav graph, Retrofit, Room v2).
- Pre-existing test state: backend **91/91 pass**, admin **40/40 pass**,
  Android JVM **49/49 pass** at inspection time (M4 suites + the M5 code the
  previous session had left mid-flight: backend/admin largely present,
  Android partial and non-compiling). **Zero pre-existing test failures.**
- Findings that drove this milestone's work: Android M5 was not compilable
  (missing imports/resources/nav destinations), M5 layouts referenced missing
  strings, `ResearchFragment` was still the M3 placeholder, scholars/fatwa
  screens absent, no docs-m5, no M5 evidence.

## 1. Backend (executed)

| Check | Result | Evidence |
|---|---|---|
| `mvn test` (JDK 21, Maven 3.9.16, Zonky embedded PostgreSQL) | **92/92 pass** (67 M1–M4 regression + 24 prior M5 + 1 new regression test) | surefire reports; `/tmp/m5-backend-final.log` |
| Academy lifecycle | draft→review→approve (reviewer ≠ creator)→publish; publish-before-approval 409 WORKFLOW-001; self-approval denied; draft-only editing | `M5AcademyIntegrationTest` |
| Enrolment/progress ownership | per-user progress; anonymous enrol 401; continue-rail excludes completed | same |
| Assessment integrity | pre-submission payload carries **no** correct flag / explanation; grading server-side; explanations only after submit; wrong option → 0; owner-only result access | `M5AssessmentSecurityIntegrationTest` |
| **Regression (new)** | attempt payload is answerable (options included, id+text only) and the real correct option grades **100** — locks the fix for the defect the harness found (see §2) | same, `attemptPayloadIsAnswerableAndCorrectOptionGradesFullScore` |
| Versioned question bank | edit bumps version + invalidates approval; submit-review + decision with reviewer ≠ author; publish gated on approved questions | same |
| Flashcard separation | admin decks via publish state machine; user decks private, stranger 404; lists owner-scoped | same |
| Research citations | `quran_ayah` citations auto-verified against the imported Quran; fabricated surah 999 → VAL-001; three-tier classification enforced | `M5ResearchScholarsIntegrationTest` |
| Scholar verification | verify/revoke audited; public directory serves verified profiles | same |
| Fatwa governance (OD-08) | assign-reviewer → scholarly decision (scholar_reviewer only) → publish blocked for the reviewer; published-only public directory | `FatwaSecurityIntegrationTest` (M2) + `M5ResearchScholarsIntegrationTest` + harness |
| Production packaging | `ikp-backend-0.1.0-SNAPSHOT.jar` rebuilt | target/ |

## 2. Live-stack integration harness (executed — no mocks)

`scripts/m5-stack-harness.sh`: real PostgreSQL (pgbin binaries) + Spring Boot +
Flyway **V1..V6** + admin-API-driven workflows with six distinct synthetic
role accounts + public/member endpoint verification. **32/32 PASS** →
`docs-m5/evidence/m5-live-stack-harness.log`.

The harness found **one real defect** the unit suite had missed: the
pre-submission attempt payload carried no options, so members could not
answer, and submitting a real option id crashed grading (HTTP 500 NPE in
`AssessmentService.submitAttempt`). Fixed (`PublicOption` id+text only,
no correctness data), regression test added (backend now 92), harness re-run
to 32/32.

## 3. Android (executed)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` (JDK 17 / AGP 8.5.2 / Gradle 8.9) | **49/49 pass** (40 M3/M4 regression + 9 new `M5LearningTest`: row mapping, Gson contract incl. `media_url` alternate, pre-submission DTO leak-invariant, submit-body shape) |
| `:app:assembleDebug` | BUILD SUCCESSFUL — `app-debug.apk` 8,705,336 B (v0.5.0-m5, versionCode 3) |
| `:app:assembleRelease` (R8) | BUILD SUCCESSFUL — `app-release-unsigned.apk` 2,982,629 B |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL — compiled, **not executed** (no emulator/KVM here; same honest limitation as M3/M4) |
| Completed in this milestone | real `ResearchFragment` (articles/scholars/fatwas modes + fatwa search), `ResearchArticleFragment` (tier + citations), `ScholarDetailFragment`, `FatwaDetailFragment` (OD-08 disclaimer), 8 nav destinations, `CourseRows` util, 37 strings + Arabic core subset, Room-untouched (M5 repositories are pass-through; offline sync = Phase 14/M7 per roadmap), version bump |
| Fixed from previous session | `NavHostFragment`/`ApiResponse`/`PUT` imports, `CourseRows` + `tv_signin_hint` missing resources, dead `subtitle2()` call, chapter sortOrder reference, `Outcome` generics |

## 4. Admin CMS (executed)

- `npm test` (vitest): **40/40 pass** (31 M2–M4 + 9 M5 pages tests:
  academy, assessments, research, scholars).
- `npm run build` (tsc -b && vite build): production bundle OK.
- M5 surface verified present: nav entries Academy/Assessments/Research/
  Scholars (`AdminLayout.NAV`), routes with permission guards in `App.tsx`,
  service modules `api/academy.ts` + `api/research.ts`.

## 5. Deliverables (download/)

- `ikp-android-v0.5.0-m5-debug.apk` / `ikp-android-v0.5.0-m5-release-unsigned.apk`
- `m5-learning-research-v0.1.zip` (complete source incl. docs-m5)
- `14-feature-tracker-v1.4.xlsx` (20 M5 rows updated; 9 sheets preserved)
- `docs-m5/` (this report set + harness log)

## 6. Honest status of M5 items

| Item | Status |
|---|---|
| Academy (Module 10) | **Live** — catalogue/enrol/progress/lessons; quiz + flashcard lessons; pdf/video open material references. Certificates excluded (OD-11 not approved) |
| Assessment (Module 13) | **Live** — versioned reviewed questions, server-side grading, weak topics; timed quizzes |
| Flashcards | **Live** — admin published decks + private user decks strictly separated |
| Research (Module 11) | **Live** — topics, tiered articles, verified citations, save/notes; related-resources panel deferred to M6 citation graph |
| Scholars (Module 14) | **Live** — profiles + audited verification; photo upload deferred |
| Fatwa directory (Module 14) | **Live** — published-only, scholar attribution, OD-08 separation enforced in the harness |
| Review workflows | **Live** — academy/quiz/research state machines + versioned question review, all audited |
| Android integration | Compiles + JVM-tested; full device run still pending (no emulator here — device checklist in m5-device-testing note) |
| Offline behaviour | M5 content is online-first by design; offline/synchronisation is Phase 14 (M7) per the approved roadmap — documented, not silently missing |
| M6 | **Not started** — this delivery stops at the M5 gate per the owner's condition 6 |
