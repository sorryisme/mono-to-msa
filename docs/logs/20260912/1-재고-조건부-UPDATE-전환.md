# 재고·주문 상태를 조건부 원자적 UPDATE 로 전환

- 일자: 2026-09-12
- 근거 문서: [docs/plan/260912-lock.md](../../plan/260912-lock.md)

## 1. 계획 타당성 판단

| 계획 항목 | 판단 | 처리 |
| --- | --- | --- |
| 1. `UNIQUE(product_option_id)` 추가 | 타당 | 반영 |
| 2. 조건부 차감 UPDATE 로 비관적 조회 락 대체 | 타당 | 반영 |
| 3. 옵션 ID 오름차순 차감으로 데드락 회피 | 타당 | 반영 |
| 4. 주문 생성 시 `PAYMENT_PENDING` 상태 저장 | 방향은 타당하나 지금은 부적절 | 주석으로 대체 |
| 5. DB 트랜잭션 종료 후 결제 요청 | 타당 | 주석으로 대체 |
| 6. 결제 성공·실패·취소를 조건부 상태 UPDATE 로 1회만 처리 | 타당, 결제 없이도 지금 필요 | 반영 |
| 7. 결제 미완료 주문 만료 + 재고 복구 배치 | 타당 | 주석으로 대체 |
| 8. PG 조회 API 대사 배치·이벤트 재처리 | 타당 | 주석으로 대체 |

4·5·7·8 은 결제 모듈 자체가 없어 지금 구현하면 호출되지 않는 코드와 DB enum 변경만 남는다. 의도와 전환 시 해야 할 일을 `OrderStatus`, `OrderService.createOrder` 의 주석으로 남겼다.

6 번은 결제와 무관하게 지금 필요했다. 기존 `confirmOrder` 는 상태 검사 없이 `COMPLETED` 로 덮어써서 이미 취소된 주문도 확정할 수 있었고, `cancelOrder` 는 비관적 락에 의존하고 있었다.

## 2. 변경 내용

### 스키마 / 엔티티

- `schema.sql`: `inventory.product_option_id` 의 일반 인덱스를 `UNIQUE KEY uk_inventory_product_option_id` 로 변경. 조건부 차감 UPDATE 가 "옵션당 정확히 1행" 을 갱신한다는 전제를 DB 가 보장한다.
- `Inventory`: `@UniqueConstraint` 선언 추가, `decrease()` / `increase()` 제거. 수량 불변식은 애플리케이션이 아니라 DB 의 UPDATE 조건이 지킨다.
- `Order`: `changeStatus()` 제거. 상태 전이는 조건부 UPDATE 로만 한다.
- `OrderStatus`: 결제 도입 시 `PAYMENT_PENDING` / `PAID` 로 의미를 분리해야 한다는 주석 추가. enum 상수는 늘리지 않았다(DB enum 컬럼 동반 변경 필요).

### 리포지토리

- `InventoryRepository`: `findAllByProductOptionIdInForUpdate()` 제거, `decreaseQuantity()` / `increaseQuantity()` 추가.
  - 차감: `update Inventory i set i.quantity = i.quantity - :quantity where i.productOptionId = :id and i.quantity >= :quantity`
  - 반환값 1 = 확보 성공, 0 = 재고 부족 또는 재고 행 없음
- `OrderRepository`: `findByIdForUpdate()` 제거, `updateStatusIfCurrent(id, from, to)` 추가.
  - `update Order o set o.status = :to where o.id = :id and o.status = :from`

### 서비스

- `createOrder`: 판매중 옵션 검증 → **옵션 ID 오름차순 조건부 차감** → 주문 저장 순서로 변경. 차감 결과가 0행이면 `OUT_OF_STOCK` 으로 전체 롤백한다. 차감을 저장보다 앞에 두어 재고 부족이면 불필요한 INSERT 없이 실패한다.
- `cancelOrder`: 상세 조회 → 조건부 상태 전이(`PENDING` → `CANCELLED`) → 전이에 성공한 경우에만 재고 복구. 사용자 취소와 (추후) 결제 실패 처리·만료 배치가 동시에 들어와도 재고가 두 번 복구되지 않는다. 재고 행이 사라진 옵션은 취소를 막지 않고 `WARN` 로그만 남긴다.
- `confirmOrder`: 존재 확인 후 조건부 전이(`PENDING` → `COMPLETED`). 전이 실패 시 `ORDER_STATUS_NOT_CHANGEABLE`. 결제 성공 콜백이 중복 수신돼도 한 번만 확정된다.

## 3. 테스트

- `InventoryRepositoryTest`: 조건부 차감 성공/재고 부족/행 없음, 복구 성공/행 없음 검증으로 교체.
- `OrderRepositoryTest`: 비관적 락 테스트를 조건부 상태 전이 테스트(1행 갱신 / 재시도 시 0행 / 없는 ID 0행)로 교체.
- `OrderServiceTest`: 새 계약에 맞게 재작성. `createOrder` 는 요청 순서를 뒤집어도 옵션 ID 오름차순으로 차감하는지 Spock 의 순서 있는 `then:` 블록으로 검증한다.

### 실행 결과

- `./gradlew spotlessApply compileJava compileTestGroovy` — 성공
- `./gradlew test` (MySQL master/replica 기동 상태) — **15개 테스트 클래스 전부 통과, 실패·스킵 0건**
- `inventory` 의 UNIQUE 제약(`uk_inventory_product_option_id`) 이 master·replica 양쪽에 반영된 것을 `SHOW INDEX` 로 확인. `EntityMappingTest` 의 `ddl-auto=validate` 검증도 통과.

## 4. 남은 일

1. 결제 모듈 도입 시 `OrderStatus` 에 `PAYMENT_PENDING` / `PAID` 분리 + `order.status` DB enum 변경
2. 결제 요청은 주문 트랜잭션 커밋 이후, 주문 ID 기반 멱등성 키로 호출
3. 결제 미완료 주문 만료 배치(만료 → `CANCELLED` 조건부 전이 → 재고 복구)
4. PG 조회 API 대사 배치 또는 이벤트 재처리로 결제 결과 유실 대비
