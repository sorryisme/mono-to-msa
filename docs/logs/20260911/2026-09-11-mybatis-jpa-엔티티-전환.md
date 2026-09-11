# MyBatis → JPA 전환 1단계: 엔티티 매핑

작업 브랜치: `refactor/mybatis-to-jpa`

## 배경과 범위

MyBatis 기반 영속성 계층을 JPA 로 전환하는 첫 단계로, **엔티티 매핑만** 도입했다.
기존 `domain/` 패키지와 MyBatis 매퍼·XML·서비스는 전혀 건드리지 않았고, 리포지토리와 서비스 전환은 다음 단계로 남긴다.

## 결정 사항

| 항목 | 선택 | 이유 |
|---|---|---|
| 엔티티 위치 | `entity/` 패키지 신설 (`~Entity` 접미사) | `domain/` 은 MyBatis resultMap 대상이라 그대로 두고 점진 전환. 전환 완료 후 접미사를 떼고 `domain/` 을 제거하면 된다 |
| 연관관계 | 부모-자식만 객체 연관, 나머지는 FK 값 | `Order↔OrderDetail`, `Cart↔CartDetail` 만 `@OneToMany`/`@ManyToOne`. `User`·`ProductOption` 참조는 `Long` FK 로 유지해 서비스 변경 범위를 줄임 |
| 의존성 | `spring-boot-starter-data-jpa` 추가 | 매핑이 실제 스키마와 맞는지 이번 단계에서 검증하기 위함 |
| 스키마 관리 | `ddl-auto: none` | 스키마는 `schema.sql` 이 관리한다. Hibernate 가 운영 스키마를 바꾸지 않게 고정 |

## 변경 내용

### 의존성 · 설정
- `build.gradle`: `spring-boot-starter-data-jpa`, `spring-boot-data-jpa-test`(Boot 4 부터 `@DataJpaTest` 슬라이스가 별도 모듈) 추가
- `src/{main,test}/resources/application.yml`: `spring.jpa.hibernate.ddl-auto=none`, `open-in-view=false`, `format_sql=true`
- 기존 `@Primary` `LazyConnectionDataSourceProxy`(master/replica 라우팅) 위에 EntityManagerFactory 가 그대로 올라간다. MyBatis 도 같은 DataSource 를 쓰므로 `JpaTransactionManager` 하나로 양쪽 트랜잭션이 동작한다

### 신규 엔티티 (`com.sorryisme.fmarket.entity`)
`BaseTimeEntity`(공통 `created_at`/`updated_at`), `UserEntity`, `StoreEntity`, `ProductEntity`, `ProductOptionEntity`, `ProductReviewEntity`, `InventoryEntity`, `OrderEntity`, `OrderDetailEntity`, `CartEntity`, `CartDetailEntity`

공통 규약:
- `@NoArgsConstructor(access = PROTECTED)` + `private` 전체 생성자에 `@Builder` — 무분별한 setter 대신 `updateProfile`, `changeStatus`, `decrease` 같은 의미 있는 변경 메서드만 노출
- `@GeneratedValue(strategy = IDENTITY)` — MySQL AUTO_INCREMENT 에 맞춤
- `created_at`/`updated_at` 은 DB 기본값(`CURRENT_TIMESTAMP`, `ON UPDATE CURRENT_TIMESTAMP`)이 채우므로 `insertable = false, updatable = false` 읽기 전용 매핑
- enum 은 `@Enumerated(EnumType.STRING)` (`OrderStatus`, 신규 `UserRole`)
- to-one 연관은 모두 `FetchType.LAZY`, 컬렉션은 빈 리스트로 초기화하고 `getter` 는 불변 뷰 반환 + 연관관계 편의 메서드(`addOrderDetail`, `addCartDetail`)로 양쪽 상태를 함께 관리

### 기존 도메인과 달라진 점 (의도된 수정)
- `order`, `user` 는 MySQL 예약어라 `@Table(name = "\"order\"")` 형태로 인용
- `Inventory` 도메인에는 PK 필드가 없었으나 테이블에 `id` PK 가 있어 엔티티에서는 이를 식별자로 매핑
- `Product.product_name` → `productName` (`@Column(name = "product_name")`)
- `Store.storeId`, `ProductReview.reviewId` → 필드명 `id` + `@Column` 으로 컬럼명 유지
- `User.createdAt/updatedAt` 이 `String` 이었으나 `LocalDateTime` 으로 매핑
- 스키마에는 있으나 도메인에 없던 `user.role` 을 `UserRole` enum 으로 추가

## 검증

- `./gradlew test` — 62개 테스트 전부 통과 (기존 MyBatis 매퍼/서비스 테스트 + 전체 컨텍스트 스모크 테스트 포함). JPA 추가 후에도 기존 테스트가 깨지지 않음을 확인
- `EntityMappingTest`(Spock, `@DataJpaTest` + 실제 MySQL): `spring.jpa.hibernate.ddl-auto=validate` 로 컨텍스트를 띄워 **모든 엔티티의 컬럼 매핑이 `schema.sql` 과 일치하는지 검증**. 추가로 식별자 생성, `Order`→`OrderDetail` cascade 저장, `Cart` 상세의 `orphanRemoval` 삭제를 확인
- `./gradlew spotlessCheck` 통과

## 남은 작업 / 미검증 사항

- 리포지토리(`JpaRepository`) 및 서비스·매퍼 전환은 미착수. 현재 엔티티는 애플리케이션 런타임 경로에서 사용되지 않는다
- `payment`, `major_category`, `subcategory` 테이블은 기존 `domain/` 에 대응 클래스가 없어 이번 범위에서 제외
- `Product ↔ ProductOption` 은 DB 상 `ON DELETE CASCADE` 지만 이번 결정(부모-자식만 연관)에 따라 FK 필드로 유지. 필요하면 다음 단계에서 연관으로 승격 가능
- 읽기/쓰기 DB 라우팅(`@Transactional(readOnly = true)` → replica)이 JPA 경로에서도 의도대로 동작하는지는 리포지토리 도입 후 확인 필요
- `equals`/`hashCode` 는 재정의하지 않았다. 현재 컬렉션이 모두 `List` 이고 영속성 컨텍스트 내 동일성으로 충분하다. `Set` 사용이나 준영속 비교가 생기면 식별자 기반으로 추가해야 한다
