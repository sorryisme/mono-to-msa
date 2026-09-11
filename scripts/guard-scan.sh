#!/usr/bin/env bash
# Java/Groovy 파일에서 금지 패턴을 검사한다. gradle을 띄우지 않으므로 매우 빠르다.
#
#   사용:   scripts/guard-scan.sh <파일> [<파일>...]
#   종료:   0 = 통과(경고는 있을 수 있음), 1 = 금지 패턴 발견
#   예외:   해당 줄에 "hook-allow:" 주석이 있으면 건너뛴다.
#           예) log.debug(...); // hook-allow: 로컬 디버깅 전용, #123 에서 제거 예정
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/hook-lib.sh"

targets=()
for f in "$@"; do
  case "$f" in
    *.java|*.groovy) [ -f "$f" ] && targets+=("$f") ;;
  esac
done
[ ${#targets[@]} -eq 0 ] && exit 0

found=0
for f in "${targets[@]}"; do
  out=$(awk -v file="$f" '
    function report(kind, msg) { printf "%s|%s:%d|%s\n", kind, file, FNR, msg }
    # 예외 표식이 있는 줄은 통째로 건너뛴다
    /hook-allow:/ { next }
    # 주석 줄은 TODO 검사만 받는다
    {
      line = $0
      is_test = (file ~ /src\/test\//)

      if (line ~ /System\.(out|err)\.print/)
        report("ERROR", "System.out/err.print 금지 - SLF4J(@Slf4j) 로거를 쓰세요 (docs/CODE_STYLE.md)")
      if (line ~ /\.printStackTrace[[:space:]]*\(/)
        report("ERROR", "printStackTrace() 금지 - log.error(msg, e) 로 남기세요")
      if (line ~ /@(Ignore|Disabled|IgnoreRest|PendingFeature)\>/)
        report("ERROR", "테스트 skip/disable 애노테이션 - 테스트를 약화시키지 말고 고치거나 삭제 사유를 남기세요")
      if (line ~ /catch[[:space:]]*\(.*\)[[:space:]]*\{[[:space:]]*\}[[:space:]]*$/)
        report("ERROR", "빈 catch 블록 - 최소한 로그를 남기거나 예외를 전파하세요")

      # catch (...) { 다음 줄이 바로 } 인 경우
      if (pending_catch && line ~ /^[[:space:]]*\}/)
        report("ERROR", "빈 catch 블록 - 최소한 로그를 남기거나 예외를 전파하세요")
      pending_catch = (line ~ /catch[[:space:]]*\(.*\)[[:space:]]*\{[[:space:]]*$/)

      if (line ~ /AKIA[0-9A-Z]{16}/)
        report("ERROR", "AWS 액세스 키로 보이는 값이 하드코딩되어 있습니다")
      if (line ~ /-----BEGIN[A-Z ]*PRIVATE KEY-----/)
        report("ERROR", "개인 키가 소스에 포함되어 있습니다")

      # 자격증명 하드코딩 (테스트 픽스처는 제외)
      if (!is_test) {
        low = tolower(line)
        if (low ~ /(password|passwd|secret|apikey|api_key|accesskey|access_key|authtoken|auth_token)[[:space:]]*=[[:space:]]*"[^"]{4,}"/)
          report("ERROR", "자격증명이 하드코딩된 것으로 보입니다 - 설정(application.yml)이나 환경변수로 옮기세요")
      }

      if (line ~ /(TODO|FIXME|XXX)/)
        report("WARN", "TODO/FIXME - 임시 우회라면 이슈 번호를 함께 남기세요")
      if (line ~ /https?:\/\// && line !~ /(localhost|127\.0\.0\.1|example\.(com|org)|w3\.org|mybatis\.org|springframework\.org|apache\.org|@see|@link)/)
        report("WARN", "외부 URL 하드코딩 - 설정으로 분리할 수 있는지 확인하세요")
    }
  ' "$f")
  [ -z "$out" ] && continue
  while IFS='|' read -r kind loc msg; do
    if [ "$kind" = ERROR ]; then err "$loc  $msg"; found=1; else warn "$loc  $msg"; fi
  done <<< "$out"
done

if [ $found -ne 0 ]; then
  info "예외가 필요하면 해당 줄에 '// hook-allow: <사유>' 주석을 붙이세요."
  exit 1
fi
exit 0
