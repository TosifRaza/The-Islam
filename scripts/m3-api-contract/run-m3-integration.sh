#!/usr/bin/env bash
# =============================================================================
# M3 integration run: real PostgreSQL (zonky binaries) + real Spring Boot
# backend (Flyway + seed) + the Android app's API-contract harness.
# Everything happens in THIS process tree so the sandbox cannot reap it.
# =============================================================================
set -u
PGBIN=/home/z/my-project/tools/pgbin/bin
PGDATA=/home/z/my-project/tools/pgdata
JAVA21=/home/z/my-project/tools/jdk21
MAVEN=/home/z/my-project/tools/maven/bin/mvn
APP=/home/z/my-project/download/platform-dev/android/app/src/main/java
HARNESS=/home/z/my-project/download/platform-dev/scripts/m3-api-contract
GC=/home/z/.gradle/caches/modules-2/files-2.1
EVIDENCE=/home/z/my-project/download/platform-dev/docs-m3/evidence
mkdir -p "$EVIDENCE"

JARS="/home/z/.gradle/caches/modules-2/files-2.1/com.squareup.retrofit2/retrofit/2.11.0/6ca8c6caf842271f3232e075519fe04081ef7069/retrofit-2.11.0.jar:/home/z/.gradle/caches/modules-2/files-2.1/com.squareup.retrofit2/converter-gson/2.11.0/9d1fe9d1662de0548e08e293041140a8e4026f81/converter-gson-2.11.0.jar:/home/z/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0/527175ca6d81050b53bdd4c457a6d6e017626b0e/gson-2.11.0.jar:/home/z/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.12.0/2f4525d4a200e97e1b87449c2cd9bd2e25b7e8cd/okhttp-4.12.0.jar:/home/z/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio-jvm/3.6.0/5600569133b7bdefe1daf9ec7f4abeb6d13e1786/okio-jvm-3.6.0.jar:/home/z/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.20/e58b4816ac517e9cc5df1db051120c63d4cde669/kotlin-stdlib-1.9.20.jar:/home/z/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm/1.8.0/b8a16fe526014b7941c1debaccaf9c5153692dbb/annotation-jvm-1.8.0.jar:"

echo "== 1. PostgreSQL =="
"$PGBIN/pg_ctl" -D "$PGDATA" -l /tmp/pg.log -o "-p 5432 -k /tmp" start >/dev/null 2>&1 || true
for i in $(seq 1 10); do
  if "$PGBIN/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then echo "postgres: up"; break; fi
  sleep 1
done

echo "== 2. Backend (Spring Boot, port 8080) =="
export JAVA_HOME="$JAVA21"
export PATH="$JAVA21/bin:$PATH"
cd /home/z/my-project/download/platform-dev/backend
IKP_DB_URL="jdbc:postgresql://localhost:5432/postgres" IKP_DB_USER=ikp IKP_DB_PASSWORD=ikp \
  IKP_SEED_DEV_DATA=true "$MAVEN" -B -ntp -q spring-boot:run > /tmp/backend-run.log 2>&1 &
BACK_PID=$!
UP=0
for i in $(seq 1 40); do
  sleep 3
  CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health 2>/dev/null)
  if [ "$CODE" = "200" ]; then UP=1; echo "backend: healthy after ~$((i*3))s"; break; fi
done
if [ "$UP" != "1" ]; then
  echo "backend FAILED to start — log tail:"; tail -30 /tmp/backend-run.log
  kill $BACK_PID 2>/dev/null; "$PGBIN/pg_ctl" -D "$PGDATA" stop >/dev/null 2>&1
  exit 2
fi

echo "== 3. Android API-contract harness (app's own Retrofit layer) =="
"$JAVA21/bin/javac" -encoding UTF-8 -cp "$JARS" -d "$HARNESS/classes" \
  "$HARNESS/ApiContractHarness.java" \
  "$APP/com/ikp/dev/app/core/network/ApiResponse.java" \
  "$APP/com/ikp/dev/app/data/remote/AuthApi.java" \
  "$APP/com/ikp/dev/app/data/remote/LibraryApi.java" \
  "$APP/com/ikp/dev/app/data/remote/dto/Dtos.java" \
  "$APP/com/ikp/dev/app/data/remote/dto/LibraryDtos.java" \
  "$APP/com/ikp/dev/app/data/mapper/Mappers.java" \
  "$APP/com/ikp/dev/app/domain/model/Models.java" 2>&1 | head -20
if [ $? -ne 0 ]; then echo "harness COMPILE failed"; kill $BACK_PID 2>/dev/null; "$PGBIN/pg_ctl" -D "$PGDATA" stop >/dev/null 2>&1; exit 3; fi

"$JAVA21/bin/java" -cp "$JARS:$HARNESS/classes" ApiContractHarness 2>&1 | tee "$EVIDENCE/api-contract-harness.log"
HR_EXIT=${PIPESTATUS[0]}

echo "== 4. Teardown =="
kill $BACK_PID 2>/dev/null
sleep 2
"$PGBIN/pg_ctl" -D "$PGDATA" stop >/dev/null 2>&1
echo "harness exit: $HR_EXIT"
exit $HR_EXIT
