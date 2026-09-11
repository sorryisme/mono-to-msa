#!/usr/bin/env bash
# PostToolUse(Write|Edit): 방금 수정한 파일 하나만 검사한다. gradle 을 띄우지 않으므로 즉시 끝난다.
# 종료 코드 2 = 차단 오류 → stderr 내용이 Claude 에게 되돌아간다.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../scripts" && pwd)/hook-lib.sh"

INPUT=$(cat)
f=$(json_path "$INPUT" file_path)
[ -n "$f" ] || exit 0
[ -f "$f" ] || exit 0

case "$f" in
  *.java|*.groovy) ;;
  *) exit 0 ;;
esac

rc=0
bash "$PROJECT_DIR/scripts/guard-scan.sh" "$f" || rc=2
bash "$PROJECT_DIR/scripts/arch-check.sh" "$f" || rc=2
exit $rc
