#!/usr/bin/env bash
# PostToolUse(Bash): 이번 세션에서 실제로 검증 명령을 돌렸는지 기록한다.
# Stop hook 이 이 마커를 보고 종료 차단 여부를 정한다.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../scripts" && pwd)/hook-lib.sh"

INPUT=$(cat)
cmd=$(json_str "$INPUT" command)
[ -n "$cmd" ] || exit 0

# 검증으로 인정하는 명령
case "$cmd" in
  *gradlew*test*|*gradlew*check*|*gradlew*build*|*gradlew*compileJava*|*test-affected.sh*|*verify-full.sh*) ;;
  *) exit 0 ;;
esac

# 빌드가 깨졌으면 검증으로 치지 않는다
if printf '%s' "$INPUT" | grep -qE 'BUILD FAILED|FAILURE: Build|Execution failed for task'; then
  exit 0
fi

sid=$(json_str "$INPUT" session_id)
mkdir -p "$STATE_DIR"
marker=$(verify_marker "$sid")
printf '%s\n%s\n' "$(date -Iseconds)" "$cmd" > "$marker"
exit 0
