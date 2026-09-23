# JPA · Hibernate 핵심 Good / Bad 예시

각 예시는 명시한 조건에서의 비교다. `❌ Bad`를 모든 상황의 금지 규칙으로 확대하지 않는다. 예시의 엔티티·필드·메서드는 설명용이며, import·생성자·부가 필드는 일부 생략했다. 저장소의 실제 구현이나 실행·성능 검증 결과를 뜻하지 않는다. 공통 트랜잭션·SQL 바인딩·커서 페이징은 [Java · Spring 예시](../../java-spring/references/good-bad-examples.md)를 참고한다.

## 1. 변경 감지는 영속 상태와 트랜잭션을 전제로 판단

조건: OSIV가 꺼져 있고 호출자 트랜잭션도 없다. 서비스 메서드는 외부 빈에서 Spring 프록시를 통해 호출한다.

```java
// ❌ Bad: Repository 조회가 끝난 뒤 준영속 객체만 변경하고 저장을 기대한다.
public void rename(Long id, String name) {
    Product product = products.findById(id).orElseThrow();
    product.rename(name);
}

// ✅ Good: 조회와 변경을 하나의 쓰기 트랜잭션으로 묶는다.
@Transactional
public void rename(Long id, String name) {
    Product product = products.findById(id).orElseThrow();
    product.rename(name);
}
```

Good 예시에서는 관리 중인 엔티티의 변경이 flush 시 반영된다. 신규·준영속 객체에도 무조건 `save()`가 불필요하다는 뜻이 아니다.

관리 중인 엔티티에 `save()`를 호출했다는 이유만으로 결함이나 추가 UPDATE 발생을 단정하지 않는다. 변경 감지에 필수는 아니지만 Repository 추상화에 맞춘 프로젝트 관례일 수 있다. [Spring Data 트랜잭션 문서](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)

## 2. 연관관계 소유 측과 삭제 전파

조건: `Order.lines`는 `mappedBy = "order"`, `OrderLine.order`는 외래 키를 관리하는 소유 측이다.

```java
// ❌ Bad: 역방향 컬렉션만 변경하면 외래 키가 설정되지 않는다.
order.getLines().add(line);

// ✅ Good: Order 내부 편의 메서드에서 양쪽 상태를 함께 설정한다.
public void addLine(OrderLine line) {
    lines.add(line);
    line.assignOrder(this);
}
```

새 `OrderLine`을 실제로 저장하려면 명시적 persist 또는 의도한 cascade 설정도 필요하다. 기존 주문 사이의 이동은 이 예시가 다루지 않으며 이전 컬렉션과 orphanRemoval 정책까지 확인해야 한다.

```java
// ❌ Bad: 여러 주문 상세가 공유하는 상품에 삭제를 전파한다.
@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
private Product product;

// ✅ Good: 공유 상품의 생명주기를 주문 상세와 분리한다.
@ManyToOne(fetch = FetchType.LAZY)
private Product product;
```

부모가 자식의 생명주기를 실제로 소유하는 관계라면 cascade·orphanRemoval은 적절할 수 있다.

### 기존 관계를 다른 대상으로 변경

조건: 회원은 팀 없이 존재할 수 있고 팀 간 이동이 가능하다. `Member.team`이 소유 측이고, `Team.members`에는 orphanRemoval·삭제 cascade가 없다. 아래 컬렉션 접근은 같은 영속성 컨텍스트 안에서 이루어진다.

```java
// ❌ Bad: 이전 팀의 컬렉션에는 회원이 그대로 남는다.
public void changeTeam(Team next) {
    this.team = next;
    next.getMembers().add(this);
}

// ✅ Good: 이전 관계를 제거하고 새 관계의 양쪽 상태를 맞춘다.
public void changeTeam(Team next) {
    if (this.team == next) {
        return;
    }
    if (this.team != null) {
        this.team.getMembers().remove(this);
    }
    this.team = next;
    if (next != null) {
        next.getMembers().add(this);
    }
}
```

이 예시의 참조 비교는 같은 영속성 컨텍스트에서 가져온 객체라는 전제다. 준영속 객체를 섞는 경우의 동일성 판단으로 일반화하지 않는다. 컬렉션은 초기화되어 있어야 하며, 편의 메서드끼리 서로를 무한 호출하지 않게 한다. orphanRemoval 관계의 자식 이동에 이 코드를 그대로 적용하지 않는다.

## 3. LAZY만으로 N+1이 해결되지는 않음

### 기본 Fetch 전략

조건: 모든 조회에서 팀 정보가 필요한 것은 아니며, 조회별로 필요한 데이터를 결정한다.

```java
// ❌ Bad: 기본 EAGER를 인식하지 못한 채 불필요한 관계 로딩을 허용한다.
@ManyToOne
private Team team;

// ✅ Good: LAZY를 기본 설계 방향으로 두고 필요한 조회에 로딩 계획을 지정한다.
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "team_id")
private Team team;
```

`@ManyToOne`과 `@OneToOne`의 기본값은 EAGER지만 항상 SQL JOIN 한 번으로 조회된다는 뜻은 아니다. LAZY는 스펙상 힌트이며 특히 `@OneToOne`은 소유 측·프록시·바이트코드 향상 등 제공자 지원을 확인한다. EAGER 설정 자체를 무조건 결함으로 취급하지 않는다. [Jakarta OneToOne 문서](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/onetoone)

### 조회별 fetch join과 EntityGraph

조건: 목록에서 모든 주문의 회원 이름이 필요하다. `Order.user`는 to-one LAZY이며 캐시·배치 조회가 적용되지 않은 상태다.

```java
// ❌ Bad: 서로 다른 user 프록시를 초기화하면서 추가 SELECT가 발생할 수 있다.
List<Order> orders = em.createQuery(
    "select o from Order o where o.status = :status", Order.class)
    .setParameter("status", status)
    .getResultList();
orders.forEach(order -> consume(
    order.getUser() == null ? null : order.getUser().getName()));

// ✅ Good: 이 조회에서 필요한 to-one 관계만 함께 가져온다.
List<Order> orders = em.createQuery(
    "select o from Order o left join fetch o.user where o.status = :status", Order.class)
    .setParameter("status", status)
    .getResultList();
orders.forEach(order -> consume(
    order.getUser() == null ? null : order.getUser().getName()));
```

다른 지연 관계까지 초기화되는지는 DTO 매핑 경로에서 확인한다. 글로벌 EAGER 전환으로 해결하지 않는다. 위 목록은 조회 전략 설명용이며 실제 API에서는 반환량도 제한한다.

Spring Data JPA에서는 다음과 같이 해당 조회의 로딩 계획을 선언할 수도 있다.

```java
// ✅ Good: 전체 findAll의 의미를 바꾸지 않고 필요한 조회에만 to-one 관계를 지정한다.
@EntityGraph(attributePaths = "user")
List<Order> findByStatus(OrderStatus status, Pageable pageable);
```

EntityGraph는 필요한 속성의 로딩 계획이다. 생성 SQL·쿼리 수는 실제로 확인하며, 컬렉션을 넣으면 페이징 문제가 자동으로 해결된다고 가정하지 않는다.

## 4. 컬렉션 fetch join과 페이지 제한을 분리

조건: 주문 20개와 각 주문의 상세가 필요하다. `lines`는 to-many 관계다.

```java
// ❌ Bad: 컬렉션 fetch join에 limit을 결합하면 메모리 페이징 또는 설정에 따른 예외가 발생할 수 있다.
List<Order> orders = em.createQuery(
    "select o from Order o left join fetch o.lines order by o.id desc", Order.class)
    .setMaxResults(20)
    .getResultList();

// ✅ Good: 먼저 주문 ID 20개를 DB에서 제한하고 그 범위의 상세를 가져온다.
List<Long> ids = em.createQuery(
    "select o.id from Order o order by o.id desc", Long.class)
    .setMaxResults(20)
    .getResultList();
if (ids.isEmpty()) {
    return List.of();
}
return em.createQuery(
    "select distinct o from Order o left join fetch o.lines "
        + "where o.id in :ids order by o.id desc", Order.class)
    .setParameter("ids", ids)
    .getResultList();
```

두 번째 조회의 `IN`이 입력 순서를 보장하지 않으므로 같은 정렬을 명시했다. 복합 정렬이면 모든 키를 일치시킨다. 두 조회 사이 데이터 변경의 허용 범위·격리 수준과 주문당 상세 수 폭증도 확인한다. [Hibernate fetch join 지침](https://docs.hibernate.org/orm/7.4/querylanguage/html_single/#explicit-fetch-join)

### 대안: 루트 페이징과 컬렉션 배치 조회

조건: `Order.lines`는 LAZY이며, 서비스의 조회 트랜잭션 안에서 같은 페이지의 컬렉션을 접근한다. `OrderSummary.from`은 상세를 읽어 DTO에 복사하며 엔티티를 DTO에 보관하지 않는다.

```properties
# 예시값이며 권장 기본값이 아니다. 페이지 크기·행 수·메모리·쿼리 수로 조정한다.
spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

```java
// ✅ Good: 루트에만 DB 페이징을 적용하고 컬렉션 초기화는 배치 조회로 묶는다.
@Transactional(readOnly = true)
public Page<OrderSummary> findOrders(Pageable pageable) {
    Page<Order> page = orders.findAll(pageable); // 컬렉션 fetch join/graph 없음
    return page.map(OrderSummary::from);
}
```

`pageable`에는 최대 크기와 고유 보조 정렬을 적용한다. 전체 개수가 필요하지 않으면 별도의 Slice 조회를 고려한다. 배치 크기는 자식 행 수의 상한이 아니므로 한 주문의 상세가 많으면 메모리 부담은 여전히 크다. 쿼리 수를 고정된 2회로 단정하지 않는다. 이 설정은 조회 배치이며 쓰기의 `hibernate.jdbc.batch_size`와 다르다. [Hibernate 배치 조회](https://docs.hibernate.org/orm/7.4/userguide/html_single/#fetching-batch)

## 5. 벌크 갱신 후 영속성 컨텍스트 동기화

조건: 이미 조회한 상품의 가격을 JPQL 벌크 update로 변경한다. 아래 코드는 쓰기 트랜잭션 안에서 실행한다.

```java
// ❌ Bad: DB만 갱신된 뒤 기존 관리 객체에서 최신 가격을 기대한다.
Product product = em.find(Product.class, id);
em.createQuery("update Product p set p.price = :price where p.id = :id")
    .setParameter("price", newPrice)
    .setParameter("id", id)
    .executeUpdate();
return product.getPrice(); // 메모리에는 이전 값이 남아 있을 수 있다.

// ✅ Good: 미반영 변경을 먼저 반영하고 벌크 갱신 후 다시 조회한다.
em.flush();
em.createQuery("update Product p set p.price = :price where p.id = :id")
    .setParameter("price", newPrice)
    .setParameter("id", id)
    .executeUpdate();
em.clear();
return em.find(Product.class, id).getPrice();
```

`clear()`는 해당 상품뿐 아니라 전체 영속성 컨텍스트를 비운다. 기존 엔티티 참조는 준영속이 되므로 재사용을 점검한다. 벌크 쿼리에 엔티티 콜백·버전 검사가 자동으로 적용된다고 가정하지 않는다.

Spring Data JPA의 `@Query` 메서드에서는 같은 의도를 다음과 같이 표현할 수 있다. 아래 두 예시는 모두 호출 서비스의 쓰기 트랜잭션 안에서 실행한다.

```java
// ❌ Bad: 벌크 갱신 이후에도 기존 관리 객체가 자동 갱신된다고 기대한다.
@Modifying
@Query("update Product p set p.price = :price where p.id = :id")
int updatePrice(@Param("id") Long id, @Param("price") BigDecimal price);

// ✅ Good: 선행 변경을 flush하고 갱신 후 컨텍스트를 비우도록 명시한다.
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update Product p set p.price = :price where p.id = :id")
int updatePrice(@Param("id") Long id, @Param("price") BigDecimal price);
```

옵션 없는 `@Modifying` 자체가 결함은 아니다. 벌크 작업 후 기존 상태를 사용하지 않는 경로라면 자동 clear가 불필요할 수 있다. `@Modifying`은 트랜잭션을 시작하지 않으며, 옵션을 켜도 기존 Java 객체의 값이 바뀌는 것은 아니므로 필요하면 다시 조회한다. [Modifying API](https://docs.spring.io/spring-data/jpa/docs/current/api/org/springframework/data/jpa/repository/Modifying.html)

## 6. saveAll과 메모리 관리·JDBC 배치를 구분

조건: 많은 신규 엔티티를 저장한다. 아래 `source`는 미리 전체 목록을 적재하지 않는 입력이며 쓰기 트랜잭션 안에서 소비한다.

```java
// ❌ Bad: 대량 목록 전체를 보관하고 saveAll만으로 JDBC 배치가 보장된다고 가정한다.
List<Product> all = loadAllProductsIntoMemory();
products.saveAll(all);

// ✅ Good: 영속성 컨텍스트에 쌓이는 엔티티 수를 제한한다.
int count = 0;
for (Product product : source) {
    em.persist(product);
    if (++count % 100 == 0) {
        em.flush();
        em.clear();
    }
}
em.flush();
em.clear();
```

100은 설명용 청크 크기다. 이 Good 예시는 메모리 관리이며 JDBC 배치 성공 예시가 아니다. 배치는 `hibernate.jdbc.batch_size`, SQL 형태, 드라이버·키 전략을 별도로 확인한다. IDENTITY insert는 Hibernate JDBC insert 배치에 제약이 있다. flush·clear는 커밋이 아니므로 긴 트랜잭션의 락 점유를 해결하지 않으며, 청크별 커밋은 별도의 원자성 결정이다. [Hibernate 배치 지침](https://docs.hibernate.org/orm/7.4/userguide/html_single/#batch-session-batch)

## 7. mock 호출 검증으로 DB 저장을 증명하지 않기

조건: 상품명 변경이 실제 DB에 반영되는지를 검증한다. Good 예시는 운영과 같은 종류의 격리된 테스트 DB를 사용하고, 테스트 트랜잭션 안에서 실행한다.

```java
// ❌ Bad: mock 호출 여부로 변경 감지·SQL 실행까지 검증했다고 보고한다.
verify(products).findById(id);

// ✅ Good: 실제 엔티티를 변경한 뒤 flush·clear하고 DB에서 다시 읽는다.
Product product = em.find(Product.class, id);
product.rename("변경된 이름");
em.flush();
em.clear();
assertEquals("변경된 이름", em.find(Product.class, id).getName());
```

fixture는 DB에 존재해야 한다. 이 검증은 매핑·변경 감지·SQL 반영 범위이며, 서비스 프록시·실제 커밋 이후 가시성·동시성을 증명하지 않는다. 각각은 해당 경계를 통과하는 별도 검증이 필요하다.

## 8. Setter 대신 허용된 상태 전이를 표현

조건: 이 예시에서는 PAID 상태의 주문만 취소할 수 있다. 실제 프로젝트의 상태 이름과 취소 정책을 확인해서 적용한다.

```java
// ❌ Bad: 상태 전이 검증 없이 어떤 주문이든 취소 상태로 바꾼다.
order.setStatus(OrderStatus.CANCELLED);

// ✅ Good: 엔티티의 동작에서 허용된 전이를 확인한다.
public void cancel() {
    if (status != OrderStatus.PAID) {
        throw new IllegalStateException("결제 완료 상태에서만 취소할 수 있습니다.");
    }
    status = OrderStatus.CANCELLED;
}
```

메서드 이름만 바꾸고 public setter를 그대로 열어 두면 우회가 가능하다. 이 검증은 객체 내부 규칙이며 동시 요청의 충돌을 해결하지 않는다. 필요한 락·버전 검사와 외부 환불의 정합성은 별도로 설계한다.

## 9. Lombok 자동 생성 범위를 엔티티에 맞추기

조건: Member와 Team이 양방향으로 연결되어 있다. 양쪽에서 연관 필드를 포함해 toString·equals/hashCode를 생성하면 순환 참조나 불필요한 지연 로딩이 발생할 수 있다.

```java
// ❌ Bad: 연관관계까지 포함하는 메서드와 무분별한 setter를 일괄 생성한다.
@Data
@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;
}

// ✅ Good: 필요한 접근자·기본 생성자만 생성하고 출력 필드를 명시한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@Entity
public class Member {
    @Id @GeneratedValue
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;
}
```

JPA 기본 생성자는 public 또는 protected여야 하며, protected는 직접 생성을 제한하는 관례다. 위 Good 예시는 `equals/hashCode`를 생성하지 않는다. ID 할당 전후·프록시·Set 사용을 고려한 동등성이 필요하면 별도로 설계한다. `@ToString` 설정만으로 `@Data`의 동등성 문제까지 해결했다고 보지 않는다. [Lombok Data](https://projectlombok.org/features/Data), [ToString](https://projectlombok.org/features/ToString)

## 10. 컬렉션은 초기화하고 참조 교체 대신 관계를 변경

조건: Team.members는 역방향이며, 회원은 독립적으로 존재한다. orphanRemoval은 사용하지 않고 `Member.changeTeam`은 2절의 양쪽 관계 갱신 메서드다.

```java
// ❌ Bad: null 상태로 시작하고, 관리 중인 컬렉션을 통째로 바꾼다.
@OneToMany(mappedBy = "team")
private List<Member> members;

public void replaceMembers(List<Member> replacement) {
    this.members = replacement;
}

// ✅ Good: 초기화된 컬렉션 참조를 유지하고 관계 소유 측도 함께 변경한다.
@OneToMany(mappedBy = "team")
private List<Member> members = new ArrayList<>();

public void removeMember(Member member) {
    if (member.getTeam() != this) {
        throw new IllegalArgumentException("이 팀에 소속된 회원이 아닙니다.");
    }
    member.changeTeam(null);
}
```

관리 컬렉션의 교체는 매핑·orphanRemoval 설정에 따라 추적·삭제 동작이나 예외에 영향을 줄 수 있다. 항상 특정 PersistentBag 오류가 난다고 단정하지 않는다. 역방향 컬렉션의 `clear()`만 호출해도 외래 키가 바뀌지 않으므로 전체 제거 시에도 소유 측을 갱신한다. 순회 중 컬렉션을 변경한다면 복사본을 순회하는 등 동시 수정도 피한다.

## 11. DTO 변환을 조회 경계 안에서 완료

조건: OSIV가 꺼져 있고 MemberResponse에는 팀 이름이 필요하다. Member.team은 LAZY이며 선택 관계다. 외부 빈이 아래 서비스를 프록시를 통해 호출한다.

```java
// ❌ Bad: 조회 트랜잭션이 끝난 엔티티에서 컨트롤러가 LAZY 관계를 읽는다.
@GetMapping("/members/{id}")
public MemberResponse find(@PathVariable Long id) {
    Member member = members.findById(id).orElseThrow();
    return MemberResponse.from(member); // 내부에서 member.getTeam().getName() 접근
}

// ✅ Good: 필요한 관계를 조회하고 서비스 트랜잭션 안에서 DTO를 만든다.
// Repository 메서드
@Query("select m from Member m left join fetch m.team where m.id = :id")
Optional<Member> findWithTeamById(@Param("id") Long id);

// Service 메서드
@Transactional(readOnly = true)
public MemberResponse find(Long id) {
    Member member = members.findWithTeamById(id).orElseThrow();
    Team team = member.getTeam();
    return new MemberResponse(member.getId(), member.getName(),
        team == null ? null : team.getName());
}
```

컨트롤러는 서비스가 반환한 DTO를 응답 계약에 맞춰 전달한다. 목록 조회라면 페이징과 생성 SQL도 확인한다. DTO 변환 자체는 N+1 해결책이 아니며 OSIV에 의존해 컨트롤러에서 관계 조회를 유발하지 않게 한다.
