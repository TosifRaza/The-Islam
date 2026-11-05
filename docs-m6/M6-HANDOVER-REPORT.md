# M6 — PROJECT HANDOVER AND VERIFICATION REPORT

**Project:** Islamic Knowledge & Learning Platform
**Report date:** 2026-11-04 (UTC)
**Workspace inspected:** `/home/z/my-project/`
**Verification mode:** read-only inspection + test execution. No source file was modified, deleted, reset or recreated. Pre-existing state was preserved exactly; all findings below are backed by executed commands whose logs are archived in `docs-m6/evidence/`.

---

## 1. Executive summary

- The platform code in this workspace is the **M1–M5 reconstructed lineage** (`platform-dev/`: Spring Boot backend, native Android app, React admin). It is real and largely intact, but it is **not committed to git** (details §3).
- **The M6 source code (AI Islamic Research Assistant) is not present in this workspace.** A previous session did implement and run an M6 backend on 2026-11-03 — hard evidence survives in the `islamic_ai` PostgreSQL database (9 applied migrations, 3 golden-evaluation runs of **20/20 passed**, 153/153 citations verified, kill-switch audit trail, OD-07 generation/embedding flags OFF) and in two surviving helper scripts (`scripts/smoke-m6.sh`, `scripts/ResetDb.java`). The M6 backend jar, the M6 admin screens, the M6 Android AI screens, `docs-m6/` and the M6 evidence packages were **lost during sandbox recycling**. Only the database and those two scripts survived.
- Verified today, from source, in this environment:
  - **Android**: `:app:assembleDebug` **BUILD SUCCESSFUL** (resumed without `clean`), APK regenerated (8,705,320 bytes). `:app:testDebugUnitTest` **49/49 passed**.
  - **Admin**: `vitest` **40/40 passed (7 files)**; production build (`tsc -b && vite build`) **SUCCESSFUL**.
  - **Backend**: `mvn test` → **91 tests: 7 passed, 84 errors**. Root cause established: a stale M1-era `GlobalExceptionHandler` (`app.ikp.backend.web`) collides with the M2 RFC-7807 handler (`app.ikp.backend.common`) on the same bean name, so no Spring context can start. Only `FlywayMigrationTest` (7/7, no Spring context) passes. **This is a pre-existing defect; it was documented, not fixed** (preserve mandate).
- The historical claims “41/41 backend tests”, “20/20 golden evaluation”, “4/4 admin tests” belong to the lost M6 session. **20/20 is corroborated by surviving database records** (3 runs, all `COMPLETED`, `pass_rate=1.0000`, explicitly noted as *synthetic technical fixtures only*). The 41/41 and 4/4 figures **cannot be reproduced or corroborated from anything in this workspace** — the code they tested no longer exists.
- **Statuses (end of report):** *M6 Technical Foundation: Incomplete.* *Production AI: Blocked.*

## 2. Environment status

All tools live under `/home/z/my-project/tools/` (sandbox has no global toolchain). Verified by execution, not by setup scripts:

| Tool | Version | Path / notes |
|---|---|---|
| Java (primary, fulljdk) | OpenJDK **21.0.13** | `/home/z/my-project/tools/fulljdk` — `javac 21.0.13` present; **`jlink` NOT present** |
| Java (secondary, jdk17) | JDK **17.0.21** | `/home/z/my-project/tools/jdk17` — **`jlink` present** → required for Android builds |
| Maven | **3.9.16** | `/home/z/my-project/tools/maven` — **stock `bin/mvn` wrapper is corrupted** (missing classpath separator, `ClassNotFoundException: plexus...launcher`); a corrected launcher is at `/home/z/my-project/scripts/mvn.sh` |
| Node.js | **24.22.2** | system `node` |
| npm | **11.19.1** | system `npm` |
| Gradle | **8.9** | local distribution `/home/z/my-project/tools/gradle-8.9`; project wrapper also pins 8.9 |
| Android SDK | platforms: **android-34**; build-tools: **34.0.0**; platform-tools present | `/home/z/my-project/tools/android-sdk`; `adb devices` → **no device/emulator attached** |
| Android Gradle Plugin | **8.5.2** | `android/build.gradle` |
| PostgreSQL | **16.4** | `/home/z/my-project/tools/pgbin` (initdb/pg_ctl/postgres; **no `psql`** — JDBC used instead); data dir `/home/z/my-project/tools/pgdata`, port **54329** |
| pgvector | **not installed and not available** (`pg_available_extensions` has no `vector`) | embeddings were stored as JSONB (§9) |

Environment notes:
- Maven Central rate-limits this sandbox IP (HTTP 429). Dependency resolution succeeded via mirrors: `scripts/settings-mirror.xml` (Maven → Aliyun) and `tools/sandbox-mirror.init.gradle` (Gradle → Aliyun). Both are sandbox workarounds, **not** part of the deliverable.
- `~/.m2` and Gradle caches were **wiped by the recycle** and were re-downloaded during this verification.
- **Operational caveat:** background (`&`) processes are reaped when a tool call ends in this sandbox (observed for maven/gradle/npm/node). Only properly daemonised processes (PostgreSQL via `pg_ctl`) survive. Long builds must run in foreground sessions.
- The `tools/pgdata` cluster needed repair before it would start (recycle side-effects): `chmod 700 pgdata` + recreation of standard **empty** runtime directories (`pg_notify`, `pg_tblspc`, `pg_replslot`, `pg_twophase`, `pg_commit_ts`, `pg_snapshots`, `pg_stat_tmp`, `pg_wal/archive_status`, `pg_logical/{mappings,snapshots}`). **No data files were touched**; the server started cleanly afterwards (restart demonstrated).

## 3. Repository and Git status

- **Workspace path:** `/home/z/my-project/`
- **Repository root:** `/home/z/my-project` (the only git repo). `platform-dev/` and `islamic-knowledge-platform/` have **no `.git` of their own and are untracked**.
- **Branch:** `main`
- **Latest commit:** `b109aae756a920c0b91eed0ed153103caca8a822` — “Initial commit” — 2026-11-02 15:34:21 +0000. It contains exactly **4 files**: `.env`, `.gitignore`, `download/README.md`, `upload/Pasted Content_1793598747138.txt`.
- **Modified files:** `.env`, `.gitignore` — file-mode changes only (644→755), zero content change.
- **Untracked files:** all deliverables and both project trees (66+ top-level untracked entries; full snapshot in `docs-m6/evidence/git-status-snapshot.txt`).
- **Git commit history of the platform code: NOT preserved.** The former M0–M4 repository and its history were lost during sandbox recycling (documented honestly here; not recreated).
- **Repository structure** (generated dirs excluded):

```
/home/z/my-project/
├── .env, .gitignore, README.md
├── download/                        ← all user-facing deliverables
│   ├── platform-dev/                ← THE platform (M1–M5 lineage)
│   │   ├── backend/   (pom.xml, src/main, src/test, Dockerfile, db/)
│   │   ├── admin/     (Vite+React 18+TS, src/features ×14, src/test ×7)
│   │   ├── android/   (Gradle :app, test-lib [junit+hamcrest jars])
│   │   ├── docs-m1/ docs-m2/ docs-m3/ docs-m4/ docs-m5/
│   │   ├── docs-m6/   ← created by THIS report only (see §10)
│   │   ├── scripts/, .github/, Makefile, docker-compose.yml,
│   │   ├── README.md, THIRDPARTY-NOTICES.md, .env.example, .gitignore (empty)
│   ├── islamic-knowledge-platform/  ← M0 blueprint package (docs/ ×17 + BASELINE-APPROVAL.md)
│   ├── 14-feature-tracker-v0.2…v1.4.xlsx
│   ├── ikp-android-v0.3.0-m3-*.apk (×2), v0.4.0-m4-*.apk (×3), v0.5.0-m5-*.apk (×2)
│   ├── m1…m5 milestone ZIPs, blueprint/baseline ZIPs, README.md, THIRDPARTY-NOTICES.md
├── imports/          (hadith/, tanzil/, manifests/ — licensed/reference import material)
├── scripts/          (session helpers incl. smoke-m6.sh, ResetDb.java, InspectDb*.java)
├── tool-results/, skills/, tools/, upload/
```

- **All three projects exist:** Backend ✓, Android ✓, Admin ✓ (M1–M5 lineage). A *separate* M6-era backend lineage (artifact `islamic-platform-backend-0.6.0-M6`, referenced by the surviving `scripts/smoke-m6.sh`) has **no surviving source anywhere** (searched `/home/z/my-project`, `/tmp` copy, caches).

## 4. Android build status

| Item | Value |
|---|---|
| Project root | `/home/z/my-project/download/platform-dev/android` |
| Gradle modules | **`:app` only** (`settings.gradle`: rootProject `IKP-Android`, `include(":app")`) — module name verified before building, per instruction |
| Application ID | `com.ikp.dev.app` (**OD-01 placeholder — frozen branding comes at gate G-7**) |
| versionName / versionCode | `0.5.0-m5` / 3 |
| compileSdk / minSdk / targetSdk | **34 / 24 / 34** |
| Java compatibility | source/target **17** |
| Gradle wrapper | **8.9** (`gradle-wrapper.properties`) |
| AGP | **8.5.2** |
| Previous Gradle build running? | **No** — no gradle/java build processes found (`ps` audit) |
| In-tree build output before this run | **None** — `android/app/build/` was empty (recycle casualty) |

**APKs found in the workspace (all preserved, none deleted):**

| Path (under `/home/z/my-project/download/`) | Bytes | Last modified (UTC) | Variant |
|---|---|---|---|
| `ikp-android-v0.3.0-m3-debug.apk` | 8,389,722 | 2026-11-02 20:45 | debug |
| `ikp-android-v0.3.0-m3-release-unsigned.apk` | 2,798,076 | 2026-11-02 20:45 | release-unsigned |
| `ikp-android-v0.4.0-m4-debug.apk` | 8,613,026 | 2026-11-02 21:38 | debug |
| `ikp-android-v0.4.0-m4-release-unsigned.apk` | 2,934,692 | 2026-11-02 21:38 | release-unsigned |
| `ikp-android-v0.4.0-m4-androidTest.apk` | 3,853,685 | 2026-11-02 21:39 | androidTest |
| `ikp-android-v0.5.0-m5-debug.apk` | 8,705,336 | 2026-11-03 10:25 | debug |
| `ikp-android-v0.5.0-m5-release-unsigned.apk` | 2,982,629 | 2026-11-03 10:25 | release-unsigned |

No M6 APK exists anywhere. There is **no `app`-named module confusion** — the correct module-specific command was used:

```bash
JAVA_HOME=…/tools/jdk17 ANDROID_HOME=…/tools/android-sdk \
  tools/gradle-8.9/bin/gradle --no-daemon --console=plain \
  --init-script tools/sandbox-mirror.init.gradle :app:assembleDebug
```

**Result: `BUILD SUCCESSFUL in 1m 36s`** (37 tasks: 15 executed, 22 up-to-date — **no `clean` was run**). New APK generated:

- `android/app/build/outputs/apk/debug/app-debug.apk` — **8,705,320 bytes**, 2026-11-04 06:45 UTC, debug variant (matches the surviving M5 deliverable byte-for-byte within zip timestamps).

First attempt with `JAVA_HOME=tools/fulljdk` failed: `JdkImageTransform … jlink executable …/fulljdk/bin/jlink does not exist`. **Android builds in this environment must use `JAVA_HOME=tools/jdk17`** (the only JDK with `jlink`). Log: `docs-m6/evidence/m6-android-build.log`.

## 5. Android unit-test results

Command (correct module, without destroying build outputs):

```bash
:app:testDebugUnitTest   # executed after assembleDebug, same JVM/SDK settings
```

**Result: `BUILD SUCCESSFUL`; 49 tests, 49 passed, 0 failed, 0 skipped** (8 test classes, incl. `AuthValidationTest`, `EpubParserTest`, `M5LearningTest`). Aggregated from JUnit XML in `android/app/build/test-results/testDebugUnitTest/`; HTML report at `android/app/build/reports/tests/testDebugUnitTest/`. Log: `docs-m6/evidence/m6-android-test.log`.

**Instrumented tests:** sources exist (`app/src/androidTest/.../RoomDaoTest.java`, `AppLaunchTest.java`) and the `connectedDebugAndroidTest` task is available, but **no emulator or physical device is attached** (`adb devices` → empty). **No instrumented test was executed in this environment and none is claimed to have passed.** The only surviving instrumented artefact is the M4-era `ikp-android-v0.4.0-m4-androidTest.apk` listed above.

## 6. Android implementation audit

Source root: `android/app/src/main/java/com/ikp/dev/app/`. Structure: MVVM (`ui/*` fragments + `*ViewModel`), `data/remote` (Retrofit), `data/local` (Room), `core/*` (network/security/error/workers/database), `di/`.

| Requested feature | Status | Evidence (actual files) |
|---|---|---|
| Login screen | **Implemented** | `ui/auth/LoginFragment.java`, `AuthViewModel.java`, `UnavailableFragment.java`; nav destinations in `res/navigation/nav_graph.xml` |
| Conversation list | **ABSENT** | no conversation UI or DAO anywhere (M6 source lost) |
| AI chat screen | **ABSENT (placeholder only)** | `ui/assistant/AssistantFragment.java` — the M3 foundation: composer disabled in layout, status banner, *“The approved RAG subsystem lands in M6 … NO connection to any AI provider”* |
| Citation chips | **ABSENT** | no citation UI/component in the tree |
| Source detail sheet | **ABSENT** | no source-detail UI/component in the tree |
| MVVM structure | **Implemented** | 37 fragments, 15 ViewModels, `data/repository/*`, `domain/model/*` |
| Retrofit integration | **Implemented** | `core/network/ApiClient.java`, `ApiResponse.java`, 4 API interfaces (`AuthApi`, `ContentApi`, `LibraryApi`, `AcademyApi`) + `data/remote/dto/*` |
| Room integration | **Implemented** | `core/database/IkpDatabase.java`; DAOs: `ContentDao`, `LibraryDao`, `SyncDao`, `UserDataDao`, `UserProfileDao`; entities: `CacheEntities`, `ContentCacheEntities`, `SyncEntities`, `UserDataEntities` — **no chat/conversation entities** |
| Authentication handling | **Implemented** | `core/network/AuthInterceptor.java` + `TokenAuthenticator.java` (single-flight 401 refresh, single-use rotating refresh tokens, session expiry on refresh failure) |
| Error states | **Implemented** | `ui/common/StateView.java` + dedicated error/empty/loading layouts; four-state UI policy from M3 |
| Loading states | **Implemented** | same (`StateView`) |
| Empty states | **Implemented** | same (`StateView`, per-screen empty layouts) |
| Network failure behaviour | **Implemented (offline/error surfaces)** | authenticator expiry path + StateView error states; no silent failures found |
| Session/token handling | **Implemented** | `core/security/TokenStore.java`, `EncryptedTokenStore.java`, `SessionManager.java` |

**Incomplete / unverified:** the entire AI assistant surface (conversation list, chat, citations, source sheet) exists only as the honest M3 placeholder; M5 “Partial (M5)” tracker rows (ACA-04 revision lists, ACA-05 certificates—OD-11, RES-03 related-resources) remain partial; instrumented tests unexecuted here (§5).

## 7. Backend implementation and test results

**Stack (from `backend/pom.xml`, verified):** `app.ikp:ikp-backend:0.1.0-SNAPSHOT` · Spring Boot **3.5.6** · Java **21** · Spring Security + JPA/Hibernate (`ddl-auto: none` — Flyway is the sole schema authority) + Flyway Community (locations `db/migration/identity` + `db/migration/content`, forward-only, validate-on-migrate) + OAuth2 resource server (JWT) + Argon2id via BouncyCastle 1.78.1 + springdoc 2.8.9 · tests use **Zonky embedded-postgres 2.1.1** (real PostgreSQL, no Docker).

**Configuration** (`application.yml`): DB via `IKP_DB_URL/IKP_DB_USER/IKP_DB_PASSWORD` (dev defaults `jdbc:postgresql://localhost:5432/ikp`); JWT secret dev-default overridable by `IKP_JWT_SECRET`; content import dir `/home/z/my-project/imports`; fixture gate `app.content.allow-fixtures` (env `IKP_ALLOW_FIXTURES`); storage = private local filesystem buckets with quarantine (S3 deferred to OD-06).

**Inventory:** 38 `@RestController` classes, 32 `@Entity` classes, 18 `@Service` classes across packages: `auth, book, content, dashboard, quran, hadith, dua, prayer, calendar, dictionary, subject, academy, assessment, research, scholars, fatwa, personal, usercontent, storage, search, audit, workflow, home, daily, web, config`.

**M6 modules in source: ALL ABSENT.** There is no `ai` package, no conversation/embedding/citation-verification/religious-safety/evaluation code, and the migration set is exactly `V1__identity_and_access … V6__m5_learning_research` + repeatable seeds. Module-by-module against the checklist: authentication/authorisation ✓ (M2 RBAC, tested), source governance ✓ **only as M5 research-citation verification** (research citations validated against imported Quran at save time; fabricated reference → `VAL-001`), ingestion pipeline ✓ **as M4 controlled imports** (`ContentImportService` + `IntegrityService`), full-text retrieval ✓ (global search, M4/M5), permission filtering ✓ (RBAC) — **embedding abstraction ✗, generation abstraction ✗, citation-verification engine ✗ (M6 form), religious-safety engine ✗, conversation API ✗, feedback API ✗, usage controls ✗, evaluation framework ✗** (all existed only in the lost M6 backend; their *schema* survives in the `islamic_ai` DB, §9).

**Test execution (today):**

```bash
mvn -B -ntp -s scripts/settings-mirror.xml test   # JDK 21, Maven 3.9.16
```

**Result: 91 tests run — 7 passed, 84 errors, 0 failures, 0 skipped → BUILD FAILURE.** Per class:

| Test class | Run | Errors |
|---|---|---|
| `FlywayMigrationTest` | 7 | **0 — passed** (no Spring context) |
| `BookWorkflowIntegrationTest` | 6 | 6 |
| `M4ContentImportIntegrityTest` | 9 | 9 |
| `SubjectCmsIntegrationTest` | 9 | 9 |
| `HealthEndpointTest` | 1 | 1 |
| `MediaUploadIntegrationTest` | 8 | 8 |
| `M4PersonalLibraryPrivacyTest` | 5 | 5 |
| `DashboardAuditIntegrationTest` | 4 | 4 |
| `M5AssessmentSecurityIntegrationTest` | 9 | 9 |
| `M5AcademyIntegrationTest` | 8 | 8 |
| `FatwaSecurityIntegrationTest` | 9 | 9 |
| `RbacIntegrationTest` | 8 | 8 |
| `M5ResearchScholarsIntegrationTest` | 8 | 8 |

**Root cause (established, not guessed):** every Spring-context test fails during context load with

```
ConflictingBeanDefinitionException: Annotation-specified bean name 'globalExceptionHandler'
 for bean class [app.ikp.backend.web.GlobalExceptionHandler] conflicts with existing,
 non-compatible bean definition of same name and class [app.ikp.backend.common.GlobalExceptionHandler]
```

Two `@RestControllerAdvice` classes exist:
- `app/ikp/backend/web/GlobalExceptionHandler.java` — **35 lines, the M1 placeholder** (`{"error":{code,message}}` shape);
- `app/ikp/backend/common/GlobalExceptionHandler.java` — **90 lines, the M2 replacement** (RFC-7807 `problem+json`), whose own header states it *replaces the M1 placeholder shape*.

The stale M1 file makes the context unbootable. The Zonky embedded Postgres itself starts fine (PostgreSQL 14.19 bootstrap visible in the log) — the failure is purely the bean-name conflict. **Per the “preserve all existing work / do not hide failures” mandate, nothing was changed.** Recommended one-line fix for the next engineer: delete the stale `web/GlobalExceptionHandler.java`, then re-run `mvn test` (expect ≈91 passes; the M5 evidence report documents **92/92** at M5 acceptance — the tree now has 91, a one-test drift investigated in §13).

Logs and surefire XML preserved: `docs-m6/evidence/m6-backend-test.log`, `backend/target/surefire-reports/`.

**Historical claims check:**
- *“41/41 backend tests”* — **not reproducible**: belongs to the lost M6 backend (`islamic-platform-backend-0.6.0-M6`); no source, no reports, no DB record of test counts. Not fabricated here.
- *“M5 backend 92/92”* — documented in `docs-m5/m5-evidence-report.md`; current tree has 91 tests and is blocked by the defect above.
- *“20/20 golden evaluation”* — **corroborated by surviving database records** (§9): 3 evaluation runs on 2026-11-03, each `total_cases=20, passed=20, failed=0, pass_rate=1.0000, status=COMPLETED`, with the run summary explicitly stating *“Synthetic technical fixtures only; no scholar-reviewed religious answers evaluated”*.

## 8. Admin implementation and test results

**Stack (verified):** React **18.3.1**, react-router-dom **6.30.6**, Vite **5.4.21** (resolved from ^5.4.11), TypeScript **5.6.3**, Tailwind **3.4.15**, Vitest **2.1.8**. `node_modules` had been wiped by the recycle; `npm ci` reinstalled cleanly (exit 0).

**Test run:** `npm test` (vitest run) → **Test Files 7 passed (7), Tests 40 passed (40), 0 failed** (7.15 s). Files: `auth.test.tsx` (6), `dashboard.test.tsx` (4), `HealthBadge.test.tsx` (2), `books-review-audit.test.tsx`, `users-subjects.test.tsx`, `m5-pages.test.tsx`, `ContentPage.test.tsx`. Log: `docs-m6/evidence/m6-admin-test.log`. Note: the handed-over claim *“4/4”* corresponds to **nothing** in the current tree or in milestone documentation (M5 evidence says 40/40); the verified, reproducible figure today is **40/40**.

**Production build:** `npm run build` (`tsc -b && vite build`) → **SUCCESS** (62 modules; `dist/index.html` 0.42 kB, `assets/index-*.css` 20.49 kB, `assets/index-*.js` 332.60 kB / 90.26 kB gzip). Log: `docs-m6/evidence/m6-admin-build.log`.

**Screen audit against the checklist:**

| M6 admin surface | Status |
|---|---|
| AI dashboard | **ABSENT** |
| OD-07 status screen | **ABSENT** |
| Kill switch UI | **ABSENT** (the kill-switch *backend flag + audit trail* exists in the surviving DB) |
| AI source registry | **ABSENT** |
| Governance screens | **ABSENT** for AI (governance for books/fatwa workflow from M2/M5 present: `features/review`, `features/books`) |
| Ingestion screens | **ABSENT** (M2 `features/uploads` is media upload, not AI ingestion) |
| Evaluation screens | **ABSENT** |
| Feedback screens | **ABSENT** |

Present features: `academy, assessment, audit, auth, books, content, dashboard, profile, research, review, scholars, subjects, uploads, users` (M2–M5 scope).

## 9. Database and local infrastructure

- **Server:** PostgreSQL **16.4** (`tools/pgbin`), data dir `/home/z/my-project/tools/pgdata`, listening `127.0.0.1:54329`. **Running** (restarted today after the recycle damage repair described in §2; restart-and-restore demonstrated). Auth is local trust (dev posture); **no passwords are printed in this report**. Credentials for the app are supplied via environment (`IKP_DB_*`), never committed.
- **Databases:** `islamic_ai` (M6-lineage), `postgres`, `template1`. The M1–M5 backend's dev DB (`ikp`) is **not present** in this cluster — the M1–M5 backend was verified against Zonky-embedded instances, not this one.
- **`islamic_ai` — the surviving M6 database.** 18 tables in `public`:
  `users, ai_sources, ai_source_audit, ingestion_jobs, ingestion_job_events, content_chunks, content_embeddings, ai_conversations, ai_messages, ai_citations, ai_feedback, ai_usage_records, ai_budgets, ai_feature_flags, evaluation_cases, evaluation_runs, admin_audit, flyway_schema_history`.
- **Flyway applied versions (all `success=true`):** V1 foundation · V2 ai sources · V3 ingestion · V4 chunks embeddings · V5 conversations · V6 feedback usage flags · V7 evaluation · V8 seed governance · V9 chunk search tokenizer. *(The M1–M5 backend's V1–V6 identity/content migrations are a different lineage and are NOT applied here.)*
- **Extensions:** `plpgsql` only. **pgvector: not installed, not available** → the M6 design stored embeddings as **JSONB** (`content_embeddings.vector jsonb`, with `provider/model/model_version/dimensions/index_version` columns) and used **PostgreSQL FTS** for retrieval: `content_chunks.search_tsv tsvector` with GIN index `idx_chunks_tsv`; observed chunks carry populated tsvectors — **FTS retrieval demonstrably worked**.
- **Fixture data: YES, pervasively and honestly labelled.** 15 `ai_sources` (5 `FIXTURE-*` APPROVED fixtures + `GOV-BLOCKED-001` governance-negative test source + `IT-*` integration-test sources, several REVOKED to prove revocation paths); 20 `evaluation_cases` **all `is_fixture=true`, `reviewer_status=SYNTHETIC_TECHNICAL_FIXTURE`**; 9 chunks; 9 embeddings (`DETERMINISTIC_FIXTURE/deterministic-hash/dim=64`); 108 users (1 ADMIN + 107 test USERs). **Fixture mode is enabled** in the surviving backend contract (`app.content.allow-fixtures=true` default) and every AI row is explicitly `is_fixture`-labelled. No real/unlicensed religious content is present.
- **Execution evidence preserved in the DB (2026-11-03 13:05–13:18 UTC):** 36 conversations (33 ACTIVE/3 DELETED); 180 messages — 90 USER + 90 ASSISTANT with recorded response states **72 ANSWERED (fixture), 9 INSUFFICIENT_EVIDENCE, 3 SCHOLAR_REFERRAL, 3 CONFLICTING_SOURCES, 3 REFUSED_UNSAFE**; **153/153 citations `VERIFIED`**; 90 usage records — provider `DETERMINISTIC_FIXTURE`, model `deterministic-extractive`, **estimated cost 0.000000**; 11 ingestion jobs (5 READY, 3 QUARANTINED with reasons, 3 ROLLED_BACK); **3 evaluation runs, each 20/20 passed**; kill-switch toggled on/off 3× with full `admin_audit` trail; `ai_budgets` GLOBAL: $0.00/month, 50 req/user/day, 4000 tokens/request, 6 context chunks.
- **Feature flags (current DB state):** `ai.assistant.enabled=true` · **`ai.generation.enabled=false`** (*“DISABLED until OD-07 provider approval”*) · **`ai.embedding.production.enabled=false`** · `ai.evaluation.enabled=true` · `ai.killswitch=false`.
- Full dump of all inspection queries: `docs-m6/evidence/m6-db-inspection.txt` (JDBC scripts: `/home/z/my-project/scripts/InspectDb*.java`).

## 10. Documentation status

**The 13 expected `docs-m6/` documents: ALL MISSING.** `docs-m6/` did not exist in the workspace before this report; this directory was created **only** to hold `M6-HANDOVER-REPORT.md` (this file) and `evidence/` (today's logs). Nothing was recreated or back-filled to look historical. Per-file: `m6-baseline-inspection.md`, `m6-project-architecture.md`, `m6-source-governance.md`, `m6-ingestion-pipeline.md`, `m6-content-chunking.md`, `m6-embedding-abstraction.md`, `m6-retrieval-engine.md`, `m6-generation-abstraction.md`, `m6-citation-verification.md`, `m6-religious-safety.md`, `m6-conversation-and-usage-controls.md`, `m6-evaluation-framework.md`, `m6-testing-and-security-report.md` — **each: missing, no location, no contents, no supporting evidence in-tree.** (The M6 master prompt's own 13-file list — `m6-inspection-report.md`, `m6-rag-architecture.md`, `ai-*.md`, etc. — is likewise entirely missing.)

**What does exist:** `docs-m1/` (5 files) · `docs-m2/` (8) · `docs-m3/` (15) · `docs-m4/` (8) · `docs-m5/` (8, incl. `evidence/` with the live-stack harness log) — the reconstructed-lineage documentation, each with implementation/evidence reports. The M0 blueprint package lives in `download/islamic-knowledge-platform/` (17 docs + tracker + `BASELINE-APPROVAL.md`, approval granted 2026-11-02).

**Honest loss statement (as required):** the former M0–M4 repository and evidence packages were **lost during sandbox recycling**. The present M1–M5 tree, its docs and the milestone ZIPs are the **reconstructed lineage** (dated 2026-11-02/03) — they must not be presented as the original M0–M4 evidence. Of the M6 work, only the `islamic_ai` database, `scripts/smoke-m6.sh` and `scripts/ResetDb.java` survived; every M6 document, source file, jar, APK and test report is gone. Historical M6 evidence cited in this report is limited to what the surviving database itself records.

## 11. Feature tracker and packaging

- **Excel tracker exists:** `download/14-feature-tracker-v1.4.xlsx` (52,149 bytes; modified 2026-11-03 10:20; creator Z.ai), plus v0.2/v1.0/v1.1/v1.2/v1.3 history. Sheets preserved: Module Inventory, Library Categories, Requirements Checklist (104 records), FR ↔ Tracker Map, Permission Matrix, Dependencies, Open Decisions, Assumptions, Traceability. **Feature IDs preserved** (104 records, incl. `AI-01…AI-05` = M6 Phase 13, currently “Not started”/“Backlog” — which matches the current source state accurately).
- **Known tracker defect (documented, not fixed):** the v1.3→v1.4 updater (`scripts/m5_update_tracker.py`) wrote M5 status/evidence into the **Priority (F)** and **Dependencies (G)** columns of ~20 M5 rows instead of the status columns; those rows' Backend/Admin/Android status cells still read “Not started”/“In Progress”, and the `Current status` column is stale since M0 (“Backlog (blueprint drafted)” on every row). The correct M5 evidence lives in `docs-m5/`.
- **M6 evidence links: none** — no M6 artefact exists to link.
- **Source ZIPs present (paths/sizes):** `islamic-knowledge-platform-blueprint-v0.2.zip` (142,440), `islamic-knowledge-platform-baseline-v1.0.zip` (144,332), `m1-platform-foundation-v0.1.zip` (82,107) + `v0.1.1` (536,301), `m2-admin-cms-foundation-v0.1.zip` (791,928), `m3-android-foundation-v0.1.zip` (1,193,847), `m4-core-knowledge-modules-v0.1.zip` (830,766), `m5-learning-research-v0.1.zip` (1,451,989). **No M6 ZIP existed**; a new handover source ZIP was created today (§16 / final summary).
- **APKs:** 7 deliverable APKs (M3/M4/M5, §4) + the freshly rebuilt in-tree `app-debug.apk`. **Test reports:** admin/Android HTML+XML reports exist in-tree (fresh); backend surefire XML exists (shows the 84-error state honestly).
- **Git commit history: NOT preserved** for platform code (§3).

## 12. Security observations

1. **No secrets committed:** `platform-dev` ships only `.env.example` (placeholders); DB credentials flow via `IKP_DB_*` env; the outer `~/.env` holds a single `DATABASE_URL` (value never printed here). Local Postgres uses trust auth — acceptable for the sandbox only.
2. **Dev-only JWT default** in `application.yml` (`dev-only-secret-change-me…`), explicitly commented as non-production and overridable by `IKP_JWT_SECRET` — must be replaced before any real deployment.
3. **M2–M5 security posture is implemented and test-covered:** Argon2id password hashing, JWT resource server, per-request DB-derived authorities, append-only audit logs, PII masking for Support roles, media quarantine, private buckets, per-user upload quotas, fixture-withholding licence gate (`allow-fixtures=false` proves the production posture).
4. **M6 governance posture (surviving DB only):** per-permission licence flags (`indexing/ai_retrieval/embedding/commercial`), ingestion quarantine + rollback, citation verification status per citation, safety `safety_json` per message, kill switch with audit trail, global budget row at $0.00 — and **generation/embedding flags OFF**. These are schema+data evidence; the enforcing code is lost.
5. **Religious-content integrity:** no fabricated religious content found; all AI-adjacent data is labelled synthetic (`SYNTHETIC_TECHNICAL_FIXTURE`, “Synthetic technical fixture for mechanics testing only”).
6. Operational: the recycle-induced tool damage (Maven wrapper, jlink, PG dirs, background-process reaping) is an environment hazard the next session must know about (§2).

## 13. Known defects

1. **[Blocking] Duplicate `@RestControllerAdvice` bean name** `globalExceptionHandler` (`web/` M1 placeholder vs `common/` M2 RFC-7807) → backend Spring context cannot boot → **84/91 tests error**. Fix: remove the stale `app/ikp/backend/web/GlobalExceptionHandler.java`. Not applied (preserve mandate).
2. **[Blocking] M6 source total loss** — backend/admin/Android M6 code, `docs-m6/`, M6 jar/APK/ZIP all absent; only `islamic_ai` DB + 2 helper scripts survive. Nothing M6 is executable from source.
3. **[Medium] Tracker v1.4 column misalignment** — M5 statuses in Priority/Dependencies; status columns stale (§11).
4. **[Medium] Test-count drift** — `docs-m5` records backend **92/92**; the tree contains **91** tests. One test (likely the M5 regression test) is missing from the current tree; investigate before trusting the M5 regression coverage.
5. **[Env] `fulljdk` lacks `jlink`** → Android builds must use `tools/jdk17` as Gradle JVM.
6. **[Env] Corrupted stock Maven wrapper** (`tools/maven/bin/mvn`) — use `scripts/mvn.sh` (corrected launcher, archived rationale).
7. **[Env] Background processes reaped** between tool calls in this sandbox (maven/gradle/npm/node); only `pg_ctl`-daemonised processes persist — run builds in foreground sessions.
8. **[Cosmetic] `android/.gitignore` is empty and `platform-dev` is not under git** — version control must be re-established (§16).

## 14. Blocked functionality

| Item | Blocked by | Evidence |
|---|---|---|
| Production AI generation | **OD-07** (owner approval) + flag `ai.generation.enabled=false` | DB flags; M6 master prompt Level B; standing restriction “no paid AI provider without owner approval” |
| Production embeddings | **OD-07** + flag `ai.embedding.production.enabled=false` | DB flags |
| Any paid service / unlicensed content import | Standing owner restrictions (M0) | `BASELINE-APPROVAL.md` |
| Fatwa publication without scholar approval | **OD-08** (non-bypassable workflow) | M5 tests (`FatwaSecurityIntegrationTest`); enforced reviewer≠publisher |
| Academy certificates | **OD-11** (deferred) | tracker + M5 governance report |
| Library category 06 | **OD-03** (hidden placeholder, locked server-side + UI) | M2 implementation notes |
| Google Play publication / production hosting | Standing restrictions (OD-06 etc.) | roadmap gates |
| M6 verification re-runs (golden eval, smoke journeys) | **Lost source** — requires M6 re-implementation first | §7, §9 |
| Instrumented Android tests in this environment | No device/emulator attached | §5 |

## 15. Open decisions

From tracker v1.4 (“Open Decisions”), cross-checked with M5 governance report and surviving DB flags. **None of OD-01…OD-11 is confirmed; all carry recorded interim working assumptions.**

| ID | Decision | Status | Interim working assumption (recorded) |
|---|---|---|---|
| OD-01 | App branding / package | **Open — gate G-7** | Working title “Islamic Knowledge Platform”; `applicationId com.ikp.dev.app` placeholder |
| OD-02 | UI languages | **Open — gate G-1** | AR/EN/UR/HI interface |
| OD-03 | Library category 06 | **Open — gate G-1** | Data-driven hidden placeholder; nothing invented |
| OD-04 | Authentication options | **Open — gate G-2** | Email+password implemented; extensible design |
| OD-05 | Content licensing & budget | **Open — gate G-3** | Public-domain-first candidates under verification |
| OD-06 | Hosting & storage budget | **Open — gate G-4** | Local/private storage in dev; small start |
| OD-07 | **AI provider & budget** | **Open — gate G-5** | Provider abstraction built (schema+flags prove it); **pgvector unavailable → JSONB+FTS approach recorded in surviving schema**; **production AI stays blocked** |
| OD-08 | Scholar review arrangement | **Open — gate G-6** | Workflow engine ready for any roster; enforced in M5 |
| OD-09 | Monetisation | **Open** | No payments in v1 |
| OD-11 | Academy certificates | **Open** | Deferred — not implemented |

Confirmed vs proposed vs unresolved: **confirmed** = M0 blueprint approval and its standing restrictions only; **proposed assumptions** = the interim column above plus M6's deterministic-fixture testing posture; **unresolved** = everything gated at G-1…G-7, incl. all of OD-01…OD-11.

## 16. Exact next actions

1. **Re-establish version control immediately:** `git add platform-dev islamic-knowledge-platform … && git commit` in `/home/z/my-project` (the platform code is currently untracked; another recycle would repeat today's loss). Consider pushing the outer repo to a remote.
2. **Fix the backend boot defect** (delete stale `app/ikp/backend/web/GlobalExceptionHandler.java`), then `mvn test` — expect ≈91 passes; reconcile the 92→91 drift against `docs-m5` (§13.4).
3. **Repair tracker to v1.5:** restore Priority/Dependencies on the ~20 affected rows, move M5 statuses into the proper status columns, refresh `Current status` (keep Feature IDs untouched).
4. **Re-implement M6 from the M6 master prompt**, using the surviving `islamic_ai` schema as the authoritative design record: 9 Flyway migrations (V1 foundation → V9 chunk-search tokenizer), JSONB embeddings + tsvector/GIN FTS (no pgvector in this environment), per-permission licence flags, quarantine + rollback ingestion, `safety_json`/`retrieval_json` message records, per-citation verification, `ai_budgets`/`ai_usage_records` controls, `evaluation_cases` semantics, the five feature flags. Rebuild: backend (artifact `islamic-platform-backend`), admin AI screens, Android AI screens; re-create the 13 `docs-m6/` documents.
5. **Re-verify M6 exactly as the DB records it was verified:** backend suite incl. the 20-case golden evaluation (fixture mode only, `DETERMINISTIC_FIXTURE` provider), `scripts/smoke-m6.sh` seven journeys, kill-switch/audit checks — then present M6 for owner review.
6. **Keep production AI blocked** until OD-07 approval **and** all Level-B conditions (provider approval, AI-processing rights per source, evaluation thresholds, scholarly safeguards, privacy/retention terms, cost limits) are met.
7. **Do not start M7** — per instruction, work stops after this handover report and its deliverables.

---

## Final statuses

**M6 Technical Foundation: Incomplete.**
Evidence: the foundation **was** built and verified on 2026-11-03 — the surviving `islamic_ai` database records 9 successful migrations, 3 golden-evaluation runs of 20/20, 153/153 verified citations, safety-refusal/referral/sufficiency behaviours across 90 assistant messages, quarantine/rollback ingestion, kill-switch audit trail and OD-07 flags — **but every source artifact was lost in the sandbox recycle**. Nothing M6 is currently buildable, runnable or re-verifiable from this workspace; the M1–M5 tree itself currently fails 84/91 backend tests due to the stale duplicate class (§7/§13). A foundation that cannot be exercised or inspected from source is, for handover purposes, incomplete.

**Production AI: Blocked.**
Evidence: `ai.generation.enabled=false` and `ai.embedding.production.enabled=false` in the surviving flag table (“DISABLED until OD-07 provider approval”); every recorded generation/embedding call used the `DETERMINISTIC_FIXTURE` provider at $0.000000 cost; no paid AI provider is configured anywhere; OD-07 remains open at gate G-5 and the standing owner restrictions forbid paid AI and unlicensed ingestion. It must remain blocked until the owner approves OD-07 and all Level-B conditions are met.

---

*Verification evidence archive: `docs-m6/evidence/` — `m6-backend-test.log`, `m6-android-build.log`, `m6-android-test.log`, `m6-admin-install.log`, `m6-admin-test.log`, `m6-admin-build.log`, `m6-db-inspection.txt`, `m6-toolchain.txt`, `git-status-snapshot.txt`.*
