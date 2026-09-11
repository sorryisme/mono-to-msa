#!/usr/bin/env bash
# 시크릿/대용량 파일 검사. 파일 종류를 가리지 않는다 (yml, properties, sql, env ...).
#
#   사용:   scripts/secret-scan.sh <파일> [<파일>...]
#   종료:   0 = 통과, 1 = 시크릿 또는 대용량 파일 발견
#   예외:   scripts/.hookignore 에 경로(glob)를 한 줄씩 적으면 시크릿 검사에서 제외된다.
#           개별 줄 예외는 "hook-allow:" 주석.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"

IGNORE_FILE="$PROJECT_DIR/scripts/.hookignore"
MAX_BYTES=$((1024 * 1024))   # 1MB

ignored() {
  [ -f "$IGNORE_FILE" ] || return 1
  local rel="${1#"$PROJECT_DIR"/}"
  local pat
  while IFS= read -r pat; do
    case "$pat" in ''|\#*) continue ;; esac
    # shellcheck disable=SC2254
    case "$rel" in $pat) return 0 ;; esac
  done < "$IGNORE_FILE"
  return 1
}

found=0
for f in "$@"; do
  [ -f "$f" ] || continue

  size=$(wc -c < "$f" | tr -d ' ')
  if [ "$size" -gt "$MAX_BYTES" ]; then
    err "$f  ${size} bytes - 1MB 초과 파일은 커밋하지 마세요"
    found=1
  fi

  case "$(basename "$f")" in
    .env|.env.local|.env.production) err "$f  .env 파일은 커밋 대상이 아닙니다 (.gitignore 확인)"; found=1; continue ;;
  esac

  ignored "$f" && continue
  grep -Iq . "$f" 2>/dev/null || continue   # 바이너리 건너뛰기

  # 어떤 파일에서든: 알려진 토큰 형태 + 따옴표로 감싼 자격증명 할당
  pats=(
    -e 'AKIA[0-9A-Z]{16}'
    -e '-----BEGIN[A-Z ]*PRIVATE KEY-----'
    -e 'gh[pousr]_[A-Za-z0-9]{16,}'
    -e 'xox[baprs]-[A-Za-z0-9-]{10,}'
    -e "(password|passwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|client[_-]?secret)[[:space:]]*[:=][[:space:]]*[\"'][A-Za-z0-9/+=_.:@-]{8,}[\"']"
    # ${VAR:기본값} 의 기본값 자리. 아래 "설정 파일" 규칙은 값이 $ 로 시작하면 통째로
    # 건너뛰므로, 환경변수처럼 보이지만 실제 비밀번호가 기본값에 박혀 있는 경우를 놓친다.
    # 값을 비우면(${VAR}) 통과한다 - 누락 시 조용히 접속하는 대신 기동 단계에서 실패한다.
    -e '(password|passwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|client[_-]?secret)[[:space:]]*[:=][[:space:]]*\$\{[A-Za-z_][A-Za-z0-9_]*:[^}[:space:]]{4,}\}'
  )
  # 설정 파일: 따옴표 없는 key: value 형태도 검사
  case "$f" in
    *.yml|*.yaml|*.properties|*.cnf|*.conf|*.env-example)
      pats+=( -e "^[[:space:]]*(password|passwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|client[_-]?secret)[[:space:]]*[:=][[:space:]]*[^[:space:]#\$\{][^[:space:]]{7,}" )
      ;;
  esac

  hits=$(grep -nEi "${pats[@]}" "$f" 2>/dev/null | grep -v 'hook-allow:' || true)
  [ -z "$hits" ] && continue
  while IFS= read -r h; do
    err "$f:${h%%:*}  시크릿으로 보이는 값이 있습니다 - 환경변수/시크릿 저장소로 옮기세요"
    found=1
  done <<< "$hits"
done

if [ $found -ne 0 ]; then
  info "오탐이면 scripts/.hookignore 에 경로를 추가하거나 해당 줄에 'hook-allow:' 주석을 붙이세요."
  exit 1
fi
exit 0
