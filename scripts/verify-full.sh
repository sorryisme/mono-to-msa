#!/usr/bin/env bash
# CI 단계 전체 검증. 로컬 hook 이 우회되었더라도 여기서는 반드시 강제된다.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR"

rc=0
run() { printf '\n%s▸ %s%s\n' "$C_DIM" "$*" "$C_OFF" >&2; "$@" || rc=1; }

run bash scripts/guard-scan.sh $(find src -name '*.java' -o -name '*.groovy')
run bash scripts/arch-check.sh
run bash scripts/secret-scan.sh $(git ls-files 2>/dev/null || find src -type f)
run ./gradlew clean check

[ $rc -eq 0 ] && ok "전체 검증 통과" || err "전체 검증 실패"
exit $rc
