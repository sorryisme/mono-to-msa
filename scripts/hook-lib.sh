#!/usr/bin/env bash
# 모든 hook 스크립트가 공유하는 헬퍼. 직접 실행하지 말고 source 해서 쓴다.

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
STATE_DIR="$PROJECT_DIR/.claude/state"

C_RED=$'\033[31m'; C_YELLOW=$'\033[33m'; C_GREEN=$'\033[32m'; C_DIM=$'\033[2m'; C_OFF=$'\033[0m'

err()  { printf '%s✗ %s%s\n' "$C_RED" "$*" "$C_OFF" >&2; }
warn() { printf '%s! %s%s\n' "$C_YELLOW" "$*" "$C_OFF" >&2; }
ok()   { printf '%s✓ %s%s\n' "$C_GREEN" "$*" "$C_OFF" >&2; }
info() { printf '%s  %s%s\n' "$C_DIM" "$*" "$C_OFF" >&2; }

# stdin JSON에서 문자열 필드 하나를 꺼낸다 (jq 없이 동작해야 함).
# 사용: json_str "<원본 JSON>" file_path
json_str() {
  printf '%s' "$1" \
    | sed -n 's/.*"'"$2"'"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p' \
    | head -1 \
    | sed -e 's|\\\\|/|g' -e 's|\\"|"|g'
}

# JSON 에서 파일 경로를 꺼낸다. Windows 역슬래시는 Git Bash 가 이해하는 / 로 통일한다.
json_path() {
  json_str "$1" "$2" | tr '\\' '/'
}

# 문자열을 JSON 문자열 리터럴 내부에 넣을 수 있게 이스케이프한다.
json_escape() {
  printf '%s' "$1" | awk '{gsub(/\\/,"\\\\"); gsub(/"/,"\\\""); printf "%s\\n", $0}'
}

# stdin JSON에서 boolean 필드가 true 인지 확인한다.
json_true() {
  printf '%s' "$1" | grep -q "\"$2\"[[:space:]]*:[[:space:]]*true"
}

# 검증 완료 마커 경로. 세션마다 따로 둔다.
verify_marker() {
  printf '%s/verified-%s' "$STATE_DIR" "${1:-nosession}"
}

# gradlew 래퍼 경로 (Git Bash 기준)
gradlew() {
  ( cd "$PROJECT_DIR" && ./gradlew "$@" )
}

in_git_repo() {
  git -C "$PROJECT_DIR" rev-parse --git-dir >/dev/null 2>&1
}

# 검증이 필요한(=커밋되지 않은) 소스 변경이 있는지
has_source_changes() {
  in_git_repo || return 1
  [ -n "$(git -C "$PROJECT_DIR" status --porcelain -- src build.gradle settings.gradle 2>/dev/null)" ]
}
