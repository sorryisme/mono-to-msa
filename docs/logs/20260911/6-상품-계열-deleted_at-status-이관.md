# 상품 계열 deleted_at → status 이관

- 작업일: 2026-09-11
- 범위: `Product`, `ProductOption`, `Inventory` (User 는 제외)
- 결과: `./gradlew test` 68건 통과 (실패 0, 오류 0)

## 1. 배경

`deleted_at` 이 네 테이블에 있었지만 이를 조건으로 거는 쿼리가 하나도 없었다(JPA 전환 전 MyBatis 매퍼에도 없었다).
삭제된 상품이 목록에 나오고 주문도 됐다. 상품은 삭제 외에 판매중지 같은 라이프사이클이 필요하고
Order/Payment 가 이미 status enum 을 쓰고 있어, 상품 계열은 상태로 이관하고 모든 조회 경로에 필터를 넣었다.

## 2. 설계 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 상태 값 | `ProductStatus { ON_SALE, SUSPENDED, DELETED }` | 판매중지와 삭제를 구분. 품절은 재고에서 파생되므로 저장하지 않음 |
| 공유 enum | Product·ProductOption 이 같은 enum 사용 | 값이 같고 의미가 같음 |
| Inventory | `deleted_at` 컬럼 제거 | 옵션의 생명주기를 따르므로 독립 상태 불필요 |
| Hibernate `@SoftDelete` | 사용 안 함 | 이진(삭제/비삭제) 전용이라 다중 상태에 못 씀. 조회마다 조건을 직접 건다 |
| 인덱스 | 추가 안 함 | `status` 단독은 선택도가 낮음 |

## 3. 조회 경로별 필터

| 경로 | 조건 |
|---|---|
| 상품 목록 검색 (`ProductSpecification`) | `status = ON_SALE` 항상 포함 |
| 상품 상세 (`findByIdAndStatusNot(DELETED)`) | 삭제만 제외. 판매중지 상품은 상세 노출 |
| 상세의 옵션 (`findAllByProductIdAndStatusNot(DELETED)`) | 삭제된 옵션만 제외 |
| 리뷰 작성 (`existsByIdAndStatusNot(DELETED)`) | 삭제된 상품에는 리뷰 불가 |
| 주문 생성 (`findAllByIdInAndStatus(ids, ON_SALE)`) | 판매중 옵션만. 요청 id 와 개수가 다르면 `IllegalArgumentException("판매 중이 아닌 상품 옵션이 포함되어 있습니다.")` 로 저장 전에 거절 |

주문 생성은 이전에 없는 옵션을 조용히 건너뛰고 재고 단계에서 "재고 수량이 충분하지 않습니다" 로 실패했다.
이제 옵션 조회 직후 검증하므로 주문 INSERT 전에 명확한 메시지로 끝난다.

## 4. 변경 파일

- `enums/ProductStatus.java` 신규
- `entity/Product.java`, `entity/ProductOption.java` — `deletedAt`/`softDelete()` 제거, `status` + `suspend()`/`resume()`/`delete()`/`isDeleted()`. 삭제 후 상태 변경은 `IllegalStateException`
- `entity/Inventory.java` — `deletedAt` 제거
- `resources/schema.sql` — product·product_option 에 `status enum(...) NOT NULL DEFAULT 'ON_SALE'`, 세 테이블에서 `deleted_at` 제거. `data.sql` 은 컬럼을 지정하지 않으므로 기본값으로 채워져 수정 없음
- `repository/ProductRepository.java`, `ProductOptionRepository.java`, `ProductSpecification.java` — 위 표의 메서드
- `service/ProductService.java`, `service/OrderService.java` — 필터 메서드로 교체, 주문 옵션 검증 추가
- `dto/response/ProductListResponseDto.java` — `deletedAt` → `status`(문자열). **API 응답 계약 변경**
- `docs/ARCHITECTURE.md` — 상태 정책 문단 추가

## 5. 테스트

- `repository/ProductRepositoryTest` — 판매중지·삭제 상품을 시드해 목록 제외, 상세 삭제만 제외, 옵션 상세/주문용 필터 검증 추가
- `entity/EntityMappingTest` — `ddl-auto=validate` 통과 + status 기본값·소프트 삭제가 문자열 `DELETED` 로 저장되는지 네이티브 조회로 확인
- `service/ProductServiceTest`, `OrderServiceTest` — 새 리포지토리 메서드로 stub 교체, 판매중 아닌 옵션 거절 시 `save`·재고 호출이 없음을 검증

## 6. 후속: OrderStatus 정리

`OrderStatus` 가 상수 이름과 같은 `value` 필드와 Lombok `@Getter` 를 들고 있었다. `@Enumerated(EnumType.STRING)` 으로
상수 이름이 그대로 저장되므로 중복이라 제거하고, `UserRole`·`ProductStatus` 와 같은 단순 상수형으로 맞췄다.
`getValue()` 를 호출하는 곳은 없어 다른 수정은 없다. 전체 테스트 68건 통과.

## 7. 미조치·다음

- `User.deleted_at` 은 유지. 탈퇴 시각·UNIQUE(login_id, email) 재가입 정책과 함께 별도 작업
- 상태를 바꾸는 API(판매중지/삭제)는 아직 없다. 엔티티 메서드만 준비했다
- 장바구니 담기(`CartService.addCart`)는 옵션 존재·상태를 검증하지 않는다. 기존에도 없던 검증이라 이번 범위에서 제외
