# M5 API Integration Report

Milestone: M5 — Learning & Research Modules · 2026-11-03
Conventions unchanged from M2/M4: `/api/v1`, `{"data": …, "meta": …}` envelope,
RFC 7807 problem+json with stable codes, JWT bearer, method-security
permission codes, envelope pagination (1-based `page`).

## Public (guest-browsable GETs, whitelisted in SecurityConfig)

| Endpoint | Purpose |
|---|---|
| `GET /academy/courses?page&size` | published course catalogue (title/subject/level/lesson count) |
| `GET /academy/courses/{id}` | published course detail incl. chapters → lessons |
| `GET /assessments/quizzes?page&size` / `GET /assessments/quizzes/{id}` | published quiz catalogue/detail — **no answers, no explanations** |
| `GET /flashcards/decks` / `GET /flashcards/decks/{id}` | published admin decks |
| `GET /research/topics` · `GET /research/articles?topic&page` · `GET /research/articles/{id}` | topic taxonomy; published articles; detail with citations + `sourceTier` |
| `GET /scholars?page&size` / `GET /scholars/{id}` | scholar directory (verified profiles) |
| `GET /fatwas?q&category&page` / `GET /fatwas/{id}` | published-only fatwa directory with reviewing scholar + review date |
| `GET /search?q&groups` | grouped search now serves live `courses`, `lessons`, `research`, `scholars`, `fatwas` (M4 placeholders removed); private uploads never indexed |

## Member (authenticated)

| Endpoint | Notes |
|---|---|
| `POST /academy/courses/{id}/enrol` | idempotent per user; 401 anonymous |
| `POST /academy/lessons/{id}/complete` | recomputes course progress; `status` completed at 100% |
| `GET /academy/courses/{id}/progress` | owner-scoped |
| `GET /academy/continue?limit` | active enrolments with next-lesson pointers |
| `POST /assessments/quizzes/{id}/attempts` | freezes presented question set; payload = questions + options (**id + text only** — no correct flag / explanation) |
| `POST /assessments/attempts/{id}/submit` | server-side grading; time limit + grace → expired; result carries `correctOptionId` + `explanation` |
| `GET /assessments/attempts?quizId&page` · `GET /assessments/attempts/{id}` | owner-scoped history / graded result |
| `GET /assessments/weak-topics` | wrong-percentage per subject from submitted attempts |
| `GET /flashcards/my/decks` · `POST /flashcards/my/decks` | private decks — strictly separated from admin decks (owner-scoped reads) |
| `PUT /user-content/bookmarks` / `PUT /user-content/notes` | refKeys now include `course:`, `lesson:`, `research:`, `fatwa:`, `scholar:` |

## Admin (permission-gated, audited)

| Endpoint group | Permission | Notes |
|---|---|---|
| `/admin/academy/courses|chapters|lessons` CRUD + `/{id}/{action}` | `academy.manage` | actions: submit-review, approve-editorial (reviewer ≠ creator), publish, unpublish/archive |
| `/admin/assessments/quizzes` CRUD + actions; `/questions` create/edit; `/questions/{id}/submit-review|decision|versions`; `/flashcards/decks` CRUD | `assessment.manage` + roles | question edit bumps version and invalidates approval; decision requires editorial role and ≠ author; publish gated on approved questions |
| `/admin/research/articles` CRUD + `/{id}/{action}`; `/topics` | `content.edit` / scholarly role | actions: submit-review, approve-editorial, approve (scholarly), reject, publish; quran_ayah citations verified against the imported Quran (VAL-001 on failure) |
| `/admin/scholars` CRUD + `/{id}/verify` | `scholars.manage` | verification recorded to audit log |

## Cross-tier contract notes

- The pre-submission payload change (options added, correctness still absent)
  is consumed by the Android `QuestionPublicDto` (already modelled) — the
  client never gains grading data.
- The harness verified the full chain end-to-end with six distinct synthetic
  role accounts (`docs-m5/evidence/m5-live-stack-harness.log`).

## OpenAPI

M5 modules register under the existing springdoc setup (`/swagger-ui`); groups
unchanged, controllers annotated `@Tag`/`@Operation` per house style.
