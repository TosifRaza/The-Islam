# IKP Android — M3 Native Application Foundation (v0.3.0-m3)

Native Android application foundation (Java + XML, MVVM — per approved
blueprint C-01 and the M3 master prompt). Built ON the M1 skeleton; no
replacement project was created; the pinned toolchain is unchanged.

## 1. What is in place (M3)

- **Architecture**: single-activity (`MainActivity`) + Navigation Component;
  MVVM (ViewModel + LiveData); Repository pattern; manual DI
  (`di/ServiceLocator` — see the decision note in §6).
- **Design system** (M3.2): approved palette (Deep Emerald `#164638`,
  Islamic Gold `#C7A75A`, Warm Ivory `#F7F6F0`, White) as XML tokens;
  Material 3 DayNight themes with a full **dark theme**; typography scale;
  spacing/dimension tokens (48 dp touch targets); reusable components
  (`StateView`, cards, buttons, inputs, custom six-section bottom bar).
- **Startup & onboarding** (M3.3): SplashScreen API → `SplashFragment` with
  the pure, unit-tested `StartupPolicy` decision (onboarding → login → home,
  incl. the offline-saved-session path); 4-page onboarding (3 feature pages +
  **permission-introduction page**); guest-browse entry.
- **Six primary sections** (M3.5): Home / Library / Academy / Research /
  AI Assistant / Profile — real bottom navigation with per-tab state
  preservation, protected actions, deep-link foundation
  (`ikp://library/book/{bookId}`).
- **Authentication** (M3.4/§3.7): login against the real backend
  (`POST /api/v1/auth/login`), single-flight rotating token refresh
  (OkHttp `Authenticator`), logout with revocation, session-expiry handling,
  **Android Keystore-backed token storage**
  (`EncryptedSharedPreferences`, AES-256-GCM master key). Passwords are
  never stored; tokens are never logged.
- **Home**: real subjects + books rails (backend APIs); documented
  unavailable states for daily-content modules (M4) — **no fabricated
  religious content**.
- **Library**: real categories (14 public; position 06 stays server-hidden),
  search (debounced), language filters, cursor-paged book lists, book detail,
  recently-viewed (local Room history).
- **Profile & settings** (M3.6): real `/auth/me` profile, devices & sessions
  (list/revoke via `/auth/sessions`), working appearance switch
  (light/dark/system), working in-app language switch (EN/AR; final list
  gated by OD-02), honest states for capabilities the backend has not
  published (profile editing, data export, account deletion).
- **Room foundation** (M3.8): v1 database (`ikp.db`, 11 tables) with a
  documented forward-only migration policy; profile/category/book caches with
  TTLs; user-data foundations (progress, bookmarks, notes, downloads,
  preferences, history); pending-sync queue + sync log.
- **WorkManager foundation** (M3.9): network-constrained, exponential-backoff
  `PendingSyncWorker` with idempotent op keys and a handler registry
  (handlers register in M7 — foundation only).
- **States** (M3.10): loading/empty/error(+stable error code)/offline banner —
  every significant screen; no screen stays blank after a failure.
- **Multilingual/RTL** (M3.11): all UI strings in resources; `values-ar` core
  translations + `values-hi`/`values-ur` rendering-proof subsets (OD-02 gates
  the final list); `supportsRtl` + start/end-relative layouts; per-app locale
  switching (AppCompat) with auto-store.
- **Accessibility**: 48 dp targets, content descriptions, TalkBack labels,
  text-scaling-friendly `sp`, accessible contrast in both themes.
- **Testing** (M3.12): 28 JVM unit tests (startup policy, error mapping,
  mappers + category-position invariant, envelope parsing, token-refresh
  single-flight & rotation, sync scheduling, validation); instrumentation
  suite (launch flow, sign-in validation, Room DAO incl. position-ordering +
  sync idempotency); API-contract integration harness (§5).

## 2. Project structure

```
app/src/main/java/com/ikp/dev/app/
├── IkpApplication.java          # graph init + persisted theme
├── MainActivity.java            # single activity, bottom bar, offline banner
├── VersionInfo.java             # (M1) support metadata
├── core/
│   ├── common/                  # UiState, Outcome, StartupPolicy, NetworkMonitor, LocalePrefs
│   ├── database/IkpDatabase.java
│   ├── error/                   # ApiError + ErrorMapper (problem+json, stable codes)
│   ├── navigation/              # (nav graph lives in res/navigation)
│   ├── network/                 # ApiClient, AuthInterceptor, TokenAuthenticator, ApiResponse
│   ├── security/                # TokenStore, EncryptedTokenStore, SessionManager
│   └── workers/                 # SyncScheduler, PendingSyncWorker
├── data/
│   ├── local/{entity,dao}/      # Room entities + DAOs
│   ├── mapper/Mappers.java      # DTO → domain (null-tolerant)
│   ├── remote/                  # AuthApi, LibraryApi + dto/
│   └── repository/              # Auth, Subject (TTL cache), Book (cursor-paged cache)
├── domain/model/Models.java     # User, Subject, Book, BookDetail, SessionInfo, Page
├── ui/
│   ├── splash/ onboarding/ auth/
│   ├── home/ library/ academy/ research/ assistant/
│   ├── profile/                 # Profile, Settings, Sessions (+ adapters/VMs)
│   └── common/                  # StateView, MessageKeys
└── di/ServiceLocator.java       # manual DI graph
```

## 3. Toolchain (unchanged, pinned)

- AGP 8.5.2 ↔ Gradle 8.9 (committed wrapper) ↔ **JDK 17** ↔ compileSdk 34,
  minSdk 24, targetSdk 34, `versionName 0.3.0-m3`.
- Provisioning (as used in this milestone's evidence runs):

```bash
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

- Build + test (all executed successfully for M3 evidence):

```bash
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest
# BUILD SUCCESSFUL — outputs:
#   app/build/outputs/apk/debug/app-debug.apk               (8.4 MB, versionCode 2)
#   app/build/outputs/apk/release/app-release-unsigned.apk  (2.8 MB, R8-minified)
#   app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# JVM tests: 28 tests, 0 failures, 0 errors (JUnit XML under app/build/test-results)
```

- SDK-free JVM fallback (no Android SDK; curated pure classes): `make android-test`.

## 4. Backend connection (prompt §18 — emulator vs device)

- `BASE_URL` comes from `BuildConfig` (never a hardcoded production endpoint):
  - **debug default**: `http://10.0.2.2:8080/` — the emulator's alias for the
    host machine's localhost. **A physical device must NEVER use 10.0.2.2**;
    use `IKP_BASE_URL` (see below) or adb-reverse.
  - **release**: injected via `-PIKP_BASE_URL=https://<staging-or-prod>/`
    (cleartext is disabled in release; HTTPS required).
- Physical device quick path: `adb reverse tcp:8080 tcp:8080` then
  `./gradlew -PIKP_BASE_URL=http://127.0.0.1:8080/ assembleDebug`.
- No production backend credentials exist anywhere in this repository.

## 5. API-contract integration harness (real backend evidence)

`scripts/m3-api-contract/` compiles the app's **own** `AuthApi`/`LibraryApi`
interfaces, DTOs and mappers and runs them against a live stack
(PostgreSQL + Spring Boot via `make compose-up` or a local run):

```bash
bash scripts/m3-api-contract/run-m3-integration.sh
# 29 checks — login errors, login, /me, sessions, refresh rotation +
# rotation-replay rejection, subjects (14, position-6 absent), books +
# search, 404 NF-001, anonymous rejection AUTH-002, logout revocation.
# Evidence: docs-m3/evidence/api-contract-harness.log
```

## 6. Architecture decision — manual DI

`ServiceLocator` is deliberate: the approved stack is Java-only, and
annotation-processor DI (Hilt) requires the Kotlin toolchain (kapt/ksp) that
the stack excludes. The graph is small, explicit and testable
(`resetForTests()`). Revisit only via an approved stack change.

## 7. Package-name note (OD-01, gate G-1)

`applicationId com.ikp.dev.app` remains a **provisional development id**
(inside `defaultConfig`, clearly labelled). The final applicationId/branding
is frozen only at the branding & package-name freeze before Google Play
submission. Never publish a build using the provisional id.

## 8. Honest capability map (nothing pretends to work)

| Capability | State |
|---|---|
| Login, session restore, refresh rotation, logout, sessions list/revoke | ✅ real backend |
| Profile display (name/email/status/roles/member-since) | ✅ real backend |
| Appearance (light/dark/system) + language switch (EN/AR) | ✅ working locally |
| Library categories, book browse/search/detail | ✅ real backend |
| Book download | ⚠️ blocked: public API does not expose file UUIDs required by `/download-token` (documented in `docs-m3/m3-api-integration-report.md`) |
| Registration / email verification / forgot-reset password | 🚫 no backend endpoints → documented unavailable screens |
| Profile self-update, data export, account deletion | 🚫 no backend endpoints → honest states |
| Ayah/Hadith of Day, Hijri, prayer times, Qibla, Tasbih | 🚫 M4 scope → honest unavailable states |
| Academy courses | 🚫 M5 scope → foundation layout |
| Research + AI assistant | 🚫 M6 scope → foundation layout, composer disabled, no AI provider connected |
| Offline sync payloads | 🚫 M7 scope → WorkManager foundation only |
