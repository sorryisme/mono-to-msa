# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개발 환경
OS - Window 11

## 프로젝트 개요
이 프로젝트는 Spring Boot 기반 이커머스 백엔드입니다 (장바구니/주문/상품/유저).
Java 21, Spring Boot 4.1, Spring Data JPA(Hibernate), MySQL 8.4, 테스트는 JUnit 5 · Mockito 사용

## 문서 구성

상세 내용은 아래 문서로 분리되어 있습니다:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 레이어드 아키텍처, 패키지 구성, 읽기/쓰기 DB 라우팅, 멱등성/락, 인증
- [docs/API_RESPONSE.md](docs/API_RESPONSE.md) — API 응답 봉투 형식, HTTP 상태와 본문 `code`(ErrorCode) 계약, 예외 핸들러 매핑 (응답/예외를 건드릴 때 반드시 준수)
- [docs/CODE_STYLE.md](docs/CODE_STYLE.md) — Spotless 포맷터, 로깅 컨벤션
- [docs/TESTING.md](docs/TESTING.md) — 테스트 관례 (JUnit 5·Mockito, 슬라이스 테스트 구성)
- [docs/PROJECT_ANALYSIS.md](docs/PROJECT_ANALYSIS.md) — 코드베이스 분석 스냅샷
- [docs/HOOKS.md](docs/HOOKS.md) — 단계별 hook 구성(SessionStart/PostToolUse/pre-commit/pre-push/CI/Stop), 금지 패턴 규칙, 예외 표식 `hook-allow:`
- `.claude/skills/git-commit-convention/SKILL.md` — 브랜치 명명, 커밋 메시지 규칙 (커밋/브랜치 작업 시 참고)
- [.claude/skills/java-spring/SKILL.md](.claude/skills/java-spring/SKILL.md) — Java·Spring 구현/리뷰 공통 기준, Spring 트랜잭션과 DB 공통 규칙
- [.claude/skills/jpa-hibernate/SKILL.md](.claude/skills/jpa-hibernate/SKILL.md) — JPA·Hibernate 엔티티·영속성 컨텍스트·쿼리·배치·검증 기준 (JPA 관련 작업 시 추가 적용)
- `.claude/agents/java-spring-reviewer.md` — Java·Spring 구현/리뷰 서브에이전트 규약 (`java-spring` 공통 기준 + 관련 작업에 `jpa-hibernate` 추가 적용, MyBatis→JPA 전환기 취급 규칙, 권한 경계, 보고 형식)
- `docs/plan/`, `docs/todo/`, `docs/review/`, `docs/logs/` — 작업 문서 흐름 (아래 "작업 문서 흐름" 절 참고)

## 폴더 구조

```
f-market/
├── docker-init/                    # MySQL master/replica 초기화 스크립트, mysql-config/*.cnf
├── docs/
│   ├── ARCHITECTURE.md             # 아키텍처 상세 문서
│   ├── PROJECT_ANALYSIS.md         # 코드베이스 분석 스냅샷
│   ├── API_RESPONSE.md             # API 응답·오류 코드 계약
│   ├── CODE_STYLE.md               # 포맷터, 로깅 컨벤션
│   ├── TESTING.md                  # 테스트 관례
│   ├── HOOKS.md                    # 단계별 hook 구성
│   ├── plan/                       # 사용자가 미리 작성해 두는 작업 지시서 (실행 입력)
│   │                               #   {YYMMDD}-{주제}.md
│   ├── todo/                       # 지금 하지 않고 미뤄 둔 후속 작업 (보류 목록)
│   │   └── BACKLOG.md              #   기본 누적 파일. 주제가 커지면 {YYMMDD}-{주제}.md 로 분리
│   ├── review/                     # 코드 리뷰 결과 기록 ({YYMMDD}-{리뷰대상}.md)
│   └── logs/{년월일}/              # 완료된 작업 산출물 로그 ({순서}-{작업제목}.md)
├── src/
│   ├── main/
│   │   ├── java/com/sorryisme/fmarket/
│   │   │   ├── annotation/         # @RequireLogin, @LoginUserId, @Idempotent, @IdempotencyKeyParam
│   │   │   ├── aop/                # AuthenticationAspect, IdempotencyAspect
│   │   │   ├── common/             # AppConstants, ErrorCode, SessionManager, PageableSupport, dto/ResponseDto·FieldErrorDto
│   │   │   ├── config/             # DataSourceConfiguration, ReplicationRoutingDataSource, WebConfig
│   │   │   ├── controller/         # Cart / Order / Product / User
│   │   │   ├── dto/{request,response}
│   │   │   ├── entity/             # JPA 엔티티 (BaseTimeEntity + Cart/CartDetail/IdempotencyKey/Inventory/MajorCategory/Order/OrderDetail/Product/ProductOption/ProductReview/Store/Subcategory/User)
│   │   │   ├── enums/              # OrderStatus, UserRole
│   │   │   ├── exception/          # BusinessException(ErrorCode 보유), GlobalExceptionHandler
│   │   │   ├── filter/             # MDCLoggingFilter
│   │   │   ├── repository/         # Spring Data JPA 리포지토리 + ProductSpecification
│   │   │   ├── resolver/           # LoginUserIdResolver
│   │   │   ├── service/            # Cart / Order / Product / User
│   │   │   └── utils/              # PasswordCipher
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── schema.sql / data.sql
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/com/sorryisme/fmarket/
│       │   ├── FmarketApplicationTests    # 스모크 테스트 (@SpringBootTest)
│       │   ├── common/             # PageableSupportTest (단위)
│       │   ├── controller/         # ControllerResponseContractTest (@WebMvcTest, 봉투·201·401 계약)
│       │   ├── entity/             # EntityMappingTest (@DataJpaTest, ddl-auto=validate 로 스키마 일치 검증)
│       │   ├── exception/          # GlobalExceptionHandlerTest (@WebMvcTest, 오류 코드 → HTTP 상태 계약)
│       │   ├── repository/         # Repository 테스트 (@DataJpaTest, 실제 MySQL)
│       │   ├── service/            # Service 테스트 (Mockito Mock 기반)
│       │   └── testUtils/          # DomainFixture
│       ├── https/                  # products.http
│       └── resources/application.yml
├── scripts/                        # 검증 스크립트 (hook / CI 공용) + wait-for-it.sh
├── .githooks/                      # pre-commit, pre-push (core.hooksPath 로 연결)
├── .claude/                        # agents/, hooks/, settings.json, skills/
├── docker-compose.yml
├── Dockerfile
└── build.gradle / settings.gradle
```

## Git / 커밋 컨벤션

브랜치 명명, Conventional Commits 스타일 커밋 메시지 규칙은 `.claude/skills/git-commit-convention/SKILL.md` 스킬로 분리했습니다 — 커밋/브랜치 작업 시 해당 스킬을 참고하세요.

## 작업 문서 흐름 (plan / todo / review / logs)

`docs/` 아래 네 폴더는 작업 단계별 입력·출력을 담는다. 문서를 만들거나 옮기기 전에 해당 폴더가 있는지 확인하고, 없으면 생성한 뒤 그 안에 둔다. 네 폴더는 모두 `.gitkeep` 으로 추적되므로 비어 있어도 clone 한 환경에 그대로 존재한다.

**plan 은 선행 조건이 아니다.** 일반적인 흐름은 `plan → 작업 → logs` 이고 거기서 나온 보류 항목이 todo, 리뷰 결과가 review 로 가지만, plan 문서 없이 대화로 바로 지시받은 작업에서도 todo 와 review 는 동일하게 작성한다. 즉 todo·review 작성 여부는 "plan 이 있었는지"가 아니라 "미룬 항목이 생겼는지 / 리뷰를 수행했는지"로 판단한다.

### docs/plan/ — 작업 지시서 (입력)
- 사용자가 지시사항이 많을 때 미리 작성해 두는 문서. Claude 는 이 문서를 읽고 그대로 실행한다.
- 파일명: `docs/plan/{YYMMDD}-{주제}.md` (예: `260912-lock.md`)
- "plan 대로 진행해줘", "docs/plan 확인해줘" 같은 요청을 받으면 먼저 이 폴더의 해당 문서를 읽어 작업 범위를 확정한다. 대상이 여러 개면 어떤 것을 실행할지 확인한다.
- plan 문서 본문은 그대로 두고, 실행을 마치면 문서 맨 위에 `> 상태: 완료 (docs/logs/{년월일}/{순서}-{작업제목}.md)` 한 줄을 추가해 산출물과 연결한다. 일부만 처리했다면 `> 상태: 부분 완료 ({로그 경로}) — 잔여 항목은 docs/todo/BACKLOG.md` 로 적는다.
- plan 항목 중 실행하지 않고 미룬 것이 있으면 `docs/todo/` 로 옮겨 적는다.

### docs/todo/ — 보류 작업
- 지금 하지 않고 미뤄 둔 항목을 모은다. plan 에서 남은 항목, 작업 중 발견한 개선점, 리뷰에서 보류로 판단한 지적 등 **출처와 무관하게** 여기로 모은다.
- 기본은 누적 파일 한 장: `docs/todo/BACKLOG.md`. 항목이 흩어지지 않아 관리가 쉽다.
- 한 주제의 보류 항목이 많아 BACKLOG 한 장으로 읽기 어려워지면 그때만 `docs/todo/{YYMMDD}-{주제}.md` 로 분리하고, BACKLOG.md 에 그 파일로 가는 한 줄 링크를 남긴다.
- 각 항목은 체크박스로 적고 배경·보류 사유·관련 경로를 함께 남긴다.
  - `- [ ] {할 일} — 배경: {왜 필요한가} / 보류 사유: {지금 안 하는 이유} / 관련: {파일·문서 경로}`
- 나중에 실제로 처리하면 `- [x]` 로 바꾸고 완료 산출물 로그 경로를 덧붙인다. 항목을 지우지 않고 남겨 이력을 유지한다.
- todo 항목을 임의로 먼저 처리하지 않는다. 처리 여부는 사용자에게 확인한다.

### docs/review/ — 리뷰 결과
- 코드 리뷰(`/code-review`, `java-spring-reviewer` 서브에이전트, 사용자 리뷰 지시 등) 결과를 저장한다. plan 기반 작업이 아니어도, 대화로 바로 받은 리뷰 요청이어도 동일하게 남긴다.
- 파일명: `docs/review/{YYMMDD}-{리뷰대상}.md` (plan/todo 와 통일한 평평한 구조. 같은 날 같은 대상을 두 번 리뷰하면 뒤에 `-2` 를 붙인다)
- 포함할 내용: 리뷰 대상(브랜치·커밋·PR·파일 범위), 리뷰 수행 주체, 심각도별 발견 사항, 항목별 조치 결과(수정함 / 보류 / 해당 없음).
- 보류로 판단한 지적은 반드시 `docs/todo/BACKLOG.md` 에도 항목으로 남기고, 그 항목의 `관련:` 에 리뷰 문서 경로를 적는다.

### docs/logs/ — 완료 산출물
- 아래 "작업 후 산출물 작업" 규칙을 따른다.

## 작업 후 산출물 작업
- 지시한 코드 작업 이후 산출물을 작성하여 DOCS에 추가한다. 해당 작업 내용이 간단한 문서 작업이거나 오타, 변경사항이 적을 경우 따로 산출물로 작성하지 않는다
- 산출물 작성 시 docs/logs/{년월일}/{순서}-{작업제목}.md 형태로 저장한다.
- 기존 로그가 없거나 순서가 없을 경우 1번부터 시작하며, 기존 순서가 있는경우 기존 순서에 1씩 더해 순서에 표기한다 
- 작성 전 반드시 해당 날짜의 docs/logs/{년월일}/ 폴더가 존재하는지 확인하고, 없으면 새로 생성한 뒤 그 안에 추가/이동한다. docs/logs/ 루트에 날짜 파일을 직접 두지 않는다.
