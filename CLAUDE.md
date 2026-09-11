# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개발 환경
OS - Window 11

## 프로젝트 개요
이 프로젝트는 Spring Boot 기반 이커머스 백엔드입니다 (장바구니/주문/상품/유저).
Java 21, Spring Boot 4.1, Spring Data JPA(Hibernate), MySQL 8.4, 테스트는 Spock 사용

## 문서 구성

상세 내용은 아래 문서로 분리되어 있습니다:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 레이어드 아키텍처, 패키지 구성, 읽기/쓰기 DB 라우팅, 멱등성/락, 인증
- [docs/CODE_STYLE.md](docs/CODE_STYLE.md) — Spotless 포맷터, 로깅 컨벤션
- [docs/TESTING.md](docs/TESTING.md) — Spock 테스트 관례
- [docs/PROJECT_ANALYSIS.md](docs/PROJECT_ANALYSIS.md) — 코드베이스 분석 스냅샷
- [docs/HOOKS.md](docs/HOOKS.md) — 단계별 hook 구성(SessionStart/PostToolUse/pre-commit/pre-push/CI/Stop), 금지 패턴 규칙, 예외 표식 `hook-allow:`
- `.claude/skills/git-commit-convention/SKILL.md` — 브랜치 명명, 커밋 메시지 규칙 (커밋/브랜치 작업 시 참고)
- `.claude/agents/java-spring-reviewer.md` — Java·Spring 구현/리뷰 서브에이전트 규약 (`java-spring-jpa` 스킬 기준 + MyBatis→JPA·Spock→JUnit 전환기 취급 규칙, 권한 경계, 보고 형식)

## 폴더 구조

```
f-market/
├── docker-init/                    # MySQL master/replica 초기화 스크립트, mysql-config/*.cnf
├── docs/
│   ├── ARCHITECTURE.md             # 아키텍처 상세 문서
│   ├── PROJECT_ANALYSIS.md         # 코드베이스 분석 스냅샷
│   ├── CODE_STYLE.md               # 포맷터, 로깅 컨벤션
│   ├── TESTING.md                  # 테스트 관례
│   └── HOOKS.md                    # 단계별 hook 구성
├── src/
│   ├── main/
│   │   ├── java/com/sorryisme/fmarket/
│   │   │   ├── annotation/         # @RequireLogin, @LoginUserId, @Idempotent, @IdempotencyKeyParam
│   │   │   ├── aop/                # AuthenticationAspect, IdempotencyAspect
│   │   │   ├── common/             # AppConstants, GlobalExceptionHandler, SessionManager, dto/ResponseDto
│   │   │   ├── config/             # DataSourceConfiguration, ReplicationRoutingDataSource, WebConfig
│   │   │   ├── controller/         # Cart / Order / Product / User
│   │   │   ├── dto/{request,response}
│   │   │   ├── entity/             # JPA 엔티티 (BaseTimeEntity + Cart/CartDetail/IdempotencyKey/Inventory/MajorCategory/Order/OrderDetail/Product/ProductOption/ProductReview/Store/Subcategory/User)
│   │   │   ├── enums/              # OrderStatus, UserRole
│   │   │   ├── exception/          # DuplicateData / NotFoundData / RequireLogin / UpdateFail
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
│       ├── groovy/com/sorryisme/fmarket/
│       │   ├── entity/             # EntityMappingTest (@DataJpaTest, ddl-auto=validate 로 스키마 일치 검증)
│       │   ├── repository/         # Repository Spock 테스트 (@DataJpaTest, 실제 MySQL)
│       │   ├── service/            # Service Spock 테스트 (Mock 기반)
│       │   └── testUtils/          # DomainFixture
│       ├── java/                   # FmarketApplicationTests (스모크 테스트)
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

## 작업 후 산출물 작업
- 지시한 코드 작업 이후 산출물을 작성하여 DOCS에 추가한다. 해당 작업 내용이 간단한 문서 작업이거나 오타, 변경사항이 적을 경우 따로 산출물로 작성하지 않는다
- 산출물 작성 시 docs/logs/{년월일}/{순서}-{작업제목}.md 형태로 저장한다.
- 기존 로그가 없거나 순서가 없을 경우 1번부터 시작하며, 기존 순서가 있는경우 기존 순서에 1씩 더해 순서에 표기한다 
- 작성 전 반드시 해당 날짜의 docs/logs/{년월일}/ 폴더가 존재하는지 확인하고, 없으면 새로 생성한 뒤 그 안에 추가/이동한다. docs/logs/ 루트에 날짜 파일을 직접 두지 않는다.
