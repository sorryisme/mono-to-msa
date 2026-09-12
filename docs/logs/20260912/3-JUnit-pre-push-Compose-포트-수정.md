# JUnit pre-push 및 Compose 레플리카 포트 수정

- 작업일: 2026-09-12
- 브랜치: `refactor/spock-to-junit`
- 범위: pre-push 컴파일 태스크, Java 앱의 레플리카 준비 확인, 관련 문서

## 배경

Spock 제거 후에도 `scripts/test-affected.sh` 가 삭제된 `compileTestGroovy` 태스크를 호출해
pre-push 검증이 실패할 수 있었다. 또한 Java 앱 컨테이너가 Compose 네트워크에서 레플리카의
호스트 공개 포트인 3307로 접속을 기다리고 있어, 실제 컨테이너 포트 3306과 일치하지 않았다.

## 변경 내용

- pre-push 컴파일 태스크를 `compileJava compileTestJava` 로 변경했다.
- `java-app` 의 준비 확인 대상을 `db-replica:3306` 으로 변경했다.
- `PROJECT_ANALYSIS.md` 와 `ARCHITECTURE.md` 에 호스트 공개 포트와 Compose 내부 포트를 구분해 기록했다.
- 검증 자동화 문서에서 git hook, Claude Code hook, CI가 실제로 호출하는 스크립트를 구분했다.

## 검증

- `.\\gradlew.bat compileJava compileTestJava` — 성공 (`BUILD SUCCESSFUL`)
- `git diff --check` — 통과
- 변경 파일에서 `compileTestGroovy`, `db-replica:3307` 잔존 여부 검색 — 없음
- `docker compose config --quiet` — 통과 (Docker CLI 사용 가능한 세션에서 재실행)
- `bash scripts/test-affected.sh` — 3단계 모두 통과 (컴파일 / spotlessCheck / 전체 테스트).
  `compileTestGroovy` 수정이 실제로 동작하는지 확인한 것이 이 변경의 핵심 검증이다
- `./gradlew clean test` — 테스트 클래스 15개 / 90개 전부 통과 (failures 0, errors 0, skipped 0)

`docker compose up` 으로 실제 기동해 레플리카 대기가 통과하는지는 확인하지 않았다 —
Compose 정의의 문법 검증과 포트 값 일치까지만 확인한 상태다.
