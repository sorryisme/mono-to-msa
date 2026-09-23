# Java · Spring 핵심 Good / Bad 예시

각 예시는 명시한 조건에서의 비교다. `❌ Bad`를 모든 상황의 금지 규칙으로 확대하지 않는다. 예시의 클래스·메서드·테이블은 설명용이며, import·생성자·부가 필드는 일부 생략했다. 저장소의 실제 구현이나 실행·성능 검증 결과를 뜻하지 않는다.

## 1. 필수 의존성은 생성자로 주입

조건: 일반적인 Spring 서비스 빈의 필수 의존성. JPA 엔티티 생성 규약과 구분한다.

```java
// ❌ Bad: 필수 의존성이 생성 시점에 드러나지 않고 수동 생성 시 null로 남는다.
@Service
class OrderService {
    @Autowired
    private OrderRepository orders;
}

// ✅ Good: 생성 시 필요한 의존성을 명시하고 참조 재할당을 막는다.
@Service
class OrderService {
    private final OrderRepository orders;

    OrderService(OrderRepository orders) {
        this.orders = orders;
    }
}
```

기존 생성자 생성 도구를 사용해도 된다. 필드 주입만으로 기능 장애라고 판정하지 않는다.

## 2. 입력 검증과 응답 DTO

조건: 이름은 null·공백을 허용하지 않고 최대 50자이며, 응답에는 공개 필드만 포함한다.

```java
// ❌ Bad: @Size는 null을 거절하지 않으며, 엔티티 반환은 내부 필드를 노출할 수 있다.
record CreateUserRequest(@Size(max = 50) String name) {}

@PostMapping("/users")
User create(@RequestBody CreateUserRequest request) {
    return users.create(request.name());
}

// ✅ Good: DTO 제약을 @Valid로 적용하고 응답 필드를 명시한다.
record CreateUserRequest(@NotBlank @Size(max = 50) String name) {}
record UserResponse(Long id, String name) {}

@PostMapping("/users")
@ResponseStatus(HttpStatus.CREATED)
UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return users.createResponse(request.name());
}
```

위 예시는 반환 데이터 경계에 집중한다. 실제 구현에서는 저장소의 응답 봉투·오류 계약과 인증·인가 규칙을 함께 따른다.

## 3. 입력값을 쿼리 문자열에 붙이지 않기

조건: 외부 입력인 이메일로 회원을 조회한다. 값은 바인딩하고 정렬 필드 같은 식별자는 별도로 허용 목록을 적용한다.

```java
// ❌ Bad: 입력값이 JPQL 문법으로 해석될 수 있다.
em.createQuery("select u from User u where u.email = '" + email + "'", User.class)
    .getResultList();

// ✅ Good: 쿼리 구조와 입력값을 분리한다.
em.createQuery("select u from User u where u.email = :email", User.class)
    .setParameter("email", email)
    .getResultList();
```

## 4. 자기 호출에 트랜잭션 생성을 기대하지 않기

조건: 기본 프록시 방식이며 호출자가 트랜잭션을 시작하지 않았다. 아래 `writeOrderAndLines`는 같은 DB에 주문과 상세를 저장한다.

```java
// ❌ Bad: 같은 빈의 자기 호출은 save()의 트랜잭션 프록시를 통과하지 않는다.
public void placeOrder(Command command) {
    this.save(command);
}

@Transactional
public void save(Command command) {
    writeOrderAndLines(command);
}

// ✅ Good: 외부 빈이 호출하는 유스케이스 진입점에서 트랜잭션을 시작한다.
@Transactional
public void placeOrder(Command command) {
    writeOrderAndLines(command);
}
```

호출은 Spring이 관리하는 서비스 프록시를 통해 들어와야 한다. 별도 트랜잭션 경계가 실제로 필요할 때만 다른 빈이나 `TransactionTemplate`을 고려한다. 외부 결제까지 DB 트랜잭션으로 원자성이 보장되는 것은 아니다. [Spring 트랜잭션 프록시 문서](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 5. 깊은 OFFSET과 커서 페이징

조건: 대용량 로그의 다음 페이지를 순서대로 조회하며, `id`는 인덱스가 있는 고유 키다.

```sql
-- ❌ Bad: 깊은 페이지에서는 앞의 많은 행을 건너뛰는 작업이 반복된다.
SELECT id, created_at, message
FROM request_logs
ORDER BY id DESC
LIMIT 10 OFFSET 100000;

-- ✅ Good: 직전 페이지의 마지막 id 이후 범위부터 조회한다.
-- :last_id는 직전 페이지 마지막 행의 id이며, SQL 바인딩 값이다.
SELECT id, created_at, message
FROM request_logs
WHERE id < :last_id
ORDER BY id DESC
LIMIT 10;
```

첫 페이지는 커서 조건 없이 조회한다. 커서 방식은 임의 페이지 번호 이동을 그대로 대체하지 않으며, 작은 OFFSET까지 금지하지 않는다. 실제 인덱스·실행 계획·지연 시간으로 효과를 확인한다.

복합 정렬에서는 필터와 커서의 묶음도 보존한다. 아래는 `created_at`이 NOT NULL이고 정렬 키가 변하지 않는 조건이다.

```sql
-- ❌ Bad: AND가 OR보다 먼저 결합되어 다른 사용자의 행도 포함될 수 있다.
WHERE user_id = :user_id AND created_at < :last_created_at
   OR (created_at = :last_created_at AND id < :last_id)

-- ✅ Good: 커서 전체에 사용자 필터를 적용하고 고유 키로 순서를 확정한다.
WHERE user_id = :user_id
  AND (created_at < :last_created_at
       OR (created_at = :last_created_at AND id < :last_id))
ORDER BY created_at DESC, id DESC
LIMIT 10;
```

조회 조건에 맞는 `(user_id, created_at, id)` 등의 인덱스를 검토하되 일괄 추가하지 않는다. 고유 보조 정렬 키의 필요성은 [MySQL LIMIT·정렬 문서](https://dev.mysql.com/doc/refman/8.4/en/limit-optimization.html)도 참고한다.

## 6. 민감 정보가 포함된 객체를 통째로 기록하지 않기

조건: 요청 객체에 비밀번호·토큰 등 민감 필드가 포함될 수 있다.

```java
// ❌ Bad: toString() 구현에 따라 비밀번호·토큰까지 기록된다.
log.info("회원 가입 요청: {}", request);

// ✅ Good: 로그 정책상 허용된 처리 결과와 추적용 식별자만 기록한다.
log.info("회원 가입 완료: userId={}", createdUserId);
```

파라미터 로깅 자체가 마스킹을 제공하지 않는다. 식별자도 저장소의 개인정보·로그 보존 정책에 맞춰 선택한다.

## 7. 조회 기본값과 쓰기 트랜잭션을 구분

조건: 클래스 기본값을 readOnly로 두는 서비스이며, 외부 빈에서 프록시를 통해 호출한다.

```java
// ❌ Bad: 클래스의 readOnly 기본값을 상속한 메서드에서 쓰기를 수행한다.
@Service
@Transactional(readOnly = true)
class MemberService {
    public Long join(String name) {
        return members.save(new Member(name)).getId();
    }
}

// ✅ Good: 조회는 기본값을 사용하고 쓰기 유스케이스는 명시적으로 재정의한다.
@Service
@Transactional(readOnly = true)
class MemberService {
    public MemberResponse find(Long id) {
        return members.findResponseById(id).orElseThrow(); // DTO 프로젝션 조회
    }

    @Transactional
    public Long join(String name) {
        return members.save(new Member(name)).getId();
    }
}
```

조회에 일반 `@Transactional`을 사용한 사실만으로 Bad라고 판단하지 않는다. readOnly는 최적화 힌트이며 쓰기 차단 보장이 아니다. 기본 전파 REQUIRED에서 이미 외부 트랜잭션이 있다면 그 속성이 우선하므로 메서드 어노테이션만으로 읽기 전용 트랜잭션이 쓰기로 전환된다고 기대하지 않는다. 자기 호출에도 재정의가 적용되지 않는다. 읽기/쓰기 DB 라우팅을 사용하면 replica 선택과 조회 정합성도 함께 확인한다. [Spring Data 트랜잭션 문서](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
