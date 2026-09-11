# Hook 구성

빠르고 결정적인 검사는 앞 단계에, 느린 검사는 뒤 단계에 둡니다.
모든 단계는 **같은 스크립트**(`scripts/`)를 호출하므로 검증 로직이 한 곳에만 존재합니다.

| 시점 | 구현 | 검사 | 실측 |
|---|---|---|---|
| 작업 시작 | Claude Code `SessionStart` | 브랜치·작업 트리·Java·MySQL·`.env` 확인 | ~1초 |
| 파일 수정 후 | Claude Code `PostToolUse(Write\|Edit)` | 금지 패턴 + 의존 방향 | <0.1초 |
| 커밋 전 | git `pre-commit` | 포맷·금지 패턴·시크릿·의존 방향·스키마 | ~3초 |
| 푸시 전 | git `pre-push` | 컴파일·포맷·테스트 | ~10초 |
| PR/CI | `.github/workflows/verify.yml` | 위 전체 + `gradlew clean check` | 제한 없음 |
| 작업 종료 전 | Claude Code `Stop` | 검증 실행 여부 확인 후 차단 | ~0.2초 |

## 스크립트

| 스크립트 | 역할 | 호출하는 곳 |
|---|---|---|
| `scripts/hook-lib.sh` | 공통 헬퍼 (JSON 파싱, 상태 마커) | 전부 |
| `scripts/guard-scan.sh` | 금지 패턴 (`System.out`, 빈 catch, 테스트 skip, 자격증명) | PostToolUse, pre-commit, CI |
| `scripts/arch-check.sh` | 의존 방향 (controller → service → mapper) | PostToolUse, pre-commit, CI |
| `scripts/secret-scan.sh` | 시크릿·1MB 초과 파일 | pre-commit, CI |
| `scripts/lint-changed.sh` | pre-commit 묶음 | pre-commit |
| `scripts/test-affected.sh` | pre-push 묶음 | pre-push, Stop 검증 |
| `scripts/verify-full.sh` | 전체 검증 | CI |

수동 실행:

```bash
bash scripts/lint-changed.sh --staged   # 커밋 전 검사
bash scripts/test-affected.sh           # 푸시 전 검사
bash scripts/verify-full.sh             # CI 와 동일한 전체 검증
```

## 검사 규칙

`guard-scan.sh` — `.java` / `.groovy` 대상

차단(ERROR):
- `System.out.print` / `System.err.print` → SLF4J 사용 ([CODE_STYLE.md](CODE_STYLE.md))
- `printStackTrace()` → `log.error(msg, e)`
- `@Ignore` / `@Disabled` / `@IgnoreRest` / `@PendingFeature` → 테스트 약화 금지
- 빈 `catch` 블록
- 자격증명 하드코딩 (`src/test/**` 픽스처는 제외), AWS 키, 개인 키

경고(WARN, 차단하지 않음):
- `TODO` / `FIXME` / `XXX`
- 외부 URL 하드코딩

`arch-check.sh` — 금지된 import 방향
- `service` → `controller`
- `mapper` → `controller` / `service`
- `domain`, `dto` → 상위 레이어
- `controller` → `mapper` (service 를 건너뜀)

## 예외 처리

한 줄 예외는 해당 줄에 사유와 함께 표식을 답니다.

```java
log.debug("payload={}", raw); // hook-allow: 로컬 디버깅 전용, #123 에서 제거
```

시크릿 검사에서 파일 단위로 빼려면 `scripts/.hookignore` 에 경로(glob)를 추가합니다.
**실제 시크릿이 아님을 확인했을 때만** 추가하세요.

## Stop hook (작업 종료 전 검증)

커밋되지 않은 소스 변경이 있는데 이번 세션에서 `./gradlew test|check|build|compileJava`
(또는 `scripts/test-affected.sh`)를 한 번도 실행하지 않았거나, 마지막 검증 이후에 소스가
다시 바뀌었으면 **답변 종료를 차단**하고 다음 형식의 보고를 요구합니다.

```yaml
verification:
  - criterion: 동일 취소 요청에서 재고가 중복 증가하지 않는다
    command: ./gradlew test --tests '*OrderServiceTest'
    result: passed

unverified:
  - 매퍼 테스트 - 로컬 MySQL 미기동
```

빌드가 실패한 명령(`BUILD FAILED`)은 검증으로 인정하지 않습니다.
상태 마커는 세션별로 `.claude/state/verified-<session_id>` 에 저장되며 git 에서 제외됩니다.

## 활성화

git hook 은 `core.hooksPath` 로 연결되어 있습니다. 새로 clone 했다면 한 번 실행하세요.

```bash
git config core.hooksPath .githooks
```

Claude Code hook 은 `.claude/settings.json` 에 정의되어 있고, `/hooks` 로 확인·비활성화할 수 있습니다.

로컬 hook 은 `--no-verify` 로 우회할 수 있지만 CI 는 우회할 수 없습니다.

## DB 자격증명

`application.yml` 은 값을 직접 갖지 않고 환경변수를 읽으며, 로컬 `docker-compose` 기준
기본값만 함께 둡니다. 우선순위는 **환경변수 > 기본값** 입니다.

| 변수 | 기본값 | 쓰이는 곳 |
|---|---|---|
| `MYSQL_USER` / `MYSQL_PASSWORD` | `sorry` / 로컬 DB 비밀번호 | main·test 양쪽 |
| `MYSQL_DATABASE` | `fmarket` | 양쪽 |
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | test |
| `MYSQL_MASTER_HOST` / `MYSQL_REPLICA_HOST` | `db-master` / `db-replica` | main |

- 실제 값은 `.env` 에만 둡니다 (gitignore 대상, `.env-example` 참고).
- `docker-compose` 는 `env_file: .env` 로 `java-app` 컨테이너에 주입합니다.
- CI 는 잡마다 임시 비밀번호를 생성해 쓰므로 워크플로 파일과 GitHub Secrets 에 자격증명이 없습니다.
- 다른 계정으로 테스트하려면: `MYSQL_USER=x MYSQL_PASSWORD=y ./gradlew test`

`secret-scan.sh` 는 `${...}` 형태를 시크릿으로 보지 않으므로, 누군가 값을 다시
하드코딩하면 pre-commit 단계에서 걸립니다.

## 알려진 환경 이슈

`./gradlew test` 는 위 설정으로 실제 MySQL 에 붙습니다. 계정이 맞지 않으면 매퍼 테스트와
스모크 테스트가 함께 실패합니다 (`Access denied for user 'sorry'@'localhost'`).
`test-affected.sh` 가 이 원인을 감지해 해결 방법을 출력합니다. DB 가 없으면 `*ServiceTest`
만 실행되고 나머지는 "미검증"으로 보고됩니다.

스모크 테스트(`FmarketApplicationTests`)는 별개로 `jdbcUrl is required` 로 실패합니다 —
`DataSourceConfiguration` 이 기대하는 `source`/`replica` 설정이 테스트 프로파일에 없기 때문이며,
자격증명과 무관한 기존 이슈입니다.
