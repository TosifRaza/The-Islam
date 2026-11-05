# Technology Stack Version Review — Spring Boot ↔ Android Gradle Plugin

**Version:** 1.0 · **Date:** 2026-11-02 · **Status:** For M1 owner review (v0.1.1 corrective item 5)
**Scope:** review only. **No version change has been made in v0.1.1.** The owner's instruction — *do not perform an unnecessary major-version upgrade without compatibility testing* — is honoured: every upgrade path below is documented with its implications and is proposed for a tested, scheduled migration, not executed now.

---

## 1. Current versions in the M1 foundation (all verified working in this cycle)

| Component | Version | Evidence from this cycle |
|---|---|---|
| Spring Boot | **3.5.6** | 7/7 tests green (`mvn test`); full stack boot healthy |
| Java (backend) | 21 (Temurin) | `javac 21.0.13` / `java 21.0.13` |
| Spring Framework (managed) | 6.2.11 | boot jar runtime info |
| Flyway (managed) | 11.7.2 | migrations applied on real PostgreSQL (4 successful rows) |
| PostgreSQL driver / Hikari | 42.7.7 / 6.3.3 | stack smoke: `db:"UP"` |
| springdoc-openapi | 2.8.9 | Swagger UI path configured |
| Zonky embedded-postgres (test) | 2.1.1 → PostgreSQL 14.19 binaries | used by backend tests; no Docker needed |
| Android Gradle Plugin | **8.5.2** | `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL |
| Gradle (wrapper, committed) | **8.9** | `./gradlew` run in this cycle, exit 0 |
| JDK (Android toolchain) | 17 (Temurin) | AGP 8.x requires JDK ≥ 17 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 24 | dev-only posture (see §3) |

## 2. Spring Boot — support status and recommended versions

Support data (endoflife.date Spring Boot API, consulted 2026-11-02; matches spring.io's published support policy):

| Line | Latest patch | Released | OSS support end | Extended (commercial) end | Java baseline |
|---|---|---|---|---|---|
| 3.5 (current line of this project) | 3.5.16 (2026-06-25) | 2025-05-31 | **2026-06-30 (already passed)** | 2032-06-30 | Java 17–25 |
| 4.0 | 4.0.9 (2026-10-22) | 2025-11-30 | 2026-12-31 | 2027-12-31 | Java 17–25 |
| **4.1 (current stable)** | **4.1.2 (2026-10-22)** | 2026-06-30 | **2027-07-31** | 2028-07-31 | Java 17–26 |

**Finding:** 3.5.6 is functional and fully verified for M1, but the 3.5 line left OSS support on 2026-06-30 — it no longer receives open-source security patches.

**Recommendation (in order of preference):**

1. **Scheduled, tested upgrade to Spring Boot 4.1.x** early in M2, before further backend feature work. This is the recommended *supported* line (OSS until 2027-07-31, Java 17 baseline unchanged, our Java 21 runtime is inside the supported range).
2. If the owner prefers minimal churn, an **in-line patch bump 3.5.6 → 3.5.16** is low-risk (same minor line, last OSS patch of 3.5) but does not restore OSS security coverage.
3. Either way: the upgrade must land as its own reviewed commit with the full test suite (`mvn test`, 7 tests incl. real-PostgreSQL migration tests) green before merge — per the owner's standing instruction.

**Migration implications of 3.5 → 4.1 (for the spike):**

- Baseline stays Java 17+ — our Java 21 toolchain needs no change.
- **springdoc-openapi 2.8.9 does not support Boot 4** — replace with the springdoc 3.x line (release notes list Boot 4 compatibility). This is the only direct third-party dependency in `pom.xml` that needs a coordinated change.
- Managed-dependency bumps land automatically (Spring Framework 7, Flyway 12 line, Tomcat 11): the migration tests assert schema behaviour, not library internals, so the existing suite is the right safety net; Flyway migration semantics used here (Option A forward-only + repeatable seeds) are unchanged.
- `spring.flyway.*`, `management.*`, `server.*` properties used in `application.yml` remain in the supported set; check the Boot 4.1 release notes for property renames during the spike.
- No code changes expected in the M1 controller/test surface (plain MVC + JDBC); the two test classes compile against stable APIs.

## 3. Android Gradle Plugin — compatibility and recommended versions

AGP ↔ toolchain facts (developer.android.com build guides, consulted 2026-11-02; plus this cycle's empirical build):

| AGP | Min Gradle | Min JDK | Note |
|---|---|---|---|
| **8.5.2 (current in project)** | 8.7 | 17 | verified working with our pinned **Gradle 8.9** wrapper |
| 8.x later patches | 8.9–8.13 | 17 | same 8.x DSL; drop-in patch-level moves |
| 9.x (current stable line, e.g. 9.4.0) | **9.6.0** | 17 | build-tools 36.0.0; new major line |

**Finding:** AGP 8.5.2 + Gradle 8.9 + JDK 17 + compileSdk 34 is a coherent, mutually compatible set and is now **proven by a real build** (`assembleDebug` + `testDebugUnitTest` exit 0; 3/3 tests; APK produced). The committed wrapper (v0.1.1) pins Gradle 8.9 exactly, which satisfies AGP 8.5's ≥ 8.7 requirement.

**Recommendation:**

1. **For M1 and the next phases: stay on AGP 8.5.2 / Gradle 8.9.** It is the newest 8.5 patch, works, and the wrapper removes local-tool drift.
2. Do **not** jump to AGP 9.x now. It requires Gradle ≥ 9.6 and build-tools 36.0.0 and is an unnecessary major upgrade while the app is a skeleton — the same "no major upgrade without compatibility testing" rule applies.
3. **Planned, tested migration path (one change-set):** when UI phases begin or Play-compliance becomes relevant (post gate G-1), move Gradle wrapper + AGP + compileSdk/targetSdk together to the then-current supported line, validated by `./gradlew assembleDebug testDebugUnitTest`. Today `compileSdk/targetSdk 34` is a deliberate dev-only posture: Google Play currently requires target API 35+ for app *updates*; that matters only at Play submission, which is gated by G-1/OD-01 and explicitly out of M1 scope.

## 4. Compatibility matrix (what is pinned where)

| Decision | Value | Where pinned | Status |
|---|---|---|---|
| Spring Boot | 3.5.6 | `backend/pom.xml` (parent) | verified; upgrade path documented above |
| Java backend | 21 | `backend/pom.xml` `<java.version>` | verified |
| springdoc | 2.8.9 | `backend/pom.xml` | compatible with Boot 3.x; must move to 3.x line at Boot 4 |
| AGP | 8.5.2 | `android/build.gradle` | verified |
| Gradle | 8.9 | `android/gradle/wrapper/gradle-wrapper.properties` (committed) | verified |
| JDK Android | 17 | CI `setup-java` + local builds | verified |
| compileSdk/targetSdk | 34 | `android/app/build.gradle` | dev-only; revisit at UI/Play phases |
| JUnit (Android JVM tests) | 4.13.2 | `android/app/build.gradle` + vendored `android/test-lib/` (sha256-documented) | verified |

## 5. Sources

- endoflife.date Spring Boot support API — https://endoflife.date/api/spring-boot.json (consulted 2026-11-02)
- Spring Boot official support policy — https://spring.io/projects/spring-boot#support (support lifecycle)
- Android Gradle Plugin release notes / current stable (9.4.0, Gradle ≥ 9.6.0, build-tools 36.0.0, JDK 17) — https://developer.android.com/build/releases (consulted 2026-11-02)
- AGP past-release requirements (8.5: Gradle ≥ 8.7, JDK 17) — https://developer.android.com/build/releases/past-releases
- Empirical: this cycle's build/test logs in `docs-m1/evidence/` (backend `mvn test`, Android `./gradlew assembleDebug testDebugUnitTest`, stack smoke)
