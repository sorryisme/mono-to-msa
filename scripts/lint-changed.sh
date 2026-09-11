#!/usr/bin/env bash
# pre-commit 단계 품질 검사 (목표 10초). 변경된 파일만 본다.
#
#   사용:   scripts/lint-changed.sh [--staged | <파일>...]
#           --staged  스테이징된 파일을 대상으로 한다 (pre-commit 기본값)
#   종료:   0 = 통과, 1 = 차단
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR"

files=()
if [ "${1:-}" = "--staged" ] || [ $# -eq 0 ]; then
  while IFS= read -r f; do [ -n "$f" ] && files+=("$f"); done \
    < <(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null)
else
  files=("$@")
fi

if [ ${#files[@]} -eq 0 ]; then
  info "검사할 변경 파일이 없습니다."
  exit 0
fi

rc=0
step() { printf '\n%s▸ %s%s\n' "$C_DIM" "$1" "$C_OFF" >&2; }

step "1/5 포맷 (spotlessCheck)"
if ./gradlew -q spotlessCheck 2>&1 | tail -20; then
  ok "포맷 통과"
else
  err "포맷 위반 - ./gradlew spotlessApply 로 고친 뒤 다시 add 하세요"
  rc=1
fi

step "2/5 금지 패턴 (System.out, 빈 catch, 테스트 skip ...)"
if bash scripts/guard-scan.sh "${files[@]}"; then ok "금지 패턴 없음"; else rc=1; fi

step "3/5 시크릿 / 대용량 파일"
if bash scripts/secret-scan.sh "${files[@]}"; then ok "시크릿 없음"; else rc=1; fi

step "4/5 의존 방향 (controller -> service -> mapper)"
if bash scripts/arch-check.sh "${files[@]}"; then ok "의존 방향 정상"; else rc=1; fi

step "5/5 스키마 변경 확인"
schema_changed=0
for f in "${files[@]}"; do
  case "$f" in
    src/main/resources/schema.sql|src/main/resources/data.sql|docker-init/*) schema_changed=1 ;;
  esac
done
if [ $schema_changed -eq 1 ]; then
  warn "schema.sql / data.sql / docker-init 이 변경되었습니다."
  info "master/replica 양쪽에 반영되는지, 매퍼 XML 과 컬럼이 일치하는지 확인하세요."
else
  ok "스키마 변경 없음"
fi

echo >&2
if [ $rc -ne 0 ]; then
  err "pre-commit 검사 실패. 고친 뒤 다시 커밋하세요."
  info "재실행:  bash scripts/lint-changed.sh --staged"
  info "우회(비상시):  git commit --no-verify   ← CI 에서는 그대로 걸립니다"
fi
exit $rc
