#!/usr/bin/env bash
# 레이어드 아키텍처의 의존 방향을 검사한다: controller -> service -> mapper -> (domain/dto)
#
#   사용:   scripts/arch-check.sh <파일> [<파일>...]   (인자 없으면 src/main 전체)
#   종료:   0 = 통과, 1 = 금지된 의존 방향 발견
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"

BASE='com\.sorryisme\.fmarket'

files=()
if [ $# -gt 0 ]; then
  for f in "$@"; do case "$f" in *.java) [ -f "$f" ] && files+=("$f") ;; esac; done
else
  while IFS= read -r f; do files+=("$f"); done < <(find "$PROJECT_DIR/src/main/java" -name '*.java')
fi
[ ${#files[@]} -eq 0 ] && exit 0

found=0
check() { # $1=경로패턴 $2=금지 import 패턴 $3=설명
  for f in "${files[@]}"; do
    case "$f" in
      *"$1"*) ;;
      *) continue ;;
    esac
    hits=$(grep -nE "^import[[:space:]]+(static[[:space:]]+)?${BASE}\.($2)\." "$f" || true)
    [ -z "$hits" ] && continue
    while IFS= read -r h; do
      err "${f}:${h%%:*}  $3"
      found=1
    done <<< "$hits"
  done
}

check "/service/"    "controller"                  "service 는 controller 를 참조할 수 없습니다 (의존 방향 역전)"
check "/mapper/"     "controller|service"          "mapper 는 controller/service 를 참조할 수 없습니다 (의존 방향 역전)"
check "/domain/"     "controller|service|mapper"   "domain 은 상위 레이어를 참조할 수 없습니다 (단순 데이터 홀더 유지)"
check "/dto/"        "controller|service|mapper"   "dto 는 상위 레이어를 참조할 수 없습니다"

# 컨트롤러가 매퍼를 직접 호출하면 service 레이어를 건너뛰는 것
check "/controller/" "mapper"                      "controller 가 mapper 를 직접 참조합니다 - service 를 거치세요"

[ $found -ne 0 ] && exit 1
exit 0
