# MyBatis → JPA 전환 2단계: 서비스·리포지토리 전환 및 MyBatis 제거

작업 브랜치: `refactor/mybatis-to-jpa` (1단계 [엔티티 매핑](2026-09-11-mybatis-jpa-엔티티-전환.md) 위에 진행)

## 배경과 범위

1단계에서 만든 `entity/` 를 실제 런타임 경로에 연결했다. 서비스 4종과 `IdempotencyAspect` 가 MyBatis 매퍼 대신 Spring Data JPA 리포지토리를 쓰도록 바꾸고,
그 결과 아무도 쓰지 않게 된 MyBatis 매퍼·XML·`domain/`·`mybatis-spring-boot-starter` 의존성·매퍼 테스트를 모두 제거했다.
컨트롤러의 REST 경로·요청 DTO·응답 JSON 키는 유지했다.

## 변경 내용

### 리포지토리 (`repository/` 신설, 12개)
| 리포지토리 | 대체한 매퍼 기능 | 방식 |
|---|---|---|
| `UserRepository` | `isExistUser`, `findUserByLoginId`, `isExistUserById` | 파생 쿼리 `existsByNameAndPhoneNumber`, `findByLoginId`, `existsById` |
| `StoreRepository` | `insertStore` | `save` |
| `CartRepository` / `CartDetailRepository` | `findCartIdByUserId`, `insertCart(Detail)`, `deleteCartDetailById`, `isExistCartDetailById` | `findByUserId`, `save`, `delete`, `findById` |
| `OrderRepository` | `findAllOrderList`+`countOrderList`, `findOrderById`, `findOrderByIdForUpdate`, `updateOrder`, `createOrder(Detail)` | `Page` 반환 파생/`@Query`, `@EntityGraph(orderDetails)`, `@Lock(PESSIMISTIC_WRITE)`, 변경 감지, cascade 저장 |
| `InventoryRepository` | `findStockQuantityForUpdate`, `increaseStockQuantity`, `updateStockQuantity` | `@Lock(PESSIMISTIC_WRITE)` + `in :ids` 쿼리, `Inventory.increase/decrease` 변경 감지 |
| `ProductRepository` + `ProductSpecification` | `findAllProductList`+`countProducts`(동적 `<where>`) | `JpaSpecificationExecutor.findAll(spec, pageable)` — query LIKE / majorCategory / subcategory 를 값이 있을 때만 AND |
| `ProductOptionRepository` / `ProductReviewRepository` | `findProductOptionsByIds`, `insertProductReview`, `findOneProductById` 의 조인 | `findAllById`, `save`, `findAllByProductId` |
| `MajorCategoryRepository` | `findMajorCategoryList`(INNER JOIN) | `join fetch m.subcategories` — 중분류 있는 대분류만 반환하는 기존 의미 유지 |
| `IdempotencyKeyRepository` | `insertIgnoreIdempotencyKey`(INSERT IGNORE) | `saveAndFlush` + 유니크 제약 위반 → `DuplicateDataException` |

### 엔티티 보강
- 신규: `MajorCategory`↔`Subcategory`(부모-자식 연관), `IdempotencyKey`
- `Order.of(productOptions, quantityMap, userId)` — 기존 `Order.of` 를 옮김 (PENDING, 총액 = 판매가×수량 합)
- `User` role 미지정 시 `USER` — Hibernate 는 null 을 명시적으로 INSERT 하므로 DB DEFAULT 에 기대지 않는다
- `Inventory.decrease` 부족 시 `IllegalArgumentException("재고 수량이 충분하지 않습니다.")` — 서비스 API 메시지와 통일

### 서비스
- 모든 쓰기 메서드에 `@Transactional` 부여 (`confirmOrder`, `createUser`, `updateUser`, `createReview` 는 기존에 없었음). JPA 변경 감지는 트랜잭션 안에서만 동작하므로 필수.
- `OrderService.createOrder`: 주문+상세 cascade 저장 → 재고 행 잠금 → `decrease`. 락 순서(주문 INSERT 후 재고 락)는 기존과 동일.
- `OrderService.cancelOrder`: 주문 행 락 → PENDING 검사 → 상세를 옵션별로 합산해 `increase` → CANCELLED.
- `ProductService.findProductById`: 옵션·리뷰 연관을 두지 않기로 한 결정에 따라 상품/옵션/리뷰 3회 조회 후 `ProductResponseDto.of` 로 조립 (기존 LEFT JOIN 1회의 카티전 곱 제거).
- `UserService.updateUser`: 변경 감지로 UPDATE. 영향 행 수 0 → `UpdateFailException` 분기는 사라짐(예외 클래스·핸들러는 유지).
- `common/PageableSupport.withStableSort`: 정렬 없는 Pageable 에 `id` 정렬을 붙여 페이지 경계를 안정화.

### 컨트롤러 / DTO
- 엔티티(구 도메인) 직접 노출 제거: `Page<Order>`→`Page<OrderListResponseDto>`, `Page<Product>`→`Page<ProductListResponseDto>`(`@JsonProperty("product_name")` 로 기존 키 유지), `ProductReview`→`ProductReviewResponseDto`
- 응답 DTO 에 `from(Entity)` 정적 팩토리 추가, `OrderDetailResponseDto.toInventory`·`OrderCreateDto.toInventoryList` 제거

### 제거
`mapper/*.java`(7), `resources/mapper/*.xml`(6), `domain/*.java`(10), `test/.../mapper/*Test.groovy`(7), `mybatis-spring-boot-starter(-test)`, `application.yml` 의 `mybatis:` 블록, `logback-spring.xml` 의 `org.mybatis` 로거

### hook / 문서
- `scripts/arch-check.sh`: `mapper`/`domain` 규칙을 `repository`/`entity` 로 교체 (controller→repository 직접 참조 금지 등)
- `scripts/lint-changed.sh`, `scripts/test-affected.sh`, `.claude/hooks/session-start.sh`: 안내 문구 갱신
- `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/HOOKS.md` 갱신

## 의도된 동작 변경 (기존 SQL 과 다른 점)

| 항목 | 기존 | 변경 | 이유 |
|---|---|---|---|
| 주문 기간 검색 `endPeriod` | `order_date BETWEEN '2025-01-01' AND '2025-12-31'` → 12-31 00:00 이후 주문 누락 | `>= start 00:00 AND < end+1일 00:00` | 날짜 입력의 자연스러운 의미. 종료일 당일 주문 포함 |
| 카테고리 응답 `majorCategoryName`/`description` | resultMap 컬럼명 불일치로 항상 `null` | 실제 값 매핑 | resultMap 버그 수정 |
| `SellerResponseDto.phoneNumber` | `store.businessNumber` 가 들어감 | `user.phoneNumber` | 필드 오매핑 수정. `description` 도 함께 채움 |
| 목록 정렬 | 정렬 없음(사실상 PK 순) | 정렬 미지정 시 `id ASC` 명시 | 페이지 경계 안정화. 관측 결과는 동일 |
| 상품 상세 조회 쿼리 수 | LEFT JOIN 1회 (옵션×리뷰 카티전) | 3회 | 연관 미도입 결정에 따름. 행 폭증 제거 |
| 멱등키 중복 | `INSERT IGNORE` 영향 행 0 | 유니크 제약 위반 예외 | 예외는 `IdempotencyAspect` 가 `DuplicateDataException` 으로 변환. 외부 동작 동일 |

## 검증

- `./gradlew test`: **59개 전부 통과** (실제 MySQL 사용)
  - 서비스 단위 테스트 4종 — 리포지토리 `Mock()` 기반으로 재작성 (기존 케이스 유지 + 삭제 대상 없음/재고 행 없음 케이스 추가)
  - 리포지토리 `@DataJpaTest` 6종 신규 — 기간 경계(`[from, to)`), 페이징 count, fetch join, 비관적 락 조회 후 변경 감지, `ProductSpecification` 조건 조합 5가지, 카테고리 fetch join(8/10건), 멱등키 유니크 위반, `existsByNameAndPhoneNumber`/`findByLoginId`/`findByUserId`
  - `EntityMappingTest`(ddl-auto=validate) — 신규 3개 엔티티 포함 전 엔티티 컬럼이 `schema.sql` 과 일치
  - `FmarketApplicationTests` — MyBatis 없이 전체 컨텍스트(라우팅 DataSource + JPA + AOP) 부팅
- `bash scripts/lint-changed.sh <변경 파일>`: 포맷·금지 패턴·시크릿·의존 방향 통과 (테스트 데이터의 URL 경고 1건은 기존 그대로)

## 남은 작업 / 미검증 사항

- 엔티티 접미사 제거: 전환 완료에 맞춰 `UserEntity`→`User` 등 13개 클래스를 `entity/` 패키지 안에서 접미사 없는 이름으로 정리했다(`BaseTimeEntity` 는 매핑 슈퍼클래스라 유지). 리포지토리·서비스·DTO·테스트·문서의 참조를 함께 바꿨고 `./gradlew test` 59개 통과를 재확인했다.
- **replica 라우팅 실측 미확인**: `@Transactional(readOnly = true)` 가 JPA 경로에서 replica 로 가는지는 단일 MySQL 환경이라 실측하지 못했다. 구조상 `LazyConnectionDataSourceProxy` 가 첫 쿼리 시점에 라우팅하므로 동작해야 한다.
- **동시성 실측 미확인**: `PESSIMISTIC_WRITE` 가 `FOR UPDATE` 로 나가는 것은 Hibernate 표준 동작이지만, 두 트랜잭션이 실제로 직렬화되는 것을 테스트로 증명하지는 않았다.
- `IdempotencyAspect` 와 `@Transactional` 의 advice 순서는 기존과 동일하게 두었다(명시적 `@Order` 없음). 멱등키 저장이 주문 트랜잭션에 포함되는지 여부는 기존과 같은 조건이다.
- `h2` 런타임 의존성은 사용처가 없어 보이나 이번 범위 밖이라 두었다.
