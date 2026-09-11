# ARCHITECTURE.md

f-market 백엔드의 아키텍처 상세 문서입니다. 프로젝트 개요와 폴더 구조는 [CLAUDE.md](../CLAUDE.md)를 참고하세요.

## 레이어드 아키텍처

`controller -> service -> repository (Spring Data JPA)`. 데이터 접근은 `src/main/java/.../repository/`의 `JpaRepository` 인터페이스가 담당하고,
동적 검색 조건은 `ProductSpecification`(Criteria API)으로, 잠금·fetch join 이 필요한 조회는 `@Query`/`@Lock`/`@EntityGraph` 로 표현합니다.
`entity/`의 JPA 엔티티는 영속성 계층 안에서만 다루고, 컨트롤러 응답은 항상 `dto/response` 로 변환합니다 (엔티티 직접 노출 금지).
엔티티는 setter 없이 `changeStatus`, `decrease` 같은 의미 있는 변경 메서드만 노출하며, 쓰기 트랜잭션 안에서 변경 감지로 UPDATE 가 나갑니다.
`created_at`/`updated_at` 은 DB 기본값이 채우므로 `BaseTimeEntity` 가 읽기 전용으로 매핑합니다. 스키마는 `schema.sql` 이 관리하고 `spring.jpa.hibernate.ddl-auto=none` 으로 고정합니다.

연관관계는 생명주기를 소유하는 부모-자식(`Order↔OrderDetail`, `Cart↔CartDetail`, `MajorCategory↔Subcategory`)에만 걸고(`cascade=ALL, orphanRemoval=true`, LAZY),
`User`·`ProductOption`·`Product` 참조는 `Long` FK 값으로 유지합니다. 그래서 `Product` 상세 조회는 상품·옵션·리뷰를 세 번 조회해 DTO 로 합칩니다.

`src/main/java/com/sorryisme/fmarket/` 하위 패키지 구성:
- `annotation/` — `@RequireLogin`, `@LoginUserId`, `@Idempotent`, `@IdempotencyKeyParam`. 컨트롤러 메서드/파라미터에 적용.
- `aop/` — `AuthenticationAspect`와 `IdempotencyAspect`가 위 두 애노테이션을 AspectJ `@Before` 어드바이스로 구현.
- `resolver/LoginUserIdResolver` + `config/WebConfig` — `@LoginUserId` 컨트롤러 파라미터를 `SessionManager`로부터 해석해주는 커스텀 `HandlerMethodArgumentResolver`.
- `config/` — `DataSourceConfiguration`이 `application.yml`의 `source`/`replica` 두 데이터소스를 `ReplicationRoutingDataSource`에 연결.
- `common/GlobalExceptionHandler` — `exception/` 패키지의 커스텀 예외들을 HTTP 응답으로 매핑하는 `@RestControllerAdvice`.

## 읽기/쓰기 DB 라우팅

`ReplicationRoutingDataSource`(`AbstractRoutingDataSource` 상속)는 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 값을 기준으로
트랜잭션마다 마스터/레플리카를 선택합니다 — 즉 라우팅은 호출 위치가 아니라 서비스 메서드의 `@Transactional(readOnly = true)` 설정으로 제어됩니다.
JPA 도입 후에도 `@Primary` DataSource 는 그대로 `LazyConnectionDataSourceProxy` 이므로 EntityManager 가 실제 커넥션을 얻는 시점(첫 쿼리)에 라우팅이 결정됩니다.
`readOnly = true` 는 replica 라우팅과 동시에 Hibernate flush 를 끄므로, 쓰기 메서드에는 절대 붙이지 않습니다.
Docker Compose에서 마스터는 `db-master:3306`, 레플리카는 `db-replica:3307`이며, 앱 컨테이너는 `scripts/wait-for-it.sh db-replica:3307`로 레플리카가 준비될 때까지 대기한 뒤 시작합니다.

## 멱등성과 락 (주문 생성)

- `@Idempotent`(`IdempotencyAspect`가 검사)는 `@IdempotencyKeyParam`이 붙은 UUID 파라미터를 필요로 하며, `IdempotencyKeyRepository.saveAndFlush` 로 `idempotency_keys` 에 INSERT 합니다. 유니크 제약 위반(`DataIntegrityViolationException`)이 곧 중복 요청이며 `DuplicateDataException`으로 바꿔 던집니다.
- `InventoryRepository.findAllByProductOptionIdInForUpdate`는 `@Lock(PESSIMISTIC_WRITE)`로 대상 `product_option_id` 행에 비관적 락(`SELECT ... FOR UPDATE`)을 겁니다. 수량 변경은 `Inventory.decrease/increase` 후 변경 감지로 반영됩니다.
- 주문 취소는 `OrderRepository.findByIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`)로 주문 행에 락을 건 뒤 상태를 검사합니다.
- `OrderService`의 주문 생성 로직은 `@Idempotent` + `@Transactional`을 함께 사용해, 멱등성 키 검증·재고 락·주문/재고 갱신이 하나의 트랜잭션 안에서 처리됩니다. 주문 상세는 `Order` 의 cascade 로 함께 저장됩니다.

## 인증

Spring Security가 아닌 세션 기반(`common/SessionManager`)입니다.
컨트롤러는 `@RequireLogin`으로 엔드포인트를 표시하고, `AuthenticationAspect`가 메서드 실행 전에 `SessionManager.getUserId()`를 확인해 없으면
`RequireLoginException`을 던집니다. `@LoginUserId` + `LoginUserIdResolver`는 현재 로그인한 사용자의 id를 컨트롤러 메서드 파라미터로 주입합니다.
