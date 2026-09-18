#!/usr/bin/env bash
# 부하 테스트 한 명령 실행기.
#   격리 Compose 기동 → 복제·readiness 확인 → fixture → replica 반영 확인 → k6 → DB 사후 검증 → 정리
#
#   사용:   scripts/load-test.sh <scenario> [옵션]
#           scenario: smoke | user-flow | order-contention | idempotency | order-race
#   옵션:   --vus N              시나리오의 VU 수 (기본은 시나리오 파일 참고)
#           --duration 60s       constant-vus 시나리오의 지속 시간 (smoke, user-flow)
#           --iterations N       order-contention 의 VU 당 반복 수
#           --rounds N           order-race 의 주문(라운드) 수
#           --users N            fixture 사용자 수 (기본 100). VU 수 이상이어야 한다
#           --contention-stock N 재고 경합 옵션의 초기 재고 (기본 50)
#           --retry-contract X   idempotency 실패 경로 재시도 계약: RETRY_ALLOWED(기본, 실측값) | PERMANENT_REJECT | '' (관측만)
#           --skip-build         build/libs 의 기존 jar 를 재사용한다
#           --keep               종료 후 컨테이너를 남긴다 (디버깅용. docker compose -p <project> down -v 로 직접 정리)
#   종료:   0 = k6 threshold 와 DB 사후 검증 모두 통과, 그 외 = 실패 (정리 단계는 종료 코드를 바꾸지 않는다)
#   산출물: build/loadtest/<runId>/ (k6.log, summary.json, k6-facts.txt, verify.txt, app.log, db.log)
#
# 방어 조건: 외부 DB 주소나 대상 URL 을 받지 않는다. 대상은 항상 이 스크립트가 만든 Compose 프로젝트 안이고,
# 정리도 그 프로젝트명(fmarket-load-<runId>)으로 한정한다. schema.sql 이 테이블을 DROP 하므로 공유 DB 에 붙이면 안 된다.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR"

# Git Bash 가 "/results/..." 같은 컨테이너 경로 인자를 Windows 경로로 바꾸지 않게 한다.
export MSYS_NO_PATHCONV=1

SCENARIOS="smoke user-flow order-contention idempotency order-race"
COMPOSE_FILE="performance/docker-compose.loadtest.yml"

usage() { sed -n '2,20p' "${BASH_SOURCE[0]}" >&2; exit 2; }

# ---------- 인자 ----------
scenario="${1:-}"; [ -n "$scenario" ] || usage; shift
case " $SCENARIOS " in *" $scenario "*) ;; *) err "알 수 없는 시나리오: $scenario"; usage ;; esac

# 재시도 계약 기본값은 2026-09-18 실측(키가 주문과 함께 롤백됨)으로 정했다. performance/README.md 참고.
VUS="" DURATION="" ITERATIONS="" ROUNDS="" LT_RETRY_CONTRACT="RETRY_ALLOWED"
LT_USERS=100 CONTENTION_STOCK=50 BIG_STOCK=1000000
SKIP_BUILD=0 KEEP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --vus) VUS="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --rounds) ROUNDS="$2"; shift 2 ;;
    --users) LT_USERS="$2"; shift 2 ;;
    --contention-stock) CONTENTION_STOCK="$2"; shift 2 ;;
    --retry-contract) LT_RETRY_CONTRACT="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --keep) KEEP=1; shift ;;
    *) err "알 수 없는 옵션: $1"; usage ;;
  esac
done

# ---------- 방어 조건 ----------
for v in LOADTEST_TARGET_URL LOADTEST_BASE_URL LOADTEST_MYSQL_HOST; do
  if [ -n "${!v:-}" ]; then
    err "$v 가 설정되어 있습니다. 이 스크립트는 외부 대상을 지원하지 않습니다 (schema.sql 이 테이블을 DROP 합니다)."
    exit 2
  fi
done
command -v docker >/dev/null || { err "docker 가 필요합니다"; exit 2; }
docker compose version >/dev/null 2>&1 || { err "docker compose v2 가 필요합니다"; exit 2; }
command -v openssl >/dev/null || { err "openssl 이 필요합니다 (임시 자격증명 생성)"; exit 2; }

# ---------- 실행 단위 ----------
RUN_ID="$(date +%y%m%d%H%M%S)$(openssl rand -hex 2)"
PROJECT="fmarket-load-${RUN_ID}"
RESULTS="$PROJECT_DIR/build/loadtest/$RUN_ID"
mkdir -p "$RESULTS/image"
# k6 이미지는 비루트(uid 12345)로 돌아 결과 디렉터리에 써야 한다. 일회용 디렉터리라 넓게 연다.
chmod 777 "$RESULTS" 2>/dev/null || true

# Docker Desktop(Windows) 에는 C:/... 형태를 넘겨야 바인드 마운트가 호스트 디렉터리를 가리킨다.
host_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

# 실행마다 새 자격증명. 컨테이너와 함께 사라진다.
export MYSQL_DATABASE=fmarket
export MYSQL_USER=fmarket_lt
export MYSQL_PASSWORD="$(openssl rand -hex 12)"
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 12)"
export MYSQL_REPLICATION_USER=repl_lt
export MYSQL_REPLICATION_PASSWORD="$(openssl rand -hex 12)"
export LT_PASSWORD="$(openssl rand -hex 8)"
export LT_KEY_PREFIX="$(openssl rand -hex 4)-0000-4000-8000-"
export RUN_ID LT_USERS LT_RETRY_CONTRACT VUS DURATION ITERATIONS ROUNDS
export LOADTEST_RESULTS_DIR="$(host_path "$RESULTS")"
export LOADTEST_IMAGE_CONTEXT="$(host_path "$RESULTS/image")"
# k6 와 검증 SQL 이 같은 값을 보도록 여기서 한 번만 정한다.
export LT_DUP_GROUPS="${LT_DUP_GROUPS:-10}" LT_DUP_VUS_PER_GROUP="${LT_DUP_VUS_PER_GROUP:-5}" LT_FAIL_VUS="${LT_FAIL_VUS:-5}"
export LT_MAX_QTY="${LT_MAX_QTY:-3}"
ROUNDS_EFFECTIVE="${ROUNDS:-50}"

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }
# MYSQL_PWD 로 넘겨 "Using a password on the command line" 경고를 피한다. -N -B: 헤더 없이 탭 구분.
sql() { # sql <service> [mysql args...]  (stdin 으로 SQL)
  local svc="$1"; shift
  compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$svc" mysql -uroot --default-character-set=utf8mb4 -N -B "$MYSQL_DATABASE" "$@"
}
sql_scalar() { sql "$1" -e "$2" 2>/dev/null | tr -d '\r'; }
# 배치 모드(-B)에서는 \G 세로 출력이 무시되므로 상태 조회는 일반 모드로 따로 한다.
sql_status() { compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$1" mysql -uroot -e "$2" 2>/dev/null | tr -d '\r'; }

step() { printf '\n%s▸ [%s] %s%s\n' "$C_DIM" "$(date +%H:%M:%S)" "$*" "$C_OFF" >&2; }

rc=0
cleanup() {
  local code=$?
  [ $rc -ne 0 ] || rc=$code
  step "정리 (project=$PROJECT)"
  compose logs --no-color --timestamps java-app > "$RESULTS/app.log" 2>&1 || true
  compose logs --no-color --timestamps db-master db-replica > "$RESULTS/db.log" 2>&1 || true
  if [ "$KEEP" = 1 ]; then
    warn "--keep: 컨테이너를 남깁니다. 정리:  docker compose -p $PROJECT -f $COMPOSE_FILE down -v --rmi local"
  else
    case "$PROJECT" in
      fmarket-load-*) compose down -v --rmi local --remove-orphans >/dev/null 2>&1 || warn "정리 실패: docker compose -p $PROJECT down -v 를 직접 실행하세요" ;;
      *) err "예상 밖의 project name($PROJECT) - 정리하지 않습니다" ;;
    esac
  fi
  # jar 사본(수십 MB)은 이미지에 들어갔으므로 결과 디렉터리에 남길 이유가 없다.
  rm -rf "$RESULTS/image"
  info "산출물: $RESULTS"
  # 정리 결과와 무관하게 원래 판정을 그대로 돌려준다.
  exit $rc
}
trap cleanup EXIT
die() { err "$*"; rc=1; exit 1; }

# ---------- 1. jar ----------
step "1/9 애플리케이션 jar 준비"
if [ "$SKIP_BUILD" = 0 ]; then
  ./gradlew bootJar -x test -q || die "bootJar 실패"
fi
jar="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1)"
[ -n "$jar" ] || die "build/libs 에 실행 jar 가 없습니다"
cp "$jar" "$RESULTS/image/app.jar"
ok "$jar"

# ---------- 2. 기동 ----------
step "2/9 격리 환경 기동 (project=$PROJECT)"
compose up -d --build --quiet-pull db-master db-replica java-app 2>&1 | grep -v -i '^\s*$' >&2 || true
compose ps --format '{{.Service}} {{.State}}' >&2
[ "$(compose ps --status running --format '{{.Service}}' | wc -l | tr -d ' ')" -ge 3 ] || die "컨테이너 기동 실패"

# ---------- 3. 복제 ----------
step "3/9 master/replica 역할·복제 상태 확인"
[ "$(sql_scalar db-master 'SELECT @@read_only')" = "0" ] || die "db-master 가 read_only 입니다"
[ "$(sql_scalar db-replica 'SELECT @@read_only')" = "1" ] || die "db-replica 가 read_only 가 아닙니다"
replicating=0
for _ in $(seq 60); do
  st="$(sql_status db-replica 'SHOW REPLICA STATUS\G')"
  if grep -q 'Replica_IO_Running: Yes' <<<"$st" && grep -q 'Replica_SQL_Running: Yes' <<<"$st"; then replicating=1; break; fi
  sleep 2
done
[ $replicating = 1 ] || die "replica 복제가 시작되지 않았습니다 (db.log 확인)"
ok "복제 진행 중 (IO/SQL Running: Yes)"

# ---------- 4. readiness ----------
step "4/9 애플리케이션 readiness (/actuator/health/readiness, 최대 180초)"
compose run --rm -e READINESS_TIMEOUT_SEC=180 k6 run --quiet readiness.js >&2 || die "readiness 실패 (app.log 확인)"
ok "readiness UP (source/replica DataSource 포함)"

# ---------- 5. fixture ----------
step "5/9 fixture (사용자 ${LT_USERS}명, 경합 재고 ${CONTENTION_STOCK})"
compose run --rm k6 run --quiet seed.js >&2 || die "시드 사용자 생성 실패"
fixture_out="$( {
  printf "SET @run_id='%s'; SET @user_count=%d; SET @contention_stock=%d; SET @big_stock=%d;\n" \
    "$RUN_ID" "$LT_USERS" "$CONTENTION_STOCK" "$BIG_STOCK"
  cat performance/sql/fixture.sql
} | sql db-master )" || die "fixture SQL 실패"
while IFS=$'\t' read -r name value; do
  [ -n "$name" ] || continue
  export "LT_${name}=$(tr -d '\r' <<<"$value")"
done <<<"$fixture_out"
[ -n "${LT_FLOW_OPTION_IDS:-}" ] || die "fixture 결과를 읽지 못했습니다"
ok "flow product=$LT_FLOW_PRODUCT_ID contention option=$LT_CONTENTION_OPTION_ID idem ok/empty=$LT_IDEM_OPTION_OK_ID/$LT_IDEM_OPTION_EMPTY_ID race option=$LT_RACE_OPTION_ID"

# ---------- 6. replica 반영 ----------
step "6/9 fixture 의 replica 반영 확인 (로그인·조회는 replica 를 읽는다)"
expected_users=$((LT_USERS + 1))
synced=0
for _ in $(seq 60); do
  u="$(sql_scalar db-replica "SELECT COUNT(*) FROM \`user\` WHERE login_id LIKE 'lt-${RUN_ID}-%'")"
  i="$(sql_scalar db-replica "SELECT COUNT(*) FROM inventory i JOIN product_option po ON po.id=i.product_option_id JOIN product p ON p.id=po.product_id WHERE p.product_name LIKE 'lt-${RUN_ID}-%'")"
  if [ "$u" = "$expected_users" ] && [ "$i" = "24" ]; then synced=1; break; fi
  sleep 1
done
[ $synced = 1 ] || die "replica 에 fixture 가 반영되지 않았습니다 (users=$u/$expected_users inventory=$i/24)"
ok "replica 반영 완료 (users=$u, inventory rows=$i)"

# ---------- 7. k6 ----------
step "7/9 k6 실행: $scenario (VUS=${VUS:-기본} DURATION=${DURATION:-기본} ITERATIONS=${ITERATIONS:-기본} ROUNDS=${ROUNDS:-기본})"
# k6 내장 web dashboard 를 켜서 종료 시 단일 HTML 리포트(시간축 그래프·요약 표)를 남긴다.
# readiness/seed 실행에는 붙이지 않는다 — 같은 파일을 덮어쓰기만 하고 볼 가치가 없다.
compose run --rm \
  -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=/results/report.html \
  -e K6_WEB_DASHBOARD_PERIOD="${K6_WEB_DASHBOARD_PERIOD:-2s}" \
  k6 run --summary-export /results/summary-export.json "$scenario.js" 2>&1 | tee "$RESULTS/k6.log"
k6_rc=${PIPESTATUS[0]}
if [ "$k6_rc" -ne 0 ]; then err "k6 종료 코드 $k6_rc (threshold 위반 또는 실행 오류)"; rc=1; else ok "k6 threshold 통과"; fi

# k6 관측값 → K6_* (없는 카운터는 0)
K6_ORDERS_CREATED=0 K6_ORDERS_CREATED_QTY=0 K6_TRANSPORT_ERRORS=0
if [ -f "$RESULTS/k6-facts.txt" ]; then
  while IFS='=' read -r k v; do
    [ -n "$k" ] || continue
    export "$(tr 'a-z' 'A-Z' <<<"$k")=$(tr -d '\r' <<<"$v")"
  done < "$RESULTS/k6-facts.txt"
else
  warn "k6-facts.txt 가 없습니다 (k6 가 handleSummary 전에 중단됨)"
fi

# ---------- 8. 사후 검증 ----------
verify_sql="performance/sql/verify-${scenario}.sql"
if [ -f "$verify_sql" ]; then
  step "8/9 DB 사후 검증 (master 기준)"
  # 응답이 유실된 요청의 트랜잭션이 아직 진행 중일 수 있다. 활성 트랜잭션이 없어질 때까지 기다린다.
  for _ in $(seq 30); do
    [ "$(sql_scalar db-master 'SELECT COUNT(*) FROM information_schema.innodb_trx')" = "0" ] && break
    sleep 1
  done
  prelude="SET @run_id='${RUN_ID}'; SET @big_stock=${BIG_STOCK}; SET @k6_orders_created=${K6_ORDERS_CREATED}; SET @k6_orders_created_qty=${K6_ORDERS_CREATED_QTY}; SET @k6_transport_errors=${K6_TRANSPORT_ERRORS};"
  case "$scenario" in
    user-flow) prelude+=" SET @flow_product_id=${LT_FLOW_PRODUCT_ID};" ;;
    order-contention) prelude+=" SET @option_id=${LT_CONTENTION_OPTION_ID}; SET @initial_stock=${CONTENTION_STOCK};" ;;
    idempotency) prelude+=" SET @key_prefix='${LT_KEY_PREFIX}'; SET @groups=${LT_DUP_GROUPS}; SET @fail_vus=${LT_FAIL_VUS}; SET @ok_option_id=${LT_IDEM_OPTION_OK_ID}; SET @ok_initial_stock=${BIG_STOCK}; SET @retry_contract='${LT_RETRY_CONTRACT}';" ;;
    order-race) prelude+=" SET @race_option_id=${LT_RACE_OPTION_ID}; SET @race_initial_stock=${BIG_STOCK}; SET @rounds=${ROUNDS_EFFECTIVE};" ;;
  esac
  { printf '%s\n' "$prelude"; cat "$verify_sql"; } | sql db-master | tr -d '\r' > "$RESULTS/verify.txt"
  verify_rc=${PIPESTATUS[1]}
  cat "$RESULTS/verify.txt" >&2
  if [ "$verify_rc" -ne 0 ]; then err "검증 SQL 실행 오류"; rc=1
  elif grep -q $'\tFAIL\t' "$RESULTS/verify.txt"; then err "DB 불변식 위반"; rc=1
  else ok "DB 불변식 통과"; fi
else
  step "8/9 사후 검증 없음 ($scenario)"
fi

# ---------- 9. 결과 ----------
step "9/9 결과"
[ -f "$RESULTS/k6-facts.txt" ] && sed 's/^/  /' "$RESULTS/k6-facts.txt" >&2
if [ $rc -eq 0 ]; then ok "부하 테스트 통과: $scenario (run=$RUN_ID)"; else err "부하 테스트 실패: $scenario (run=$RUN_ID)"; fi

# ---------- 리포트 (판정 이후. 실패해도 종료 코드를 바꾸지 않는다) ----------
# report.md: CI 가 $GITHUB_STEP_SUMMARY 에 붙이는 마크다운. 재료는 k6.log 의 threshold 줄, k6-facts.txt, verify.txt.
# report.html: k6 web dashboard export (그래프). 아티팩트에서 내려받아 연다.
write_report() {
  local md="$RESULTS/report.md" verdict
  [ $rc -eq 0 ] && verdict="✅ 통과" || verdict="❌ 실패"
  {
    echo "## 부하 테스트: \`$scenario\` — $verdict"
    echo
    echo "| 항목 | 값 |"
    echo "|---|---|"
    echo "| run | \`$RUN_ID\` |"
    echo "| 커밋 | \`$(git rev-parse --short HEAD 2>/dev/null || echo '-')\` |"
    echo "| 부하 | VUS=${VUS:-기본} DURATION=${DURATION:-기본} ITERATIONS=${ITERATIONS:-기본} ROUNDS=${ROUNDS:-기본} USERS=${LT_USERS} |"
    echo "| k6 종료 코드 | ${k6_rc:-?} |"
    echo "| 측정 조건 | \`loadtest\` 프로파일(본문 로깅·SQL 로그 OFF). 절대값은 실행 환경 편차가 있어 기준선 없이 비교하지 않는다 |"
    echo
    echo "### k6 threshold"
    echo
    echo "| 결과 | threshold |"
    echo "|---|---|"
    # lib/summary.js 가 찍는 "  PASS  name: expr" 줄
    if grep -qE '^  (PASS|FAIL)  ' "$RESULTS/k6.log" 2>/dev/null; then
      grep -E '^  (PASS|FAIL)  ' "$RESULTS/k6.log" | sed -E 's/^  (PASS|FAIL)  (.*)$/| \1 | `\2` |/' | sed 's/| PASS |/| ✅ PASS |/; s/| FAIL |/| ❌ FAIL |/'
    else
      echo "| - | (k6 가 요약을 남기지 못함. k6.log 확인) |"
    fi
    echo
    echo "### 주요 수치 (k6 관측)"
    echo
    echo "| metric | 값 |"
    echo "|---|---|"
    if [ -f "$RESULTS/k6-facts.txt" ]; then
      grep -vE '^k6_(data_received|data_sent|thresholds_failed)=' "$RESULTS/k6-facts.txt" | sed -E 's/^k6_([^=]+)=(.*)$/| `\1` | \2 |/'
    else
      echo "| - | (없음) |"
    fi
    echo
    echo "### DB 사후 검증 (master)"
    echo
    if [ -f "$RESULTS/verify.txt" ]; then
      echo "| 결과 | 검사 | 상세 |"
      echo "|---|---|---|"
      awk -F'\t' '{ r=$2; if (r=="PASS") r="✅ PASS"; else if (r=="FAIL") r="❌ FAIL"; else r="ℹ️ " r; printf "| %s | `%s` | %s |\n", r, $1, $3 }' "$RESULTS/verify.txt"
    else
      echo "이 시나리오는 사후 검증이 없다."
    fi
    echo
    echo "그래프·시간축은 아티팩트의 \`report.html\`(k6 web dashboard export), 전체 metric 은 \`summary.json\` 참고."
  } > "$md" 2>/dev/null && info "리포트: $md" || warn "report.md 생성 실패 (판정에는 영향 없음)"
  # k6 는 실행이 dashboard 집계 주기 몇 배보다 짧으면 "not enough data" 로 HTML 생성을 건너뛴다.
  # 경합 시나리오(수백 ms)는 원래 그렇고, smoke/user-flow 처럼 수십 초 도는 실행에는 있어야 한다.
  [ -f "$RESULTS/report.html" ] && info "리포트: $RESULTS/report.html" || info "report.html 없음 (실행이 짧아 k6 가 그래프 생성을 건너뜀. 경합 시나리오는 정상)"
}
write_report
exit $rc
