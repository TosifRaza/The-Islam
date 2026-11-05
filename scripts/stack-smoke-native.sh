#!/usr/bin/env bash
# Native (Docker-less) integration test for the IKP M1 development stack (v0.1.1).
#
# Executes the SAME six checks as scripts/compose-smoke.sh, but boots each
# component as a real local process instead of a container. Use this in
# environments where a Docker daemon is not available:
#
#   1. PostgreSQL starts and accepts connections      (real postgres binaries)
#   2. Flyway migrations execute + Spring Boot health (backend startup, then SQL)
#   3. Approved seed content present                  (exactly 11 roles)
#   4. React admin dashboard served through Nginx     (real nginx binary)
#   5. Backend health re-check                        (direct backend port)
#   6. Nginx /api/ proxy reaches the backend          (same-origin style path)
#
# Required inputs (env):
#   POSTGRES_BIN  dir containing initdb/postgres/pg_ctl (e.g. the extracted
#                 Zonky binaries io.zonky.test.postgres:embedded-postgres-
#                 binaries-linux-amd64; no root, no Docker needed). A psql
#                 client is used when present; otherwise PG_JDBC must point to
#                 a PostgreSQL JDBC driver jar (the backend already ships one
#                 version of it via Maven) and the bundled scripts/JdbcRun.java
#                 acts as the SQL client.
#   NGINX_BIN     nginx binary (compiled with --without-http_rewrite_module
#                 --without-http_gzip_module when built without root)
#   BACKEND_JAR   Spring Boot jar (built via: cd backend && mvn -B -ntp package)
#   ADMIN_DIST    built admin dashboard (built via: cd admin && npm run build)
#   PG_JDBC       (only when no psql client) PostgreSQL JDBC driver jar
#
# Ports: postgres 5433, backend 8080, nginx 8090 (all local, dev only).
# Contains no Islamic content — synthetic data only (OD-05 / G-3 gate intact).
set -uo pipefail

WORK="${IKP_STACK_WORK:-/tmp/ikp-native-stack}"
PGPORT=5433 BACKPORT=8080 NGXPORT=8090
PGDATA="$WORK/pgdata"
PASS=0; FAIL=0; FAILED=0
BACK_PID="" NGX_PID=""

pass() { echo "PASS  $1"; PASS=$((PASS+1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL+1)); FAILED=1; }

cleanup() {
  echo ">> Tearing down native stack"
  [ -n "$BACK_PID" ] && kill "$BACK_PID" 2>/dev/null
  [ -n "$NGX_PID" ] && kill "$NGX_PID" 2>/dev/null
  [ -x "${POSTGRES_BIN:-/nonexistent}/pg_ctl" ] && "$POSTGRES_BIN/pg_ctl" -D "$PGDATA" stop -m fast >/dev/null 2>&1
}
trap cleanup EXIT

for var in POSTGRES_BIN NGINX_BIN BACKEND_JAR ADMIN_DIST; do
  if [ -z "${!var:-}" ]; then echo "ERROR: $var not set (see header of this script)"; exit 1; fi
done
if [ ! -x "$POSTGRES_BIN/psql" ] && [ -z "${PG_JDBC:-}" ]; then
  echo "ERROR: no psql in POSTGRES_BIN and PG_JDBC not set (need a PostgreSQL JDBC driver jar)"; exit 1
fi

rm -rf "$WORK"; mkdir -p "$WORK/logs"
echo ">> workdir: $WORK"

# SQL client: psql when available, else JDBC via scripts/JdbcRun.java
JDIR="$(cd "$(dirname "$0")" && pwd)"
if [ -x "$POSTGRES_BIN/psql" ]; then
  sql()     { PGPASSWORD=ikp "$POSTGRES_BIN/psql" -h 127.0.0.1 -p "$PGPORT" -U ikp -d ikp     -tAc "$1" 2>/dev/null; }
  sql_pg()  { PGPASSWORD=ikp "$POSTGRES_BIN/psql" -h 127.0.0.1 -p "$PGPORT" -U ikp -d postgres -tAc "$1" 2>/dev/null; }
else
  sql()     { java -cp "$PG_JDBC" "$JDIR/JdbcRun.java" "jdbc:postgresql://127.0.0.1:$PGPORT/ikp"     "$1" 2>/dev/null; }
  sql_pg()  { java -cp "$PG_JDBC" "$JDIR/JdbcRun.java" "jdbc:postgresql://127.0.0.1:$PGPORT/postgres" "$1" 2>/dev/null; }
fi
pg_ready() { if [ -x "$POSTGRES_BIN/pg_isready" ]; then "$POSTGRES_BIN/pg_isready" -h 127.0.0.1 -p "$PGPORT" -U ikp >/dev/null 2>&1; else [ "$(sql_pg 'SELECT 1')" = "1" ]; fi; }

# --- [1/6] PostgreSQL -------------------------------------------------------
echo ">> [1/6] Starting real PostgreSQL (binaries: $POSTGRES_BIN)"
"$POSTGRES_BIN/initdb" -D "$PGDATA" -U ikp --auth=trust -E UTF8 > "$WORK/logs/initdb.log" 2>&1 \
  || { fail "initdb"; tail -20 "$WORK/logs/initdb.log"; exit 1; }
"$POSTGRES_BIN/pg_ctl" -D "$PGDATA" -l "$WORK/logs/postgres.log" \
  -o "-p $PGPORT -c listen_addresses=127.0.0.1" -w start > "$WORK/logs/pg_ctl.log" 2>&1 \
  || { fail "pg_ctl start"; tail -20 "$WORK/logs/pg_ctl.log"; exit 1; }
ok=0
for _ in $(seq 1 30); do
  if pg_ready; then ok=1; break; fi
  sleep 1
done
[ "$ok" = 1 ] && pass "PostgreSQL starts and accepts connections" || { fail "PostgreSQL healthy"; tail -20 "$WORK/logs/postgres.log"; }
sql_pg "CREATE DATABASE ikp" > /dev/null 2>&1 || true

# --- [2/6] Spring Boot + Flyway + direct health ------------------------------
echo ">> [2/6] Starting Spring Boot (Flyway migrations apply on startup)"
IKP_DB_URL="jdbc:postgresql://127.0.0.1:$PGPORT/ikp" \
IKP_DB_USER=ikp IKP_DB_PASSWORD=ikp \
java -jar "$BACKEND_JAR" > "$WORK/logs/backend.log" 2>&1 &
BACK_PID=$!
ok=0; body=""
for _ in $(seq 1 120); do
  body=$(curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/health" 2>/dev/null) || { sleep 2; continue; }
  echo "$body" | grep -q '"status":"UP"' && { ok=1; break; }
  sleep 1
done
[ "$ok" = 1 ] && pass "Spring Boot started, Flyway migrations executed, /api/v1/health UP (direct) : $body" \
  || { fail "Spring Boot + Flyway + health"; tail -40 "$WORK/logs/backend.log"; }

# --- [3/6] Seed content -------------------------------------------------------
echo ">> [3/6] Verifying approved seed content"
role_count=$(sql "SELECT count(*) FROM roles" | tail -1)
[ "$role_count" = "11" ] && pass "Exactly 11 approved roles seeded (migrations applied fully)" \
  || fail "roles count = ${role_count:-n/a} (expected 11)"
rows=$(sql "SELECT count(*) FROM flyway_schema_history WHERE success" | tail -1)
echo "   flyway_schema_history successful rows: ${rows:-n/a}"

# --- [4/6] Nginx serves the built React dashboard -----------------------------
echo ">> [4/6] Starting Nginx (static admin dist + /api/ proxy)"
cat > "$WORK/nginx.conf" <<EOF
worker_processes 1;
pid $WORK/nginx.pid;
error_log $WORK/logs/nginx-error.log;
events { worker_connections 128; }
http {
  access_log $WORK/logs/nginx-access.log;
  server {
    listen $NGXPORT;
    root $ADMIN_DIST;
    index index.html;
    location / { try_files \$uri \$uri/ /index.html; }
    location /api/ {
      proxy_pass http://127.0.0.1:$BACKPORT;
      proxy_set_header Host \$host;
      proxy_set_header X-Real-IP \$remote_addr;
    }
  }
}
EOF
"$NGINX_BIN" -c "$WORK/nginx.conf" > "$WORK/logs/nginx-start.log" 2>&1
NGX_PID=$(cat "$WORK/nginx.pid" 2>/dev/null || echo "")
sleep 1
admin_body=$(curl -fsS "http://127.0.0.1:$NGXPORT/" 2>/dev/null)
if echo "$admin_body" | grep -q 'id="root"'; then
  pass "Nginx serves the built React admin dashboard (index.html with #root)"
else
  fail "admin dashboard served through Nginx"; tail -20 "$WORK/logs/nginx-error.log" 2>/dev/null
fi

# --- [5/6] direct backend health re-check -------------------------------------
body=$(curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/health" 2>/dev/null)
echo "$body" | grep -q '"status":"UP"' && pass "Backend health (direct) still UP : $body" || fail "backend health direct"

# --- [6/6] proxy ----------------------------------------------------------------
proxy_body=$(curl -fsS "http://127.0.0.1:$NGXPORT/api/v1/health" 2>/dev/null)
if echo "$proxy_body" | grep -q '"status":"UP"'; then
  pass "Nginx /api/ proxy reaches Spring Boot : $proxy_body"
else
  fail "Nginx /api/ proxy"; tail -20 "$WORK/logs/nginx-error.log" 2>/dev/null
fi

echo
echo "================ native stack smoke summary ================"
echo "PASS: $PASS   FAIL: $FAIL"
[ "$FAILED" = 0 ] && echo "NATIVE STACK SMOKE TEST: SUCCESS" || echo "NATIVE STACK SMOKE TEST: FAILED (logs in $WORK/logs/)"
exit "$FAILED"
