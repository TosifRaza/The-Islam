#!/usr/bin/env bash
# Docker-enabled integration test for the IKP M1 development stack (v0.1.1).
#
# Boots the complete compose stack and verifies, in order:
#   1. PostgreSQL starts and becomes healthy
#   2. Flyway migrations execute (successful rows in flyway_schema_history)
#   3. The approved schema content is present (exactly 11 roles seeded)
#   4. Spring Boot starts and /api/v1/health responds (direct :8080)
#   5. The React admin dashboard is served through Nginx (:5173)
#   6. The Nginx /api/ proxy reaches the backend (:5173/api/v1/health)
#
# Exits non-zero if ANY check fails. Requires Docker + the compose plugin.
# Development-only: this stack is synthetic-data and contains no Islamic
# content (imports gated by OD-05 / G-3).
set -uo pipefail
cd "$(dirname "$0")/.."

LOGDIR="compose-logs"
mkdir -p "$LOGDIR"
PASS=0; FAIL=0; FAILED=0

pass() { echo "PASS  $1"; PASS=$((PASS+1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL+1)); FAILED=1; }

cleanup() {
  echo ">> Tearing down the stack"
  docker compose logs --no-color > "$LOGDIR/compose-full.log" 2>&1 || true
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo ">> Building and starting the compose stack (postgres, backend, admin)"
if ! docker compose up --build -d > "$LOGDIR/compose-up.log" 2>&1; then
  fail "docker compose up --build -d"
  echo ">> compose up log tail:"; tail -30 "$LOGDIR/compose-up.log"
  exit 1
fi
docker compose ps 2>&1 | tee "$LOGDIR/compose-ps.log"

echo ">> [1/6] Waiting for PostgreSQL to become healthy"
ok=0
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U ikp -d ikp >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
[ "$ok" = 1 ] && pass "PostgreSQL starts and accepts connections (pg_isready)" || fail "PostgreSQL healthy"

echo ">> [2/6] Waiting for Flyway migrations to execute (via backend startup)"
ok=0; rows=0
for _ in $(seq 1 120); do
  rows=$(docker compose exec -T postgres psql -U ikp -d ikp -tAc \
    "SELECT count(*) FROM flyway_schema_history WHERE success" 2>/dev/null || echo 0)
  [ "${rows:-0}" -ge 4 ] 2>/dev/null && { ok=1; break; }
  sleep 2
done
[ "$ok" = 1 ] && pass "Flyway migrations executed (4 successful rows: V1, V2 + 2 repeatable seeds)" \
  || fail "Flyway migrations (rows=$rows)"
echo "   flyway_schema_history successful rows: ${rows:-n/a}"

echo ">> [3/6] Verifying approved seed content (11 roles)"
role_count=$(docker compose exec -T postgres psql -U ikp -d ikp -tAc \
  "SELECT count(*) FROM roles" 2>/dev/null || echo 0)
[ "$role_count" = "11" ] && pass "Exactly 11 approved roles seeded (migrations applied fully)" \
  || fail "roles count = $role_count (expected 11)"

echo ">> [4/6] Waiting for Spring Boot health endpoint (direct :8080)"
ok=0; body=""
for _ in $(seq 1 120); do
  body=$(curl -fsS http://localhost:8080/api/v1/health 2>/dev/null) || { sleep 2; continue; }
  echo "$body" | grep -q '"status":"UP"' && { ok=1; break; }
  sleep 2
done
if [ "$ok" = 1 ]; then
  pass "Spring Boot /api/v1/health responds UP (direct) : $body"
else
  fail "backend health endpoint (direct :8080)"
fi

echo ">> [5/6] React admin dashboard through Nginx (:5173)"
admin_body=$(curl -fsS http://localhost:5173/ 2>/dev/null)
if echo "$admin_body" | grep -q 'id="root"'; then
  pass "Nginx serves the built React admin dashboard (index.html with #root)"
else
  fail "admin dashboard served through Nginx"
fi

echo ">> [6/6] Backend API through the Nginx /api/ proxy (:5173/api/v1/health)"
proxy_body=$(curl -fsS http://localhost:5173/api/v1/health 2>/dev/null)
if echo "$proxy_body" | grep -q '"status":"UP"'; then
  pass "Nginx /api/ proxy reaches Spring Boot : $proxy_body"
else
  fail "Nginx /api/ proxy"
fi

echo
echo "================ compose-smoke summary ================"
echo "PASS: $PASS   FAIL: $FAIL"
[ "$FAILED" = 0 ] && echo "STACK SMOKE TEST: SUCCESS" || echo "STACK SMOKE TEST: FAILED (logs in $LOGDIR/)"
exit "$FAILED"
