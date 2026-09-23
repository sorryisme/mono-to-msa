---
name: jpa-hibernate
description: JPA·Hibernate 엔티티·연관관계·영속성 컨텍스트·Spring Data JPA 쿼리·배치·캐시의 구현과 리뷰에 사용한다. 서비스의 엔티티 변경, 지연 로딩, 조회 성능 및 JPA 테스트도 포함한다.
---

# JPA · Hibernate 구현 및 리뷰

## 적용 범위와 공통 절차

작업 모드·시작 절차·공통 제약·리뷰 심각도와 보고 형식은 [java-spring](../java-spring/SKILL.md)을 읽고 따른다. 같은 작업에서 이미 읽었다면 다시 읽을 필요가 없다. 이 문서는 JPA·Hibernate 세부 기준을 보완하며 구현·리뷰 범위와 권한을 확대하지 않는다.

- Spring 트랜잭션 경계·프록시·롤백·전파, SQL 바인딩, DTO 경계, 페이지·커서 정렬, 인덱스·커넥션 풀·마이그레이션의 공통 기준은 `java-spring`에 둔다.
- Java·Spring Boot·Hibernate·DB 버전과 실제 설정을 확인하고, 작업과 관련된 아래 항목만 적용한다.
- 엔티티 변경이 서비스에서 일어나거나 DTO 매핑에서 지연 로딩이 발생하는 경로까지 확인한다. Repository 파일 변경 여부만으로 적용 범위를 제한하지 않는다.

## 참고 예시

핵심 규칙을 코드로 비교할 때는 [Good / Bad 예시](references/good-bad-examples.md)를 읽는다. 변경 감지, 연관관계 변경·삭제 전파, Fetch·N+1, 컬렉션 페이징·배치 조회, 벌크 갱신, 대량 처리, DB 검증, 상태 전이, Lombok, 컬렉션 관리, DTO 변환을 다룬다. 해당 작업과 관련된 예시만 적용한다.

## 영속성 컨텍스트와 트랜잭션

- 영속 상태 엔티티 변경은 트랜잭션 안에서 변경 감지로 반영될 수 있다. 신규·준영속 객체의 저장과 구분하고 기존 저장소 사용 관례를 따른다.
- 1차 캐시는 영속성 컨텍스트 범위다. 엔티티를 요청 간 공유하거나 장기간 보관하지 않는다.

## JPA 엔티티와 연관관계

- `@Entity`, `@Table`, 기본 키, null 허용, 길이, 유일 제약을 실제 스키마 및 요구사항과 일치시킨다. 키 생성 전략은 DB·부하·배치 요구에 맞춰 선택한다.
- JPA 엔티티에는 규약에 맞는 public 또는 protected 기본 생성자를 둔다. 프록시 사용과 식별자 변경을 고려해 클래스·메서드 및 `equals`·`hashCode`를 설계한다.
- enum은 특별한 사유가 없으면 `@Enumerated(EnumType.STRING)`을 사용한다. enum 이름을 바꿀 때 저장된 값의 마이그레이션도 고려한다.
- 변경 메서드는 의미 있는 동작을 표현하게 하고, 무분별한 setter로 도메인 불변식을 우회하지 않게 한다.
- 생성·수정 시각은 시간대 의미가 분명한 타입을 사용한다. `@CreatedDate`, `@LastModifiedDate`를 사용한다면 `AuditingEntityListener`와 `@EnableJpaAuditing` 등 실제 활성화 구성을 확인한다.
- 양방향 관계는 소유 측과 `mappedBy`를 일치시키고 연관관계 변경 시 양쪽 메모리 상태를 함께 관리한다. 컬렉션은 빈 컬렉션으로 초기화한다.
- 관계 조회는 LAZY를 기본 설계 방향으로 삼는다. 특히 기본 EAGER인 to-one 관계는 명시 설정과 제공자의 실제 지연 로딩 지원을 확인한다.
- `cascade = ALL`과 `orphanRemoval = true`는 부모가 자식의 생명주기를 소유할 때만 적용한다. 공유 엔티티까지 삭제 전파가 번지지 않도록 한다.
- 다대다 관계에 속성·수명주기가 필요하다면 명시적인 연결 엔티티를 고려한다.
- 소프트 삭제는 요구가 있을 때만 도입한다. 일반·네이티브 조회, 연관관계, 유일 제약, 복구, 감사 기록에 같은 정책이 적용되는지 확인한다.

## 저장소와 쿼리

- 표준 CRUD는 `JpaRepository` 또는 `CrudRepository`를 사용한다. 복잡한 조건은 `@Query`, Criteria API 또는 프로젝트가 이미 사용하는 쿼리 도구로 구현한다.
- 읽기 전용 화면·목록은 필요한 필드만 조회하는 DTO·인터페이스 프로젝션을 고려한다. 프로젝션 사용만으로 최적화를 단정하지 말고 생성 SQL을 확인한다.
- LAZY 설정만으로 N+1이 해결되지는 않는다. 실제 순회·매핑 경로에서 쿼리 수를 확인하고 필요한 관계만 fetch join, entity graph, 배치 조회 등으로 가져온다.
- 조회 한 번을 줄이기 위해 모든 관계를 EAGER 또는 fetch join으로 확장하지 않는다. 여러 컬렉션의 동시 조회는 행 수 폭증과 제공자 제약을 확인한다.
- 컬렉션 fetch join과 페이지 제한을 무심코 결합하지 않는다. DB 페이징 적용 여부를 확인하고 필요하면 ID 페이지 조회 후 관계 조회, 프로젝션 또는 배치 조회로 나눈다. [Hibernate 조회 지침](https://docs.hibernate.org/orm/7.4/querylanguage/html_single/#explicit-fetch-join)
- 벌크 update·delete는 영속성 컨텍스트와 엔티티 콜백을 거치지 않을 수 있다. 적용 전 flush 필요성, 적용 후 상태 불일치, clear 시 미반영 변경 손실 가능성을 함께 검토한다.

## 배치와 캐시

- `saveAll` 호출만으로 JDBC 배치 실행이 보장되지는 않는다. `hibernate.jdbc.batch_size`, SQL 형태, 키 생성 전략, 드라이버 설정을 확인한다. Hibernate의 IDENTITY 기반 insert는 JDBC insert 배치에 제약이 있다. 대량 처리 시 적절한 청크와 flush·clear가 메모리 및 원자성에 미치는 영향을 검토한다. [Hibernate 배치 지침](https://docs.hibernate.org/orm/7.4/userguide/html_single/#batch-session-batch)
- 2차 캐시는 반복 조회와 비용이 측정된 경우 고려한다. 데이터 변경 빈도, 외부 갱신, 무효화, 동시성 전략을 확인하고 쿼리 캐시와 구분한다.
- PostgreSQL LOB 관련 `hibernate.jdbc.lob.non_contextual_creation`은 Hibernate·드라이버 버전과 실제 LOB 문제를 확인할 때만 검토한다. 모든 PostgreSQL 프로젝트에 일괄 추가하지 않는다.

## 스키마 매핑과 변경

- 엔티티의 인덱스·유일 제약 어노테이션만으로 운영 DB가 바뀌었다고 판단하지 않는다. 마이그레이션과 실제 스키마를 확인한다.
- 스키마 변경 절차와 배포 호환성은 [java-spring](../java-spring/SKILL.md)의 "스키마 변경과 마이그레이션" 절을 따른다.

## JPA 검증

- JPA 계층은 `@DataJpaTest`를 사용하고, 어노테이션과 import는 프로젝트 버전을 따른다.
- `@DataJpaTest`의 DB 대체 설정을 확인하고 실제 대상 DB로 연결됐는지 확인한다. DB 제약·잠금·격리 수준은 [java-spring](../java-spring/SKILL.md)의 "로깅과 검증" 절에 있는 실제 DB 검증 기준을 따른다.
- SQL 로그는 진단 보조 수단이다. 필요하면 Hibernate 통계·쿼리 수·실행 계획·지연 시간을 함께 측정한다.
- `org.hibernate.SQL=DEBUG`는 SQL 확인에 사용할 수 있다. Hibernate 6 이상(7 포함)의 바인딩 로거는 `org.hibernate.orm.jdbc.bind=TRACE`지만 사용 버전을 먼저 확인한다. 바인딩 값에 민감 정보가 포함될 수 있으므로 제한된 테스트·개발 환경에서만 필요한 시간 동안 사용한다.

## 사용 예시

```text
/jpa-hibernate 주문 목록의 N+1과 컬렉션 페이징을 리뷰해줘.
서비스에서 응답 DTO로 매핑하는 경로까지 확인하고 코드는 수정하지 마.
```

```text
/jpa-hibernate 상품 엔티티와 Repository를 수정해줘.
기존 스키마와 트랜잭션 경계를 유지하고 관련 JPA 테스트를 실행해줘.
```

공식 문서는 해당 프로젝트의 버전에 맞춰 확인한다. 이 문서의 Hibernate 7.4 링크는 세부 동작을 확인한 참고 자료이며 의존성 버전 고정을 지시하지 않는다.
