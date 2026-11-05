# IKP development foundation (M1) — corrected in v0.1.1 (see docs-m1/corrective-changelog.md)
#
# Toolchain notes:
# - backend-*: Apache Maven 3.9.x + JDK 21
# - android-test: any JDK >= 17 with javac on PATH. No Android SDK needed; JUnit
#   is vendored under android/test-lib (checksums in android/README.md).

JAVAC ?= javac
JAVA  ?= java

# SDK-free JVM unit tests: the pure-Java production classes (MainActivity is
# excluded — it needs android.jar) + all JVM test sources, with the vendored
# JUnit jars on the classpath.
JVM_TEST_CLASSES := com.ikp.dev.app.VersionInfoTest
JVM_TEST_OUT     := android/app/build/jvm-test-classes
JVM_TEST_CP      := android/test-lib/junit-4.13.2.jar:android/test-lib/hamcrest-core-1.3.jar
# M3: the canonical JVM test path is `./gradlew testDebugUnitTest` (CI runs it
# on every push; the M3 suite needs Gson/Retrofit/LiveData/OkHttp on the
# classpath and lives in the Gradle test source set). This SDK-free target
# keeps its M1 role: compile + execute the pure-Java utility test with the
# vendored jars, no Android SDK required.
JVM_TEST_MAIN    := android/app/src/main/java/com/ikp/dev/app/VersionInfo.java
JVM_TEST_SOURCES := android/app/src/test/java/com/ikp/dev/app/VersionInfoTest.java

.PHONY: help backend-test backend-run admin-install admin-test admin-build \
        android-test compose-up compose-down compose-smoke clean

help:
	@echo "IKP development foundation (M1, v0.1.1)"
	@echo "  make backend-test   - run backend tests (Maven, real embedded PostgreSQL)"
	@echo "  make backend-run    - run backend locally   (needs PostgreSQL on :5432)"
	@echo "  make admin-install  - npm install admin dashboard"
	@echo "  make admin-test     - run admin unit tests (vitest)"
	@echo "  make admin-build    - production-style admin build (tsc + vite)"
	@echo "  make android-test   - compile + EXECUTE Android JVM unit tests (needs JDK >= 17 on PATH; no SDK)"
	@echo "  make compose-up     - start postgres + backend + admin (needs Docker)"
	@echo "  make compose-down   - stop the local stack"
	@echo "  make compose-smoke  - Docker-enabled integration test of the whole stack (needs Docker)"

backend-test:
	cd backend && mvn -B -ntp test

backend-run:
	cd backend && mvn -B -ntp spring-boot:run

admin-install:
	cd admin && npm install --no-audit --no-fund

admin-test:
	cd admin && npm test

admin-build:
	cd admin && npm run build

# Corrected in v0.1.1: previously this target changed directory before resolving
# repo-relative paths (broken), never put JUnit on the classpath and only
# compiled without executing. It now:
#   1. resolves all paths from this Makefile's directory,
#   2. compiles the pure-Java production class + all test sources with the
#      vendored JUnit 4.13.2 / Hamcrest 1.3 jars on the classpath,
#   3. actually EXECUTES every test method via org.junit.runner.JUnitCore,
#   4. exits non-zero if compilation fails or ANY test fails (JUnitCore's own
#      exit status is propagated — make fails only when a test really fails).
android-test:
	@command -v $(JAVAC) >/dev/null 2>&1 || { echo "ERROR: $(JAVAC) not found — put a JDK (>= 17) on PATH (JAVA_HOME/bin)"; exit 1; }
	@command -v $(JAVA)  >/dev/null 2>&1 || { echo "ERROR: $(JAVA) not found — put a JDK (>= 17) on PATH (JAVA_HOME/bin)"; exit 1; }
	@test -f android/test-lib/junit-4.13.2.jar || { echo "ERROR: android/test-lib/junit-4.13.2.jar missing (see android/README.md)"; exit 1; }
	@rm -rf $(JVM_TEST_OUT)
	@mkdir -p $(JVM_TEST_OUT)
	@echo ">> Compiling $(words $(JVM_TEST_SOURCES)) test source(s) + pure-Java production classes"
	$(JAVAC) -encoding UTF-8 -cp "$(JVM_TEST_CP)" -d $(JVM_TEST_OUT) $(JVM_TEST_MAIN) $(JVM_TEST_SOURCES)
	@echo ">> Executing tests: $(JVM_TEST_CLASSES)"
	$(JAVA) -cp "$(JVM_TEST_OUT):$(JVM_TEST_CP)" org.junit.runner.JUnitCore $(JVM_TEST_CLASSES)

compose-up:
	docker compose up --build -d

compose-down:
	docker compose down

# Docker-enabled integration test: builds and boots the complete compose stack,
# then checks postgres health, Flyway migration success, backend health,
# the Nginx-served admin dashboard and the /api/ proxy. See scripts/compose-smoke.sh.
compose-smoke:
	bash scripts/compose-smoke.sh

clean:
	rm -rf admin/dist admin/node_modules backend/target android/app/build android/.gradle
