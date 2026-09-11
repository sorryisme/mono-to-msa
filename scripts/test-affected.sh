#!/usr/bin/env bash
# pre-push 단계 검사 (목표 1~3분): 컴파일 + 테스트.
# 매퍼 테스트는 실제 MySQL(localhost:3306)을 쓰므로, DB 가 없으면 해당 테스트는
# 건너뛰고 "미검증"으로 보고한다. 전체 강제는 CI 몫.
#
#   종료:   0 = 통과, 1 = 실패
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR"

rc=0
step() { printf '\n%s▸ %s%s\n' "$C_DIM" "$1" "$C_OFF" >&2; }

step "1/3 컴파일 (compileJava, compileTestGroovy)"
if ./gradlew -q compileJava compileTestGroovy 2>&1 | tail -30; then
  ok "컴파일 통과"
else
  err "컴파일 실패 - ./gradlew compileJava compileTestGroovy 로 확인하세요"
  exit 1
fi

step "2/3 포맷 (spotlessCheck)"
if ./gradlew -q spotlessCheck 2>&1 | tail -20; then ok "포맷 통과"; else
  err "포맷 위반 - ./gradlew spotlessApply"
  rc=1
fi

step "3/3 테스트"
# 실패 원인을 리포트에서 뽑아 해결 방법까지 같이 알려준다.
diagnose() {
  local hints
  hints=$(grep -rhoE "Access denied for user [^<]{0,60}|Unknown database [^<]{0,40}|Communications link failure|jdbcUrl is required" \
            build/reports/tests/test/classes/*.html 2>/dev/null | sort -u | head -5)
  [ -z "$hints" ] && return
  echo >&2
  warn "감지된 원인:"
  while IFS= read -r h; do info "- $h"; done <<< "$hints"
  case "$hints" in
    *"Access denied"*|*"Unknown database"*|*"Communications link failure"*)
      info "→ 테스트용 MySQL 이 src/test/resources/application.yml 의 계정/DB 와 맞지 않습니다."
      info "→ docker compose up -d db-master db-replica  (3306 을 다른 MySQL 이 점유 중인지도 확인)" ;;
  esac
  case "$hints" in
    *"jdbcUrl is required"*)
      info "→ 스모크 테스트가 DataSourceConfiguration 의 source/replica 설정을 찾지 못했습니다." ;;
  esac
}

db_up=0
(exec 3<>/dev/tcp/127.0.0.1/3306) 2>/dev/null && db_up=1
if [ $db_up -eq 1 ]; then
  info "MySQL localhost:3306 감지 - 전체 테스트 실행"
  if ./gradlew test 2>&1 | tail -40; then ok "전체 테스트 통과"; else
    err "테스트 실패 - 리포트: build/reports/tests/test/index.html"
    diagnose
    rc=1
  fi
else
  warn "MySQL localhost:3306 에 접속할 수 없습니다. 매퍼 테스트를 건너뜁니다."
  info "전체 검증을 하려면: docker compose up -d db-master db-replica"
  if ./gradlew test --tests '*ServiceTest' 2>&1 | tail -40; then
    ok "서비스 테스트 통과"
    warn "미검증: 매퍼 테스트(*MapperTest), 스모크 테스트 - DB 없이 실행하지 못했습니다"
  else
    err "테스트 실패 - 리포트: build/reports/tests/test/index.html"
    diagnose
    rc=1
  fi
fi

echo >&2
if [ $rc -ne 0 ]; then
  err "pre-push 검사 실패."
  info "재실행:  bash scripts/test-affected.sh"
  info "우회(비상시):  git push --no-verify   ← CI 에서는 그대로 걸립니다"
fi
exit $rc
