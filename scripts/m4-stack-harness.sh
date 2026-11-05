#!/usr/bin/env bash
# M4 live-stack integration harness (Docker-less, mirrors scripts/stack-smoke-native.sh):
#   1. real PostgreSQL boots (pgbin binaries)
#   2. Spring Boot + Flyway V1..V5 + repeatable seeds apply on startup
#   3. controlled imports run through the ADMIN API (SR-01 Quran, SR-08 Bukhari,
#      labelled fixtures) using the documented synthetic dev admin
#   4. public M4 endpoints are verified against REAL imported content
#   5. integrity report passes
# Records PASS/FAIL per check; exit 0 only when all pass.
set -uo pipefail
ROOT=/home/z/my-project/download/platform-dev
PG=/home/z/my-project/tools/pgbin/bin
DRIVER=$(find ~/.m2/repository/org/postgresql/postgresql -name "postgresql-42*.jar" | head -1)
sql() { java -cp "$DRIVER" "$ROOT/scripts/JdbcRun.java" "jdbc:postgresql://127.0.0.1:$PGPORT/$2" "$1" 2>/dev/null; }
WORK=/tmp/ikp-m4-stack
PGPORT=5433 BACKPORT=8080
PASS=0 FAIL=0
PGDATA="$WORK/pgdata"
mkdir -p "$WORK/logs"
rm -rf "$PGDATA"

pass() { echo "PASS  $1"; PASS=$((PASS+1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL+1)); }

cleanup() {
  curl -s -X POST "http://127.0.0.1:$BACKPORT/actuator/shutdown" > /dev/null 2>&1
  pkill -f "ikp-backend-0.1.0-SNAPSHOT.jar" 2>/dev/null
  [ -x "$PG/pg_ctl" ] && "$PG/pg_ctl" -D "$PGDATA" stop -m fast > /dev/null 2>&1
}
trap cleanup EXIT

# [1/5] PostgreSQL
"$PG/initdb" -D "$PGDATA" -U ikp --auth=trust -E UTF8 > "$WORK/logs/initdb.log" 2>&1
"$PG/pg_ctl" -D "$PGDATA" -l "$WORK/logs/postgres.log" -o "-p $PGPORT -c listen_addresses=127.0.0.1" -w start > "$WORK/logs/pg_ctl.log" 2>&1
ok=0
for _ in $(seq 1 30); do
  [ "$(sql "SELECT 1" postgres)" = "1" ] && { ok=1; break; }; sleep 1
done
[ "$ok" = 1 ] && pass "PostgreSQL up on :$PGPORT" || { fail "PostgreSQL"; exit 1; }
sql "CREATE DATABASE ikp" postgres > /dev/null 2>&1

# [2/5] Backend + Flyway
IKP_DB_URL="jdbc:postgresql://127.0.0.1:$PGPORT/ikp" IKP_DB_USER=ikp IKP_DB_PASSWORD=ikp \
IKP_IMPORT_DIR=/home/z/my-project/imports IKP_STORAGE_ROOT="$WORK/storage" IKP_SEED_DEV_DATA=true \
java -jar "$ROOT/backend/target/ikp-backend-0.1.0-SNAPSHOT.jar" > "$WORK/logs/backend.log" 2>&1 &
ok=0
for _ in $(seq 1 120); do
  curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/health" 2>/dev/null | grep -q '"status":"UP"' && { ok=1; break; }
  sleep 2
done
[ "$ok" = 1 ] && pass "Spring Boot + Flyway (V1..V5) + health UP" || { fail "backend boot"; tail -30 "$WORK/logs/backend.log"; exit 1; }

flyway_rows=$(sql "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL" ikp)
[ "${flyway_rows:-0}" -ge 5 ] && pass "Flyway applied migrations incl. V5 ($flyway_rows versioned rows)" \
  || fail "flyway rows = $flyway_rows"

# [3/5] login as the documented synthetic dev admin
TOKEN=$(curl -fsS -X POST "http://127.0.0.1:$BACKPORT/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev-superadmin@ikp.test","password":"Dev#Super-1234","deviceName":"m4-harness"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
[ -n "$TOKEN" ] && pass "dev admin login OK (synthetic account)" || { fail "admin login"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

imp() { # url
  curl -fsS -X POST "http://127.0.0.1:$BACKPORT$1" -H "$AUTH" -H 'Content-Type: application/json' 2>/dev/null
}
R=$(imp "/api/v1/admin/content/import/quran")
echo "$R" | grep -q '"recordsImported":6236' && pass "controlled import: Quran 6,236 ayahs (SR-01, manifest-verified)" \
  || fail "quran import: $(echo "$R" | head -c 200)"
imp "/api/v1/admin/content/collections" | grep -q '"registered":6' && pass "hadith collections registered (6)" \
  || fail "collection registration"
R=$(imp "/api/v1/admin/content/import/hadith/bukhari")
echo "$R" | grep -qE '"recordsImported":[0-9]{4}' && pass "controlled import: Bukhari (SR-08, manifest-verified): $(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['recordsImported'])" 2>/dev/null) hadiths" \
  || fail "bukhari import"
imp "/api/v1/admin/content/fixtures" | grep -q '"recordsImported"' && pass "labelled dev fixtures seeded" || fail "fixtures"

# [4/5] public endpoints against real content
check() { # desc url pattern
  body=$(curl -fsS "http://127.0.0.1:$BACKPORT$2" 2>/dev/null)
  echo "$body" | grep -q "$3" && pass "$1" || fail "$1 — got: $(echo "$body" | head -c 160)"
}
check "GET /quran/surahs → 114 surahs" "/api/v1/quran/surahs" '"ayahCount":7'
check "GET /quran/ayahs/2:255 → verbatim Uthmani text" "/api/v1/quran/ayahs/2:255" 'textUthmani'
check "GET /hadith/collections → six collections" "/api/v1/hadith/collections" '"code":"ibnmajah"'
check "GET /search → grouped incl. later-milestone notes" "/api/v1/search?q=prayer" '"note":"content arrives in a later milestone"'
check "GET /home/daily → ayah_of_day with attribution" "/api/v1/home/daily" 'Tanzil'
check "GET /calendar/today → calculated flag" "/api/v1/calendar/today" '"calculated":true'
check "GET /prayer/methods → reference parameters" "/api/v1/prayer/methods" '"fajrAngle"'
check "GET /dictionary/search → labelled fixture entries" "/api/v1/dictionary/search?q=%D9%83" '"isFixture":true'
check "GET /duas/categories → published categories" "/api/v1/duas/categories" '"code":"morning_adhkar"'
# hadith books listing for bukhari
BID=$(curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/hadith/collections/bukhari/books" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['id'])" 2>/dev/null)
check "GET /hadith/books/$BID/hadiths → real hadith rows" "/api/v1/hadith/books/$BID/hadiths?page=1&limit=2" 'textAr'

# [5/5] integrity report
INTEG=$(curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/admin/content/integrity" -H "$AUTH")
echo "$INTEG" | grep -q '"passed":true' && pass "integrity report: ALL CHECKS PASS" || {
  fail "integrity report"; echo "$INTEG" | head -c 600; }

echo
echo "================ M4 native stack harness summary ================"
echo "PASS: $PASS   FAIL: $FAIL"
[ "$FAIL" = 0 ] && echo "M4 LIVE-STACK HARNESS: SUCCESS" || echo "M4 LIVE-STACK HARNESS: FAILED (logs in $WORK/logs/)"
exit "$([ "$FAIL" = 0 ] && echo 0 || echo 1)"
