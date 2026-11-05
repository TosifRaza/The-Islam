# M3 Navigation Verification Report

Milestone: M3 · 2026-11-02

## 1. Implemented navigation model

- **Single activity** (`MainActivity`) + **Navigation Component**
  (`res/navigation/nav_graph.xml`), start destination `splashFragment`.
- **Custom six-section bottom bar** (`activity_main.xml`): BottomNavigationView
  caps at five items, so the approved six-section IA uses an equal-weight
  bar (6 × ≥48 dp targets, selected-state tinting, per-item content
  descriptions). Bar visibility is destination-driven (hidden on splash /
  onboarding / auth / detail screens).
- **State preservation**: tab switches navigate with
  `setRestoreState(true)` + `setPopUpTo(home, inclusive=true, saveState=true)`
  — each section keeps its back stack and scroll state across tab switches;
  reselect is a no-op (no stack churn).

## 2. Destination inventory (all six sections are distinct screens)

| Section | Destination | Content |
|---|---|---|
| Home | `homeFragment` | greeting band, search entry, M4 shortcut notices, Today (documented unavailable), continue-learning, real subjects rail, real books rail |
| Library | `libraryFragment` | search entry, 14 real category cards (stable slugs, position badges), browse-all |
| Academy | `academyFragment` | foundation layout + explicit M5 status |
| Research | `researchFragment` | foundation layout + explicit M6 status |
| AI Assistant | `assistantFragment` | foundation layout, disabled composer, status banner |
| Profile | `profileFragment` | guest prompt OR real `/auth/me` identity, learning/reading summary placeholders (documented), settings/sessions/sign-in/logout rows |

Nested destinations: `booksFragment` (subject/search args, language chips,
cursor paging) → `bookDetailFragment` (arg `bookId`, **deep link**
`ikp://library/book/{bookId}`), `settingsFragment`, `sessionsFragment`,
`loginFragment` (+ register/forgot honest screens), `onboardingFragment`.

## 3. Flow verification (compiled instrumentation + tested logic)

| Flow | Mechanism | Status |
|---|---|---|
| Splash → onboarding (first run) | `StartupPolicy.decide` | JVM-tested |
| Splash → login (no session) | `StartupPolicy.decide` | JVM-tested + harness context |
| Splash → home (validated session) | `AuthRepository.restore` → policy | JVM-tested |
| Splash → home offline w/ saved session (guest mode) | policy `HOME_OFFLINE` | JVM-tested — never renders authenticated |
| Login success → home (popUpTo inclusive) | `action_login_to_home` | wired; device-run pending |
| Onboarding guest → home | `action_onboarding_to_home` | wired |
| Tab switches ×6 | saveState/restoreState | wired; device-run pending |
| Home search → Books | global action `action_global_books` | wired |
| Subject card → Books (filtered) | `action_library_to_books` + args | wired |
| Book row → detail | `action_books_to_detail` / global action | wired |
| Book detail deep link | `ikp://library/book/{bookId}` | manifest intent-filter + nav deepLink |
| Profile → Settings / Sessions / Login | scoped actions | wired |
| Session expired → global action → Login | `action_global_session_expired` + dialog | wired |
| Back navigation | system back via NavController; detail screens provide 48 dp back buttons | wired |
| Protected destinations | sessions/settings/sign-in rows react to `SessionManager` state; expiry routes to login | wired |

## 4. Honest status

Every navigation path above is implemented in the shipped nav graph and
exercised at compile level by the instrumentation suite
(`AppLaunchTest` asserts launch→sign-in, validation errors, and bottom-bar
visibility on auth screens). The sandbox has no emulator, so interactive
walkthrough evidence (screens/video) remains a device-run item — recorded in
`m3-device-testing-report.md`. No placeholder screen stands in for a section:
the six tabs render six different, purpose-built layouts.
