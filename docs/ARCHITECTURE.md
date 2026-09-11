# ARCHITECTURE.md

f-market 백엔드의 아키텍처 상세 문서입니다. 프로젝트 개요와 폴더 구조는 [CLAUDE.md](../CLAUDE.md)를 참고하세요.

## 레이어드 아키텍처

`controller -> service -> mapper (MyBatis XML)`. SQL은 전부 `src/main/resources/mapper/*.xml`에 있고,
`src/main/java/.../mapper/`의 매퍼 인터페이스는 얇은 껍데기입니다 (예외적으로 `IdempotencyKeyMapper`는 별도 XML 없이 인라인 `@Select` 사용).
`domain/`의 도메인 객체는 여러 레이어에서 공유되는 단순 데이터 홀더이며, JPA 엔티티/도메인 분리 구조는 없습니다.

`src/main/java/com/sorryisme/fmarket/` 하위 패키지 구성:
- `annotation/` — `@RequireLogin`, `@LoginUserId`, `@Idempotent`, `@IdempotencyKeyParam`. 컨트롤러 메서드/파라미터에 적용.
- `aop/` — `AuthenticationAspect`와 `IdempotencyAspect`가 위 두 애노테이션을 AspectJ `@Before` 어드바이스로 구현.
- `resolver/LoginUserIdResolver` + `config/WebConfig` — `@LoginUserId` 컨트롤러 파라미터를 `SessionManager`로부터 해석해주는 커스텀 `HandlerMethodArgumentResolver`.
- `config/` — `DataSourceConfiguration`이 `application.yml`의 `source`/`replica` 두 데이터소스를 `ReplicationRoutingDataSource`에 연결.
- `common/GlobalExceptionHandler` — `exception/` 패키지의 커스텀 예외들을 HTTP 응답으로 매핑하는 `@RestControllerAdvice`.

## 읽기/쓰기 DB 라우팅

`ReplicationRoutingDataSource`(`AbstractRoutingDataSource` 상속)는 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 값을 기준으로
트랜잭션마다 마스터/레플리카를 선택합니다 — 즉 라우팅은 호출 위치가 아니라 서비스 메서드의 `@Transactional(readOnly = true)` 설정으로 제어됩니다.
Docker Compose에서 마스터는 `db-master:3306`, 레플리카는 `db-replica:3307`이며, 앱 컨테이너는 `scripts/wait-for-it.sh db-replica:3307`로 레플리카가 준비될 때까지 대기한 뒤 시작합니다.

## 멱등성과 락 (주문 생성)

- `@Idempotent`(`IdempotencyAspect`가 검사)는 `@IdempotencyKeyParam`이 붙은 UUID 파라미터를 필요로 하며, `idempotency_keys` 테이블에 INSERT하고 중복 키인 경우 `DuplicateDataException`을 던집니다.
- `InventoryMapper.findStockQuantityForUpdate`는 대상 `product_option_id` 행에 비관적 락(`SELECT ... FOR UPDATE`)을 겁니다.
- 주문 취소/조회는 `OrderMapper.xml`의 `WHERE o.id = #{orderId} FOR UPDATE`로 행 락을 겁니다.
- `OrderService`의 주문 생성 로직은 `@Idempotent` + `@Transactional`을 함께 사용해, 멱등성 키 검증·재고 락·주문/재고 갱신이 하나의 트랜잭션 안에서 처리됩니다.

## 인증

Spring Security가 아닌 세션 기반(`common/SessionManager`)입니다.
컨트롤러는 `@RequireLogin`으로 엔드포인트를 표시하고, `AuthenticationAspect`가 메서드 실행 전에 `SessionManager.getUserId()`를 확인해 없으면
`RequireLoginException`을 던집니다. `@LoginUserId` + `LoginUserIdResolver`는 현재 로그인한 사용자의 id를 컨트롤러 메서드 파라미터로 주입합니다.
