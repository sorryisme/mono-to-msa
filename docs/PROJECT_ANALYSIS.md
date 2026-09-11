# F-Market 프로젝트 분석

> 최초 작성일: 2026-09-10 / 갱신일: 2026-09-11
> 분석 범위: **로컬 프로젝트 코드베이스만 기준** (GitHub 이슈/PR/브랜치 등 원격 저장소 정보는 배제)

## 1. 프로젝트 개요

- **설명**: G마켓을 레퍼런스로 한 기본 이커머스 플랫폼 (README 기준)
- **핵심 기능**: 구매자 - 장바구니/주문/주문내역 확인, 판매자 - 상품등록/판매관리/재고관리
- **기술 스택**: Java 21, Spring Boot 3.3.5, MySQL 8.4(Docker), MyBatis, Spock, Docker/Docker Compose (2026-09-11: Java 17 → 21 마이그레이션 완료, [[MIGRATION_PLAN|docs/MIGRATION_PLAN.md]] 1번 항목 참고)
- **주요 런타임 의존성**(`build.gradle`): `spring-boot-starter-web`, `mybatis-spring-boot-starter`, `spring-boot-starter-validation`, `spring-security-crypto`(비밀번호 암호화), `spring-boot-starter-aop`, `spring-data-commons`(페이지네이션), `mysql-connector-j`, `h2`(테스트용)
- **참고**: 현재 로컬 디렉터리는 `.git`이 없는 비-git 상태로, 커밋 이력이나 원격과의 diff를 로컬에서 추적할 수 없음

## 2. 코드베이스 구조 (레이어드 아키텍처)

```
src/main/java/com/sorryisme/fmarket/
├── annotation/     - @LoginUserId, @RequireLogin, @Idempotent, @IdempotencyKeyParam
├── aop/            - AuthenticationAspect, IdempotencyAspect
├── common/         - AppConstants, GlobalExceptionHandler, SessionManager, ResponseDto
├── config/         - DataSourceConfiguration, ReplicationRoutingDataSource, WebConfig
├── controller/     - Cart / Order / Product / User
├── domain/         - Cart, CartDetail, Inventory, Order, OrderDetail, Product,
│                     ProductOption, ProductReview, Store, User
├── dto/{request,response}
├── enums/          - OrderStatus
├── exception/      - DuplicateData / NotFoundData / RequireLogin / UpdateFail
├── filter/         - MDCLoggingFilter
├── mapper/         - MyBatis 매퍼 인터페이스 (Cart/Idempotency/Inventory/MajorCategory/Order/Product/User)
├── resolver/       - LoginUserIdResolver (커스텀 아규먼트 리졸버)
├── service/        - Cart / Order / Product / User
└── utils/          - PasswordCipher
```

- **ORM**: MyBatis(JPA 미사용) — `src/main/resources/mapper/*.xml`에 SQL 직접 관리(Cart/Inventory/MajorCategory/Order/Product/User). `IdempotencyKeyMapper`는 `@Select` 애노테이션 기반으로 별도 XML 없음
- **인증**: 세션 기반(`SessionManager`) + 커스텀 애노테이션(`@RequireLogin`, `@LoginUserId`) + `AuthenticationAspect`(AOP)로 컨트롤러 진입 시 검증
- **테스트**: Spock(Groovy)으로 Mapper 7종·Service 4종 테스트(`src/test/groovy`), 순수 JUnit은 스모크 테스트(`FmarketApplicationTests`) 1개뿐. `DomainFixture`로 테스트 데이터 생성 공통화
- **결제 기능 없음**: 컨트롤러/서비스/도메인 어디에도 결제 관련 코드가 없음 — README의 "결제" 언급과 달리 실제 구현 범위는 주문 생성/조회/취소까지

## 3. 코드에서 확인한 주요 기술적 구현

### 3.1 주문 생성 동시성 제어
- `@Idempotent` + `IdempotencyAspect`(`aop/IdempotencyAspect.java`): 요청 파라미터 중 `@IdempotencyKeyParam`이 붙은 값(36자 UUID 문자열)을 받아 `idempotency_keys` 테이블에 `INSERT ... FOR UPDATE` 후 존재 여부로 중복 요청 여부를 판단 — 위반 시 `DuplicateDataException`
- 재고 조회는 `InventoryMapper.findStockQuantityForUpdate`가 대상 `product_option_id` 목록에 대해 `SELECT ... FOR UPDATE`를 실행해 비관적 락 확보(`InventoryMapper.xml`)
- 주문 취소/조회는 `OrderMapper.xml`의 `WHERE o.id = #{orderId} FOR UPDATE`로 주문 단건에 비관적 락 적용
- `OrderService`에서 주문 생성은 `@Idempotent` + `@Transactional`로 감싸여 있어, 멱등성 키 검증과 재고 락 확보, 주문/재고 갱신이 하나의 트랜잭션 안에서 처리됨

### 3.2 읽기/쓰기 DB 라우팅
- `ReplicationRoutingDataSource`(`AbstractRoutingDataSource` 상속)가 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 값을 보고 `readOnly=true` 트랜잭션은 Replica로, 그 외는 Master(Source)로 라우팅
- `docker-compose.yml`에 `db-master`(3306)·`db-replica`(3307) 컨테이너가 정의되어 있고, 앱 컨테이너는 `scripts/wait-for-it.sh db-replica:3307 -- java -jar app.jar`로 Replica가 준비된 후 기동
- `docker-init/master`, `docker-init/replica`, `mysql-config/*.cnf`에 복제 초기화 스크립트와 MySQL 설정 존재

### 3.3 관측성
- `MDCLoggingFilter` + `logback-spring.xml`로 요청 단위 로그에 추적 정보(MDC)를 주입