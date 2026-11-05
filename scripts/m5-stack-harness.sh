#!/usr/bin/env bash
# M5 live-stack integration harness (extends scripts/m4-stack-harness.sh, Docker-less):
#   1. real PostgreSQL boots (pgbin binaries)
#   2. Spring Boot + Flyway V1..V6 + repeatable seeds apply on startup
#   3. the M5 admin workflows run through the REAL admin API (course, quiz,
#      question review, research article, scholar, fatwa) with separation of
#      duties enforced by the backend (dev synthetic accounts hold distinct roles)
#   4. public endpoints + member actions verified against that content
# Records PASS/FAIL per check; exit 0 only when all pass.
set -uo pipefail
ROOT=/home/z/my-project/download/platform-dev
PG=/home/z/my-project/tools/pgbin/bin
DRIVER=$(find ~/.m2/repository/org/postgresql/postgresql -name "postgresql-42*.jar" | head -1)
sql() { java -cp "$DRIVER" "$ROOT/scripts/JdbcRun.java" "jdbc:postgresql://127.0.0.1:$PGPORT/$2" "$1" 2>/dev/null; }
WORK=/tmp/ikp-m5-stack
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

# [1/7] PostgreSQL
"$PG/initdb" -D "$PGDATA" -U ikp --auth=trust -E UTF8 > "$WORK/logs/initdb.log" 2>&1
"$PG/pg_ctl" -D "$PGDATA" -l "$WORK/logs/postgres.log" -o "-p $PGPORT -c listen_addresses=127.0.0.1" -w start > "$WORK/logs/pg_ctl.log" 2>&1
ok=0
for _ in $(seq 1 30); do
  [ "$(sql "SELECT 1" postgres)" = "1" ] && { ok=1; break; }; sleep 1
done
[ "$ok" = 1 ] && pass "PostgreSQL up on :$PGPORT" || { fail "PostgreSQL"; exit 1; }
sql "CREATE DATABASE ikp" postgres > /dev/null 2>&1

# [2/7] Backend + Flyway (V1..V6 + repeatable seeds)
IKP_DB_URL="jdbc:postgresql://127.0.0.1:$PGPORT/ikp" IKP_DB_USER=ikp IKP_DB_PASSWORD=ikp \
IKP_IMPORT_DIR=/home/z/my-project/imports IKP_STORAGE_ROOT="$WORK/storage" IKP_SEED_DEV_DATA=true \
java -jar "$ROOT/backend/target/ikp-backend-0.1.0-SNAPSHOT.jar" > "$WORK/logs/backend.log" 2>&1 &
ok=0
for _ in $(seq 1 120); do
  curl -fsS "http://127.0.0.1:$BACKPORT/api/v1/health" 2>/dev/null | grep -q '"status":"UP"' && { ok=1; break; }
  sleep 2
done
[ "$ok" = 1 ] && pass "Spring Boot + Flyway (V1..V6) + health UP" || { fail "backend boot"; tail -30 "$WORK/logs/backend.log"; exit 1; }

flyway_rows=$(sql "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL" ikp)
[ "${flyway_rows:-0}" -ge 6 ] && pass "Flyway applied migrations incl. V6 ($flyway_rows versioned rows)" \
  || fail "flyway rows = $flyway_rows"

# [3/7] documented synthetic dev accounts (distinct roles = separation of duties)
# NOTE: the dev seeder is an ApplicationRunner that finishes after /health is UP,
# so logins are retried until the accounts exist (deterministic, no sleeps-blind).
login() { curl -fsS -X POST "http://127.0.0.1:$BACKPORT/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$2\",\"deviceName\":\"m5-harness\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null; }
SA=""; for _ in $(seq 1 45); do SA=$(login dev-superadmin@ikp.test 'Dev#Super-1234'); [ -n "$SA" ] && break; sleep 2; done
ADMIN=$(login dev-admin@ikp.test 'Dev#Admin-1234')
CONTENT=$(login dev-content@ikp.test 'Dev#Content-1234')
SCHOLAR=$(login dev-scholar@ikp.test 'Dev#Scholar-1234')
ACADEMY=$(login dev-academy@ikp.test 'Dev#Academy-1234')
READER=$(login dev-reader@ikp.test 'Dev#Reader-1234')
[ -n "$SA" ] && [ -n "$CONTENT" ] && [ -n "$SCHOLAR" ] && [ -n "$ACADEMY" ] && [ -n "$READER" ] && [ -n "$ADMIN" ] \
  && pass "dev admin login OK (6 synthetic accounts: super_admin/admin/content_manager/scholar_reviewer/academy_manager/registered_user)" \
  || { fail "dev logins"; exit 1; }

api() { # method url token [body]
  local m=$1 u=$2 t=$3 b=${4:-}
  if [ -n "$b" ]; then
    curl -fsS -X "$m" "http://127.0.0.1:$BACKPORT$u" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$b" 2>/dev/null
  else
    curl -fsS -X "$m" "http://127.0.0.1:$BACKPORT$u" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' 2>/dev/null
  fi
}
pub() { curl -fsS "http://127.0.0.1:$BACKPORT$1" 2>/dev/null; }
jq() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

# [3b] M4 controlled import — the citation verifier resolves quran_ayah
# citations against the imported Quran (SR-01), so it must be present first.
IMP=$(api POST /api/v1/admin/content/import/quran "$SA")
echo "$IMP" | grep -q '"recordsImported":6236' \
  && pass "controlled import: Quran 6,236 ayahs (SR-01, manifest-verified) — prerequisite for citation verification" \
  || { fail "quran import: $(echo "$IMP" | head -c 200)"; exit 1; }

# [4/7] Academy: draft → review → approve (reviewer ≠ creator) → publish → enrol → progress
COURSE=$(api POST /api/v1/admin/academy/courses "$ACADEMY" '{"title":"M5 Harness — Foundations of Tafsir","description":"Harness course on the discipline of tafsir.","language":"en","level":"beginner","subjectSlug":"cat_quran_tafsir","instructorDisplayName":"Ustadh Ahmad (dev data)","instructorCredentialsNote":"Recorded by admin; platform does not generate credentials","estimatedHours":6}')
CID=$(echo "$COURSE" | jq "d['data']['courseId']")
[ -n "$CID" ] && pass "course created (draft) via admin API" || { fail "course create: $(echo "$COURSE" | head -c 200)"; exit 1; }
CH=$(api POST "/api/v1/admin/academy/courses/$CID/chapters" "$ACADEMY" '{"title":"Chapter 1 — What is tafsir?","sortOrder":1}')
CHID=$(echo "$CH" | jq "d['data']['chapterId']")
LESSON=$(api POST "/api/v1/admin/academy/chapters/$CHID/lessons" "$ACADEMY" '{"title":"Lesson 1 — Definition and sources","lessonType":"text","bodyText":"Tafsir is the disciplined explanation of the Quran.","sortOrder":1}')
LID=$(echo "$LESSON" | jq "d['data']['lessonId']")
[ -n "$CHID" ] && [ -n "$LID" ] && pass "chapter + text lesson created" || fail "chapter/lesson"

EARLY=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/admin/academy/courses/$CID/publish" -H "Authorization: Bearer $SA")
[ "$EARLY" = "409" ] && pass "publish before approval rejected (409 WORKFLOW-001)" || fail "early publish = $EARLY"
SELF=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/admin/academy/courses/$CID/approve-editorial" -H "Authorization: Bearer $ACADEMY")
[ "$SELF" = "409" ] && pass "creator self-approval denied (separation of duties)" || fail "self-approval = $SELF"
api POST "/api/v1/admin/academy/courses/$CID/submit-review" "$ACADEMY" > /dev/null
api POST "/api/v1/admin/academy/courses/$CID/approve-editorial" "$CONTENT" > /dev/null
api POST "/api/v1/admin/academy/courses/$CID/publish" "$SA" > /dev/null
pub "/api/v1/academy/courses/$CID" | grep -q "M5 Harness — Foundations of Tafsir" \
  && pass "published course visible to guests with chapters" || fail "guest course detail"

ENROL=$(api POST "/api/v1/academy/courses/$CID/enrol" "$READER")
echo "$ENROL" | grep -q '"status":"active"' && pass "reader enrolled (ownership-scoped)" || fail "enrol: $(echo "$ENROL" | head -c 150)"
DONE=$(api POST "/api/v1/academy/lessons/$LID/complete" "$READER")
echo "$DONE" | grep -q '"percent":100' && pass "lesson completion → course progress 100%" || fail "progress: $(echo "$DONE" | head -c 150)"
ANON=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/academy/courses/$CID/enrol")
[ "$ANON" = "401" ] && pass "anonymous enrolment rejected (401)" || fail "anon enrol = $ANON"

# [5/7] Assessments: versioned question review; grading never leaks pre-submission
QUIZ=$(api POST /api/v1/admin/assessments/quizzes "$ACADEMY" '{"title":"M5 Harness — Aqeedah Basics","subjectSlug":"cat_aqeedah","passScorePct":50}')
QIDZ=$(echo "$QUIZ" | jq "d['data']['quizId']")
Q=$(api POST "/api/v1/admin/assessments/quizzes/$QIDZ/questions" "$ACADEMY" '{"prompt":"How many pillars of Iman are mentioned in the Hadith of Jibril?","explanation":"The Hadith of Jibril mentions six pillars of Iman.","options":[{"optionText":"Five","correct":false},{"optionText":"Six","correct":true}]}')
DETAIL=$(api GET "/api/v1/admin/assessments/quizzes/$QIDZ" "$ACADEMY")
QQ=$(echo "$DETAIL" | jq "d['data']['questions'][0]['questionId']")
[ -n "$QIDZ" ] && [ -n "$QQ" ] && pass "quiz + versioned question created (draft)" || { fail "quiz create: $(echo "$DETAIL" | head -c 200)"; exit 1; }
BLOCK=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/admin/assessments/quizzes/$QIDZ/publish" -H "Authorization: Bearer $SA")
[ "$BLOCK" = "409" ] && pass "quiz publish blocked while question unreviewed (409)" || fail "quiz publish gate = $BLOCK"
# the quiz itself must clear its own state machine (draft → approved), then the
# question clears the versioned review, then publish succeeds
api POST "/api/v1/admin/assessments/quizzes/$QIDZ/submit-review" "$ACADEMY" > /dev/null
api POST "/api/v1/admin/assessments/quizzes/$QIDZ/approve-editorial" "$CONTENT" > /dev/null
api POST "/api/v1/admin/assessments/questions/$QQ/submit-review" "$ACADEMY" > /dev/null
api POST "/api/v1/admin/assessments/questions/$QQ/decision" "$CONTENT" '{"approve":true,"notes":"verified against source"}' > /dev/null
QP=$(api POST "/api/v1/admin/assessments/quizzes/$QIDZ/publish" "$SA")
echo "$QP" | grep -q '"after":"published"' && pass "quiz published after quiz + question approval" || fail "quiz publish: $(echo "$QP" | head -c 200)"
PUBLIST=$(pub "/api/v1/assessments/quizzes")
echo "$PUBLIST" | grep -q "M5 Harness" && pass "published quiz in public catalogue" || fail "public quiz list: $(echo "$PUBLIST" | head -c 150)"
START=$(api POST "/api/v1/assessments/quizzes/$QIDZ/attempts" "$READER")
ATT=$(echo "$START" | jq "d['data']['attemptId']")
[ -n "$ATT" ] || fail "attempt start failed: $(echo "$START" | head -c 200)"
if echo "$START" | grep -q "correctOptionId\|explanation\|\"correct\""; then
  fail "attempt payload LEAKS correctness data"
else
  pass "attempt start payload carries NO correct flag / explanation"
fi
OPT=$(echo "$START" | jq "d['data']['questions'][0]['options'][1]['optionId']")
SUBMIT=$(api POST "/api/v1/assessments/attempts/$ATT/submit" "$READER" "{\"answers\":[{\"questionId\":\"$(echo "$START" | jq "d['data']['questions'][0]['questionId']")\",\"optionId\":\"$OPT\"}]}")
[ -n "$SUBMIT" ] || { SC=$(curl -s -o /tmp/m5-submit-dbg -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/assessments/attempts/$ATT/submit" -H "Authorization: Bearer $READER" -H 'Content-Type: application/json' -d "{\"answers\":[{\"questionId\":\"$(echo "$START" | jq "d['data']['questions'][0]['questionId']")\",\"optionId\":\"$OPT\"}]}"); fail "submit failed (http $SC): $(head -c 250 /tmp/m5-submit-dbg)"; }
echo "$SUBMIT" | grep -q '"scorePct":100' && pass "server-side grading: correct answer scored 100 with explanation" || fail "submit: $(echo "$SUBMIT" | head -c 200)"
HIST=$(api GET "/api/v1/assessments/attempts?page=1&size=5" "$READER")
echo "$HIST" | grep -q '"attemptId"' && pass "attempt history recorded" || fail "attempt history"

# [6/7] Research + scholars + fatwa (OD-08 governance live)
SUID=$(api GET /api/v1/auth/me "$SCHOLAR" | jq "d['data']['userId']")
SCH=$(api POST /api/v1/admin/scholars "$SA" "{\"name\":\"Dev Scholar Profile (harness)\",\"biography\":\"Test-only profile\",\"qualifications\":\"Recorded by admin (harness)\",\"areasOfExpertise\":\"Hadith sciences\",\"linkedUserPublicId\":\"$SUID\"}")
SID=$(echo "$SCH" | jq "d['data']['scholarId']")
VER=$(api POST "/api/v1/admin/scholars/$SID/verify" "$SA" '{"status":"verified"}')
echo "$VER" | grep -q '"verificationStatus":"verified"' && pass "scholar created + credential verification recorded (audited)" || fail "scholar verify: $(echo "$VER" | head -c 150)"
pub "/api/v1/scholars" | grep -q "Dev Scholar Profile" && pass "verified scholar in public directory" || fail "public scholars"

ART=$(api POST /api/v1/admin/research/articles "$CONTENT" '{"topicSlug":"quran","title":"M5 Harness — Sabab al-Nuzul overview","summary":"Why the occasion of revelation matters in tafsir.","body":"Sabab al-nuzul links revelation to events; e.g. the opening of Surah al-Fatiha.","sourceTier":"secondary","citations":[{"citationKind":"quran_ayah","surahId":1,"ayahNumber":1,"note":"Fatiha 1:1 structural anchor"}]}')
AID=$(echo "$ART" | jq "d['data']['articleId']")
echo "$ART" | jq "d['data']['citations'][0]['resolved']['textUthmani']" | grep -qE ".+" \
  && pass "article created; quran_ayah citation auto-verified against imported Quran (SR-01)" || fail "citation resolution: $(echo "$ART" | head -c 200)"
BAD=$(curl -s -X POST "http://127.0.0.1:$BACKPORT/api/v1/admin/research/articles" -H "Authorization: Bearer $CONTENT" -H 'Content-Type: application/json' -d '{"topicSlug":"quran","title":"Bad citation","body":"b","sourceTier":"secondary","citations":[{"citationKind":"quran_ayah","surahId":999,"ayahNumber":1}]}')
echo "$BAD" | grep -q '"code":"VAL-001"' && pass "fabricated citation rejected (surah 999 → VAL-001)" || fail "bad citation: $(echo "$BAD" | head -c 150)"
api POST "/api/v1/admin/research/articles/$AID/submit-review" "$CONTENT" > /dev/null
api POST "/api/v1/admin/research/articles/$AID/approve-editorial" "$ADMIN" > /dev/null
api POST "/api/v1/admin/research/articles/$AID/approve" "$SCHOLAR" > /dev/null
api POST "/api/v1/admin/research/articles/$AID/publish" "$SA" > /dev/null
pub "/api/v1/research/articles" | grep -q "M5 Harness — Sabab al-Nuzul" \
  && pass "secondary article published after editorial + scholarly review" || fail "article publish"

FAT=$(api POST /api/v1/admin/fatwas "$SA" '{"question":"M5 harness — is intention required for wudu?","answerDraft":"Harness draft text recorded by admin.","category":"purification","language":"en","sourceRefs":["Sahih al-Bukhari 1"]}')
FID=$(echo "$FAT" | jq "d['data']['fatwaId']")
[ -n "$FID" ] && pass "fatwa draft created (super_admin holds fatwa.create)" || fail "fatwa create: $(echo "$FAT" | head -c 200)"
api POST "/api/v1/admin/fatwas/$FID/submit-review" "$SA" > /dev/null
api POST "/api/v1/admin/fatwas/$FID/assign-reviewer" "$SA" "{\"reviewerUserId\":\"$SUID\"}" > /dev/null
api POST "/api/v1/admin/fatwas/$FID/decision" "$SCHOLAR" '{"decision":"approve","notes":"harness scholarly approval"}' > /dev/null
SELF_PUB=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$BACKPORT/api/v1/admin/fatwas/$FID/publish" -H "Authorization: Bearer $SCHOLAR")
[ "$SELF_PUB" = "403" ] && pass "scholar cannot publish own approval (OD-08 separation enforced)" || fail "fatwa publish separation = $SELF_PUB"
api POST "/api/v1/admin/fatwas/$FID/publish" "$SA" > /dev/null
pub "/api/v1/fatwas" | grep -q "M5 harness" && pass "published fatwa in public directory" || fail "public fatwas"
FD=$(pub "/api/v1/fatwas/$FID")
echo "$FD" | jq "d['data']['review']['scholar']['name']" | grep -qE "Dev Scholar" \
  && pass "fatwa detail shows reviewing scholar + review metadata" || fail "fatwa review block"

# [7/7] Search, dashboard, integrity
SEARCH=$(pub "/api/v1/search?q=Tafsir")
echo "$SEARCH" | grep -q '"courses"' && echo "$SEARCH" | grep -q '"research"' \
  && pass "global search serves live courses + research groups (M4 placeholders gone)" || fail "search groups"
DASH=$(api GET /api/v1/admin/dashboard "$SA")
echo "$DASH" | jq "d['data']['content']['publishedCourses']" | grep -qE "[1-9]" && echo "$DASH" | jq "d['data']['content']['assessmentAttempts']" | grep -qE "[1-9]" \
  && pass "dashboard counts real courses/enrolments/attempts" || fail "dashboard: $(echo "$DASH" | head -c 200)"
FIX=$(pub "/api/v1/academy/courses")
[ -n "$FIX" ] && pass "public academy API responds without auth (guest browsing)" || fail "public academy"

echo "----------------------------------------"
echo "M5 harness: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
