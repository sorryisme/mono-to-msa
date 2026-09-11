#!/usr/bin/env bash
# Stop: 소스를 고쳐놓고 검증 없이 답변을 끝내는 것을 막는다.
#
# 차단 조건: 커밋되지 않은 소스 변경이 있는데
#   (a) 이번 세션에서 gradlew test/check/build/compileJava 를 한 번도 돌리지 않았거나
#   (b) 마지막 검증 이후에 소스가 또 바뀐 경우
# stop_hook_active 가 true 면 무한 루프를 피하기 위해 통과시킨다.
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../scripts" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR" || exit 0

INPUT=$(cat)
json_true "$INPUT" stop_hook_active && exit 0
in_git_repo || exit 0
has_source_changes || exit 0

sid=$(json_str "$INPUT" session_id)
marker=$(verify_marker "$sid")

reason=""
if [ ! -f "$marker" ]; then
  reason="소스를 수정했지만 이번 세션에서 검증 명령을 한 번도 실행하지 않았습니다."
else
  stale=$(find src build.gradle -newer "$marker" -type f \
            \( -name '*.java' -o -name '*.groovy' -o -name '*.xml' -o -name '*.sql' -o -name '*.yml' -o -name 'build.gradle' \) \
            2>/dev/null | head -5)
  if [ -n "$stale" ]; then
    reason="마지막 검증 이후에 다음 파일이 다시 수정되었습니다: $(printf '%s' "$stale" | tr '\n' ' ')"
  fi
fi

[ -z "$reason" ] && exit 0

msg="$reason

답변을 끝내기 전에 다음을 수행하세요 (before-complete 검증):
1. 사용자의 원래 요구사항을 다시 읽는다.
2. git diff 로 실제 변경 내용을 검토한다.
3. 변경과 관련된 테스트를 실행한다 — 예: ./gradlew test --tests '*OrderServiceTest'
4. 필요한 전체 검사를 실행한다 — bash scripts/test-affected.sh
5. 테스트를 삭제하거나 약화시키지 않았는지 확인한다 (@Ignore/@Disabled, assertion 제거).
6. 아래 형식으로 완료 조건별 증거를 보고한다:

verification:
  - criterion: <완료 조건>
    command: <실제로 실행한 명령>
    result: passed | failed

unverified:
  - <실행하지 못한 검사와 그 이유>

7. 남은 위험과 실행하지 못한 검사를 명시한다.

검증 명령을 실행하면 이 차단은 자동으로 해제됩니다. 검증이 불가능한 상황이면(예: DB 미기동) 그 사실을 unverified 에 적고 사용자에게 알리세요."

printf '{"decision":"block","reason":"%s"}\n' "$(json_escape "$msg")"
exit 0
