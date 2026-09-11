#!/usr/bin/env bash
# SessionStart: 에이전트가 잘못된 환경에서 작업하는 것을 막기 위한 기준 상태 확인.
# 차단하지 않고, 확인한 사실만 컨텍스트로 주입한다. (목표 5초)
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../scripts" && pwd)/hook-lib.sh"
cd "$PROJECT_DIR" || exit 0

mkdir -p "$STATE_DIR"

lines=()
add() { lines+=("$1"); }

add "## 작업 시작 확인 (SessionStart hook)"
add ""

if in_git_repo; then
  branch=$(git branch --show-current 2>/dev/null)
  add "- 브랜치: ${branch:-(detached HEAD)}"
  dirty=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
  if [ "$dirty" -gt 0 ]; then
    add "- 작업 트리: 커밋되지 않은 변경 ${dirty}건 — 새 작업을 시작하기 전에 정리 여부를 확인하세요."
    add '```'
    while IFS= read -r l; do add "$l"; done < <(git status --porcelain 2>/dev/null | head -15)
    add '```'
  else
    add "- 작업 트리: 깨끗함"
  fi
  case "$branch" in
    main|master) add "- ⚠ 기준 브랜치(main)에서 작업 중입니다. 커밋 전에 feature 브랜치를 만드세요 (.claude/skills/git-commit-convention/SKILL.md)." ;;
  esac
else
  add "- ⚠ git 저장소가 아닙니다. pre-commit/pre-push hook 이 동작하지 않습니다."
fi

jv=$(java -version 2>&1 | head -1)
add "- Java: ${jv:-확인 불가} (프로젝트 요구: 21)"

if (exec 3<>/dev/tcp/127.0.0.1/3306) 2>/dev/null; then
  add "- MySQL localhost:3306: 접속 가능 → 리포지토리 테스트(@DataJpaTest) 실행 가능"
else
  add "- MySQL localhost:3306: 접속 불가 → 리포지토리/엔티티 테스트는 실패합니다. 필요하면 \`docker compose up -d db-master db-replica\`"
fi

[ -f "$PROJECT_DIR/.env" ] && add "- .env: 존재" || add "- .env: 없음 (.env-example 참고해서 만들어야 할 수 있음)"

add ""
add "### 이 프로젝트의 hook 규칙"
add "- 파일을 수정하면 PostToolUse hook 이 금지 패턴을 검사합니다: System.out/err.print, printStackTrace, 빈 catch, @Ignore/@Disabled, 자격증명 하드코딩, 의존 방향 역전. 예외가 필요하면 해당 줄에 \`// hook-allow: <사유>\` 를 붙이세요."
add "- 답변을 끝내기 전 Stop hook 이 검증 여부를 확인합니다. 소스를 고쳤다면 이번 세션 안에서 \`./gradlew test\` (또는 compileJava/check)를 실제로 실행해야 종료할 수 있습니다."
add "- 로컬 검사 스크립트는 git hook / CI 와 동일한 것을 씁니다: \`scripts/lint-changed.sh\`, \`scripts/test-affected.sh\`, \`scripts/verify-full.sh\`."

text=$(printf '%s\n' "${lines[@]}")
esc=$(json_escape "$text")
printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$esc"
exit 0
