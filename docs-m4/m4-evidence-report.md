# M4 Evidence Report (consolidated)

Milestone: M4 — Core Islamic Knowledge Modules · 2026-11-03
Every claim below traces to an executed command or generated artefact. Nothing
is claimed that was not run. **Environment note:** the sandbox experienced a
mid-session data-loss event; the affected artefacts were rebuilt from source
and re-verified end-to-end afterwards (final states below are the verified ones).

## 1. Backend (executed)

| Check | Result | Evidence |
|---|---|---|
| `mvn test` (JDK 21, Maven 3.9.16, Zonky embedded PostgreSQL) | **67/67 pass** (53 M1–M3 regression + 14 M4) | surefire reports; run output in session log |
| Controlled import: Quran (SR-01) | 114 surahs / 6,236 ayahs, sha256-verified against manifest, verbatim spot-check passes | `M4ContentImportIntegrityTest` |
| Controlled import: Bukhari (SR-08) | 7,563 hadith rows (ara/eng/urd merged), Arabic coverage ≥99%, grading provenance complete | same |
| Integrity service | report `passed=true` (18 checks) | same |
| Personal-library privacy | cross-user 404 on detail/patch/delete/token; admin platform list excludes personal rows; .txt upload rejected; quarantined media → UPLOAD-002 | `M4PersonalLibraryPrivacyTest` (5/5) |
| Fixture licensing gate | `allow-fixtures=false` hides every fixture row; fixture editions cannot exist as non-fixture | `fixtureGate` test |
| Production packaging | `ikp-backend-0.1.0-SNAPSHOT.jar` built | target/ |

## 2. Live-stack integration harness (executed — no mocks)

`scripts/m4-stack-harness.sh`: real PostgreSQL (Zonky binaries) + Spring Boot +
Flyway V1..V5 + admin-API-driven controlled imports + public endpoint checks
against the imported content. **19/19 PASS** → `docs-m4/evidence/m4-live-stack-harness.log`.
Import manifests (sha256 + counts) → `docs-m4/evidence/*.json`.

## 3. Android (executed)

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` (JDK 17 / AGP 8.5.2 / Gradle 8.9) | **40/40 pass** (M3 suite 28 + M4: EpubParserTest 3, M4UtilitiesTest 5 incl. upstream Adhan reference vectors, HijriCalendarTest 4 vs JDK Umm-al-Qura) |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL — `app-debug.apk` 8,613,026 B |
| `./gradlew :app:assembleRelease` (R8) | BUILD SUCCESSFUL — `app-release-unsigned.apk` 2,934,692 B |
| `./gradlew :app:assembleDebugAndroidTest` | BUILD SUCCESSFUL — compiled, **not executed** (no emulator/KVM in this environment; same honest limitation as M3) |
| Room migration 1→2 | forward-only migration code + registered `MIGRATIONS`; instrumentation coverage on device |

## 4. Admin CMS (executed)

- `npm test` (vitest): **31/31 pass** (incl. new ContentPage tests: integrity
  table, FIXTURE badge, daily workflow transitions).
- `npm run build` (tsc -b && vite build): production bundle OK.

## 5. Deliverables (download/)

- `ikp-android-v0.4.0-m4-debug.apk` / `ikp-android-v0.4.0-m4-release-unsigned.apk`
- `m4-core-knowledge-modules-v0.1.zip` (complete source incl. docs-m4)
- `14-feature-tracker-v1.3.xlsx`
- `docs-m4/` (this report set + evidence + manifests)

## 6. Honest status of M4 gate items

| Gate item | Status |
|---|---|
| 9 modules implemented, Android↔backend↔admin | **Yes** (harness + tests) |
| Quran/Hadith integrity checks | **Pass** (manifest-verified) |
| Licensing controls | **Pass** (fixture gate, LIC-001 publish guards, recitation publish hard-blocked) |
| Personal Library privacy | **Pass** (server-side ownership tests) |
| PDF/EPUB reader | **Implemented** (PdfRenderer + WebView themes/TOC/search); on-device rendering verified only by code-review + compiled instrumentation — no emulator here |
| Arabic/Urdu/Hindi rendering | Uthmani + Urdu hadith = real imported data; Hindi = labelled fixtures pending licensed datasets |
| Prayer & Qibla | Adhan vectors + bearings verified in JVM tests; sensor flow compiled, needs device run |
| Offline behaviour | Room caches (surahs/daily), Tasbih offline, stale-cache fallbacks — unit-tested at repository level |
| Recitation audio | **Blocked by rights (honest)** — metadata tables + UI disabled state only |
| Instrumentation / device tests | **Compiled, not executed** (no emulator) — checklist in m4-device-testing note |
