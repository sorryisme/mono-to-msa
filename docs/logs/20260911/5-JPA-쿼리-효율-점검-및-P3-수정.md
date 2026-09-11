# JPA 쿼리 효율 점검 및 P3 수정 — QueryDSL 대체 검토

- 작업일: 2026-09-11
- 대상 커밋: `814f210`(엔티티 보강), `c191ff0`(서비스·AOP JPA 전환)
- 결과: `./gradlew test` 63건 통과 (실패 0, 오류 0)

## 1. QueryDSL 대체 검토 결론

QueryDSL 로 바꿔서 **실행 효율이 올라가는 곳은 없다.** 동적 조건이 있는 두 곳은 옮겨도
생성 SQL 이 같고, 얻는 것은 타입 안전성과 가독성뿐이다.

| 위치 | 현재 | QueryDSL 로 옮기면 |
|---|---|---|
| `ProductSpecification` | Criteria 문자열 경로(`root.get("productName")`) | Q타입으로 컴파일 시점 검증, null 조건 처리 간결 |
| `OrderRepository` 기간 분기 | 메서드 2개 + 서비스 if | `where(null 무시)` 로 한 쿼리, DTO 프로젝션 가능 |

도입은 보류한다. 동적 조건이 두 곳뿐이라 Specification 으로 충분하고, QueryDSL 본가 마지막
릴리스(5.1.0)와 Spring Boot 4.1 / Hibernate 7 조합의 호환은 미검증이다. 조인·집계가 섞인
검색(리뷰 평균 정렬, 옵션 가격 범위 필터 등)이 생기면 그때 다시 검토한다.

## 2. 발견 사항

| 심각도 | 내용 | 조치 |
|---|---|---|
| P2 | `createOrder` 의 주문 상세가 IDENTITY + cascade 로 한 건씩 INSERT (MyBatis 는 multi-row) | 미조치. 상세 수가 적어 감수. 필요 시 `JdbcTemplate.batchUpdate` |
| P3 | `cancelOrder` 가 FOR UPDATE 조회 후 `orderDetails` 지연 로딩으로 SELECT 1회 추가 | 수정 |
| P3 | `findAllWithSubcategories` 의 `distinct` 가 SQL DISTINCT 로 전달 (Hibernate 6 은 fetch join 중복 자동 제거) | 수정 |
| P3 | 클라이언트가 정렬을 지정하면 id 보조 정렬이 빠져 페이지 경계가 흔들림 | 수정 |
| 참고 | `findProductById` 3쿼리는 이전 MyBatis 의 옵션×리뷰 카테시안 조인보다 낫다 | 유지 |

## 3. 수정 내용

### `repository/OrderRepository.java` — 잠금 조회에 상세 fetch join
`findByIdForUpdate` 를 `left join fetch o.orderDetails` 로 바꿔 취소 흐름이 잠금 1회 +
재고 1회, 총 2쿼리로 끝나게 했다. MySQL 은 조인된 `order_detail` 행도 함께 잠그지만 취소
흐름에서는 무해하다.

### `repository/MajorCategoryRepository.java` — `distinct` 제거
Hibernate 6 부터 JPQL `distinct` 는 SQL 로 그대로 내려가고, fetch join 의 부모 엔티티 중복은
항상 자동 제거된다. `description` 이 TEXT 컬럼이라 DISTINCT 정렬 비용만 더해지고 있었다.

### `common/PageableSupport.java` — id 보조 정렬 항상 부착
정렬이 없을 때만 `id` 를 붙이던 것을, 정렬이 있어도 `id` 가 없으면 맨 뒤에 `id` 보조 정렬을
붙이도록 바꿨다. `id` 가 이미 있거나 unpaged 면 그대로 돌려준다.

## 4. 테스트

- `repository/OrderRepositoryTest` — 잠금 조회 후 `PersistenceUnitUtil.isLoaded(order, "orderDetails")` 가
  true 인지 검증 추가 (지연 로딩이면 false)
- `repository/MajorCategoryRepositoryTest` — 기존 `size() == 8` 검증으로 `distinct` 제거 후에도
  중복이 없음을 확인
- `common/PageableSupportTest` 신규 — 정렬 없음 / 정렬 있음 / id 포함 / unpaged 네 경우

## 5. 미조치·제한

- 정렬 컬럼 허용 목록은 이번 범위에서 제외했다. 없는 속성을 `sort` 로 넘기면 여전히 예외가 난다.
- 쿼리 수 감소는 코드 읽기와 `isLoaded` 검증으로 확인했고, Hibernate 통계로 실측하지는 않았다.
