# 코드 스타일 & 로깅 컨벤션

[CLAUDE.md](../CLAUDE.md)에서 이관된 문서입니다.

## 코드 스타일 자동화

- **포맷터**: Spotless(`com.diffplug.spotless`) + `googleJavaFormat()` — `build.gradle`에 설정됨. import 순서, 줄바꿈, 들여쓰기(Google Java Style, 2-space)를 도구가 강제하며 사람이 리뷰에서 따로 지적하지 않습니다.
  - `./gradlew spotlessApply` — 포맷 자동 적용
  - `./gradlew spotlessCheck` — 포맷 위반 여부만 검사 (수정하지 않음)
  - Spotless 플러그인이 기본적으로 `check` 태스크에 `spotlessCheck`를 연결하므로, `./gradlew build`/`./gradlew check` 실행 시 포맷이 깨져 있으면 빌드가 실패합니다. 별도 CI 워크플로우 없이 로컬 빌드에서 강제되는 방식입니다.
  - 적용 대상은 `src/**/*.java`(Groovy 테스트 코드는 대상 아님).
  - Java 줄바꿈은 `.gitattributes`의 `*.java text eol=lf`로 LF를 강제합니다. Spotless도 이 Git 속성을 따르므로 Windows와 Linux CI에서 같은 포맷을 사용합니다.
- **정적 분석(Checkstyle, SonarLint/SonarQube)**: 아직 도입하지 않음. 필요해지면 추가 검토.

## 로깅 컨벤션

- `System.out.println` 금지, SLF4J(`@Slf4j`)만 사용
- 로그 레벨 기준: `ERROR`(즉시 대응 필요), `WARN`(예외적 상황), `INFO`(주요 비즈니스 이벤트), `DEBUG`(개발 디버깅용)
- 민감정보(비밀번호, 토큰, 개인정보)는 로그에 남기지 않음
