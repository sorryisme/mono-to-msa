# F-Market 프로젝트 분석

> 최초 작성일: 2026-09-10 / 갱신일: 2026-09-12
> 분석 범위: **로컬 프로젝트 코드베이스만 기준** (GitHub 이슈/PR/브랜치 등 원격 저장소 정보는 배제)
>
> 이 문서는 시점 스냅샷입니다. 최초 작성 시점(2026-09-10)의 MyBatis·Spock 기준 서술은
> 2026-09-11~12 의 전환 작업 결과로 모두 갱신했습니다. 전환 경위는
> [docs/logs/20260911/](logs/20260911/) · [docs/logs/20260912/](logs/20260912/) 의 산출물을 참고하세요.

## 1. 프로젝트 개요

- **설명**: G마켓을 레퍼런스로 한 기본 이커머스 플랫폼 (README 기준)
- **핵심 기능**: 구매자 - 장바구니/주문/주문내역 확인, 판매자 - 상품등록/판매관리/재고관리
- **기술 스택**: Java 21, Spring Boot 4.1.1, Spring Data JPA(Hibernate), MySQL 8.4(Docker), JUnit 5·Mockito, Docker/Docker Compose
- **주요 런타임 의존성**(`build.gradle`): `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-security-crypto`(비밀번호 암호화), `spring-boot-starter-aspectj`, `spring-data-commons`(페이지네이션), `mysql-connector-j`, `h2`
- **테스트 의존성**: `spring-boot-starter-test`(JUnit 5·Mockito·AssertJ 포함), `spring-boot-data-jpa-test`·`spring-boot-webmvc-test`(Boot 4 부터 슬라이스 테스트가 별도 모듈)
- **포맷터**: Spotless + google-java-format (`src/**/*.java`). `check`/`build` 에 연결되어 있어 포맷 위반 시 빌드 실패
- **git**: git 저장소로 관리됨 (최초 분석 시점에는 비-git 상태였음)

### 1.1 최근 주요 변경 (2026-09-11 ~ 09-12)

| 날짜 | 변경 | 비고 |
|---|---|---|
| 09-11 | Java 17 → 21, Spring Boot 3.3.5 → 4.1.1 | |
| 09-11 | **MyBatis → Spring Data JPA 전면 전환** | `mapper/`·`resources/mapper/*.xml` 제거, `entity/`·`repository/` 신설 |
| 09-11 | 상품 계열 소프트 삭제를 `deleted_at` → `status`(`ProductStatus`) 로 이관 | |
| 09-11 | API 응답 계약을 HTTP 상태 + 도메인 `ErrorCode` 로 전환 | 예외 클래스 4종 → `BusinessException` 1종 |
| 09-12 | 재고 차감·주문 상태 전이를 **조건부 원자적 UPDATE** 로 전환 | 비관적 락(`SELECT ... FOR UPDATE`) 제거 |
| 09-12 | **Spock → JUnit 5 전환 완료** | `src/test/groovy` 제거, 테스트 전부 `src/test/java` |

## 2. 코드베이스 구조 (레이어드 아키텍처)

```
src/main/java/com/sorryisme/fmarket/
├── annotation/     - @LoginUserId, @RequireLogin, @Idempotent, @IdempotencyKeyParam
├── aop/            - AuthenticationAspect, IdempotencyAspect
├── common/         - AppConstants, ErrorCode, PageableSupport, SessionManager,
│                     dto/{ResponseDto, FieldErrorDto}
├── config/         - DataSourceConfiguration, ReplicationRoutingDataSource, WebConfig
├── controller/     - Cart / Order / Product / User
├── dto/{request,response}
├── entity/         - BaseTimeEntity + Cart, CartDetail, IdempotencyKey, Inventory,
│                     MajorCategory, Order, OrderDetail, Product, ProductOption,
│                     ProductReview, Store, Subcategory, User
├── enums/          - OrderStatus, ProductStatus, UserRole
├── exception/      - BusinessException(ErrorCode 보유), GlobalExceptionHandler
├── filter/         - MDCLoggingFilter
├── repository/     - Spring Data JPA 리포지토리 11종 + ProductSpecification
├── resolver/       - LoginUserIdResolver (커스텀 아규먼트 리졸버)
├── service/        - Cart / Order / Product / User
└── utils/          - PasswordCipher
```

- **ORM**: Spring Data JPA(Hibernate). 커스텀 쿼리는 리포지토리의 `@Query` 로, 동적 검색 조건은 `ProductSpecification`(JPA Criteria) 으로 둔다. MyBatis 매퍼 XML 은 남아 있지 않다
- **스키마**: `src/main/resources/schema.sql` + `docker-init/` 로 관리한다. Hibernate 자동 생성에 맡기지 않으며, `entity/EntityMappingTest` 가 `ddl-auto=validate` 로 매핑-스키마 일치를 검증한다
- **인증**: 세션 기반(`SessionManager`) + 커스텀 애노테이션(`@RequireLogin`, `@LoginUserId`) + `AuthenticationAspect`(AOP)로 컨트롤러 진입 시 검증
- **응답 계약**: 모든 응답이 `ResponseDto` 봉투(`code`/`message`/`data`). 성공은 `code=OK`, 실패는 `ErrorCode` 이름이고 HTTP 상태도 `ErrorCode` 가 소유한다 — 상세는 [API_RESPONSE.md](API_RESPONSE.md)
- **테스트**: JUnit 5 전부 `src/test/java` (클래스 15개 / 테스트 90개). 서비스는 `Mockito.mock()` 단위 테스트, 리포지토리·엔티티는 `@DataJpaTest` + 실제 MySQL, 웹 계층 계약은 `@WebMvcTest`. `DomainFixture` 로 엔티티 픽스처 공통화 — 관례는 [TESTING.md](TESTING.md)
- **결제 기능 없음**: 컨트롤러/서비스/엔티티 어디에도 결제 관련 코드가 없음 — README 의 "결제" 언급과 달리 실제 구현 범위는 주문 생성/조회/취소까지. `OrderService.createOrder` 의 주석에 결제 도입 시의 트랜잭션 분리 방안이 설계 메모로 남아 있다

### 2.1 API 엔드포인트 (`/api/v1`)

| 도메인 | 엔드포인트 |
|---|---|
| User | `POST /user/signup`, `PUT /user/update`, `POST /user/login`, `POST /seller/signup` |
| Cart | `POST /cart/add`, `DELETE /cart/{id}` |
| Product | `GET /products/category`, `POST /products/search`, `GET /products/{id}`, `POST /{productId}/reviews` |
| Order | `POST /orders`(목록 검색), `POST /orders/create`(생성), `GET /orders/{id}`, `PUT /orders/{id}/cancel`, `PUT /orders/{id}/confirm` |

`POST /products/search` 와 `POST /orders` 는 검색 조건을 본문으로 받는 **조회** 엔드포인트다 — 이름만 보고 생성으로 읽지 않도록 주의한다. 생성은 `POST /orders/create` 쪽이다.

## 3. 코드에서 확인한 주요 기술적 구현

### 3.1 주문 생성 동시성 제어 — 조건부 원자적 UPDATE

2026-09-12 에 비관적 락 기반 구현을 조건부 UPDATE 로 바꿨다. 검토 근거는 [docs/plan/260912-lock.md](plan/260912-lock.md).

- **멱등성**: `@Idempotent` + `IdempotencyAspect`. `@IdempotencyKeyParam` 이 붙은 36자 UUID 문자열을
  `idempotency_keys` 테이블에 `saveAndFlush` 하고, **유니크 제약 위반(`DataIntegrityViolationException`)을
  곧 중복 요청으로 해석**해 `ErrorCode.DUPLICATE_REQUEST` 를 던진다 (`SELECT ... FOR UPDATE` 방식 아님)
- **재고 확보**: `InventoryRepository.decreaseQuantity` 가
  `update Inventory set quantity = quantity - :q where productOptionId = :id and quantity >= :q` 한 문장으로
  검사와 차감을 묶는다. 영향 행 0 이면 재고 부족 또는 재고 행 없음이므로 `OUT_OF_STOCK` 으로 전체 롤백.
  별도 조회 락 없이 초과 판매가 생기지 않는다
- **데드락 회피**: `OrderService.createOrder` 는 요청 순서와 무관하게 **옵션 ID 오름차순**으로 차감한다.
  서로 다른 주문이 같은 옵션들을 반대 순서로 잠그는 경로를 없애기 위한 것으로,
  `OrderServiceTest` 가 `InOrder` 로 이 순서를 계약으로 고정한다
- **주문 상태 전이**: `OrderRepository.updateStatusIfCurrent(id, from, to)` 가
  `where id = :id and status = :from` 조건부 UPDATE 다. 동시에 들어온 취소·(추후) 결제 실패 처리·만료 배치 중
  전이에 성공하는 요청이 하나뿐이므로 **재고 복구 같은 후속 작업이 중복 실행되지 않는다**.
  `cancelOrder` 가 상태 전이를 재고 복구보다 먼저 하는 이유가 이것이다
- 주문 생성은 `@Idempotent` + `@Transactional` 로 감싸여 멱등성 키 확인·재고 차감·주문 저장이 한 트랜잭션이다

### 3.2 읽기/쓰기 DB 라우팅
- `ReplicationRoutingDataSource`(`AbstractRoutingDataSource` 상속)가 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 값을 보고 `readOnly=true` 트랜잭션은 Replica 로, 그 외는 Master(Source)로 라우팅
- `docker-compose.yml` 에 `mysql-master`(3306)·`mysql-replica`(3307) 컨테이너가 정의되어 있고, 앱 컨테이너는 `scripts/wait-for-it.sh db-replica:3307 -- java -jar app.jar` 로 Replica 가 준비된 후 기동
- `docker-init/master`, `docker-init/replica`, `mysql-config/*.cnf` 에 복제 초기화 스크립트와 MySQL 설정 존재

### 3.3 페이지네이션 안정성
- `PageableSupport.withStableSort` 가 정렬에 `id` 가 없으면 맨 뒤에 보조 정렬을 붙인다. 같은 정렬 값이 여러 건일 때 페이지 경계가 흔들리는 문제를 막는 장치로, 목록 조회 서비스가 공통으로 통과시킨다

### 3.4 관측성
- `MDCLoggingFilter`(최고 우선순위 필터) + `logback-spring.xml` 로 요청 단위 로그에 `request_UUID`·`uri`·`method` 를 MDC 로 주입하고 처리 시간을 남긴다
- 요청/응답 바디는 `ContentCachingRequestWrapper`/`ContentCachingResponseWrapper` 로 캐싱해 로깅한다. Spring Framework 7 에서 한도 없는 생성자가 제거되어 요청 캐시 상한을 64KB 로 명시해 두었다

### 3.5 검증 자동화 (hook / CI 공용)
- `scripts/lint-changed.sh`, `scripts/test-affected.sh`, `scripts/verify-full.sh` 를 git hook(`.githooks/`)·Claude Code hook(`.claude/hooks/`)·CI(`.github/workflows/verify.yml`)가 함께 쓴다
- `scripts/guard-scan.sh` 가 금지 패턴(`System.out/err.print`, `printStackTrace`, 빈 catch, 테스트 skip 애노테이션, 자격증명·키 하드코딩, 외부 URL 하드코딩)을 검사한다. 예외는 해당 줄의 `// hook-allow: <사유>` 로만 허용 — 상세는 [HOOKS.md](HOOKS.md)

## 4. 눈에 띄는 개선 여지

- **조회가 `POST` 로 노출되어 있다** — `POST /orders`(주문 목록 검색)와 `POST /products/search` 는 본문으로 검색 조건을 받는 조회다. 생성 경로(`POST /orders/create`)와 메서드가 같아 이름만으로는 구분되지 않고, 캐시·멱등성 의미도 잃는다. 조건이 길어 본문이 필요하다면 경로에서라도 조회임이 드러나는 편이 낫다
- **`PUT /orders/{id}/cancel`·`/confirm`** 은 부분 상태 전이이므로 `PATCH` 가 더 맞는 의미다 (기존 클라이언트 호환 여부 확인 필요)
- **`OrderStatus` 가 PENDING/COMPLETED/CANCELLED 3개뿐이다** — 결제를 붙이면 PENDING 이 "주문 생성됨/재고 예약됨/결제 진행 중" 중 무엇인지 모호해진다. `OrderService` 주석에 `PAYMENT_PENDING` 분리 필요성이 메모로 남아 있다
- **결제 미구현에 따른 공백**: 일정 시간 결제가 끝나지 않은 주문을 만료시키는 배치와 PG 대사 재처리가 없다. 현재는 재고 차감이 "예약" 인데 이를 해제할 자동 경로가 없다
