# Spock → JUnit 5 테스트 전환

- 작업일: 2026-09-12
- 브랜치: `refactor/spock-to-junit`
- 범위: 테스트 코드 전체 (`src/test/groovy` → `src/test/java`), `build.gradle` 테스트 의존성, 테스트 관례 문서

## 1. 배경

테스트만 Groovy·Spock 이고 프로덕션 코드는 Java 였다. 언어·빌드 경로가 둘로 갈려 있어
Spotless(`src/**/*.java`) 포맷 대상에서 테스트가 빠지고, Groovy 컴파일 단계가 별도로 붙고,
Boot 4 업그레이드 때마다 `spock-spring` 의 Spring 버전 호환을 따로 확인해야 했다.
JUnit 5·Mockito·AssertJ 는 이미 `spring-boot-starter-test` 로 들어와 있어 새 의존성 없이 단일화가 가능했다.

## 2. 전환 전 기준선

전환을 시작하기 전 `./gradlew test` 를 실행해 기준선을 고정했다.

```
테스트 클래스 15개 / tests=90 skipped=0 failures=0 errors=0
```

## 3. 변환 규칙

| Spock | JUnit 5 |
|---|---|
| `def "설명"()` | lowerCamelCase 메서드 + `@DisplayName("설명")` |
| `Mock()` + `>>` | `Mockito.mock()` + `when().thenReturn()` |
| `>> { arg -> arg }` | `thenAnswer(inv -> inv.getArgument(0))` |
| `N * mock.method()` | `verify(mock, times(N))` / `never()` |
| `0 * mock._` | `verifyNoInteractions(mock)` |
| `then:` 블록을 나눠 호출 순서 고정 | `InOrder` |
| `thrown(BusinessException)` + `e.errorCode ==` | `assertThatThrownBy(...).isInstanceOf(...).extracting("errorCode").isEqualTo(...)` |
| `where:` 블록 | `@ParameterizedTest` + `@MethodSource` |
| `@SpringBean` (spock-spring) | `@MockitoBean` (Boot 4 기본) |
| Groovy `*.property`, `3.times{}` | Stream API, `for` 루프 |
| Groovy BigDecimal `==` | AssertJ `isEqualByComparingTo` (Groovy 의 `==` 는 `compareTo` 시맨틱이라 `equals` 로 바꾸면 scale 때문에 깨진다) |

### 판단이 필요했던 지점

- **`@ExtendWith(MockitoExtension)` 을 쓰지 않았다.** strict stubbing 이 켜지면 한 테스트에서
  분기별로 쓰이는 스텁이 `UnnecessaryStubbingException` 으로 터진다. Spock 의 `given:` 스텁은 느슨하므로
  동일한 시맨틱을 유지하기 위해 필드에서 `Mockito.mock()` 을 직접 만들었다.
- **기간 조회 분기 테스트**는 두 조회 메서드를 모두 스텁한 뒤 호출 횟수(`times(0)`/`times(1)`)로 라우팅을 검증했다.
  Spock 의 `periodCalls * ...` 표현을 그대로 옮긴 형태다.
- **`ProbeController`** 는 Groovy 스펙 파일 안의 두 번째 top-level 클래스였다. Java 에서도 같은 파일의
  package-private top-level 클래스로 유지했고, `ValidBody` 는 Groovy 프로퍼티가 사라지므로 public 필드로 바꿨다
  (Jackson 기본 가시성이 public 필드를 읽는다).

## 4. 변경 내용

- `src/test/groovy/` 삭제, 동일 구성을 `src/test/java/` 로 재작성 (14개 클래스)
- 중복 스모크 테스트 정리: `FmarketApplicationTest.groovy`(빈 스펙) 삭제, `FmarketApplicationTests.java` 를
  default 패키지에서 `com.sorryisme.fmarket` 로 이동
- `DomainFixture.java` 를 `src/test/java/.../testUtils/` 로 이동 (내용 변경 없음)
- `build.gradle`: `id 'groovy'` 플러그인, `spock-core`/`spock-spring` 의존성, `GroovyCompile` 인코딩 설정 제거.
  테스트 의존성 추가는 없다 — JUnit 5·Mockito·AssertJ 는 `spring-boot-starter-test` 가 가져온다.
- 리포지토리 테스트에 Spock 용으로 붙어 있던 빈 `@ContextConfiguration` 제거
- 문서: `docs/TESTING.md` 전면 갱신, `CLAUDE.md` 기술 스택·폴더 구조 갱신,
  `.claude/agents/java-spring-reviewer.md` 의 "Spock 전환 진행 중" 전제 정리

## 5. 검증 결과

```
$ ./gradlew test
테스트 클래스 15개 / tests=90 skipped=0 failures=0 errors=0   # BUILD SUCCESSFUL
```

클래스 수·테스트 수가 기준선과 동일하다. `@ParameterizedTest` 가 Spock `where:` 블록과 같은 수로
전개됐음을 의미한다 (`ProductRepositoryTest` 11 = 파라미터 5 + 단건 6, `OrderServiceTest` 16 = 파라미터 3 + 단건 13).

**회귀 탐지력 확인**: 번역 과정에서 의미를 잃을 위험이 가장 큰 단언은 `createOrder` 의
"옵션 ID 오름차순 재고 차감" 순서 검증이었다(데드락 방지 계약). `OrderService` 의 정렬을
일시적으로 `.reversed()` 로 바꿔 테스트가 실제로 실패하는지 확인한 뒤 원복했다.

```
OrderServiceTest > createOrder는 옵션 ID 오름차순으로 재고를 차감하고 주문을 저장한다 FAILED
```

## 6. 남은 것 / 주의

- `scripts/guard-scan.sh`, `scripts/verify-full.sh`, `.claude/hooks/*.sh` 의 `*.groovy` 패턴은 그대로 두었다.
  매칭될 파일이 없어 무해하고, 스크립트 동작을 건드리지 않는 편이 안전하다.
- 테스트를 새로 쓸 때의 관례는 `docs/TESTING.md` 에 모아 두었다 — 특히
  `MockitoExtension` 을 쓰지 않는 이유와 `InOrder` 로 고정한 순서 계약은 지워지면 안 된다.

## 7. 후속 작업 (같은 날 처리)

전환 직후 남겨 두었던 두 항목을 이어서 처리했다.

### 7.1 테스트 픽스처의 외부 도메인 정리

`guard-scan.sh` 가 `UserRepositoryTest`·`UserServiceTest` 의 `logoUrl("https://naver.com/test.jpg")` 를
"외부 URL 하드코딩" 으로 경고했다(ERROR 아님). 규칙은 `example.com`/`example.org` 를 허용 목록에 두고 있고,
테스트 픽스처를 실제 타사 도메인에 걸어 두는 것 자체가 바람직하지 않으므로 RFC 2606 예약 도메인으로 바꿨다.

같은 성격의 문제인 이메일 픽스처(`@naver.com`)도 함께 정리했다 — `DomainFixture`, `UserServiceTest`,
`data.sql` 시드. guard-scan 은 이메일을 검사하지 않으므로 경고 해소와는 무관한, 일관성을 위한 변경이다.
`data.sql` 이 바뀌었으므로 `@DataJpaTest` 계열을 포함한 전체 테스트를 다시 돌려 확인했다.

결과: `guard-scan.sh` 가 `src` 전체에서 경고 0건, 테스트 90개 전부 통과.

### 7.2 PROJECT_ANALYSIS.md 갱신

2026-09-10 작성 이후 갱신되지 않아 MyBatis·Spock·Boot 3.3.5 기준으로 현재 코드와 어긋나 있었다.
코드를 다시 조사해 다음을 반영했다.

- 기술 스택·의존성·패키지 트리를 JPA·JUnit 5 기준으로 교체 (`mapper/`·`domain/` → `entity/`·`repository/`,
  예외 4종 → `BusinessException`, `enums` 3종, `common/dto`)
- 09-11~12 주요 변경 이력 표 추가
- 3.1 절을 비관적 락 서술에서 **조건부 원자적 UPDATE** 서술로 교체. 멱등성 판정이
  `SELECT ... FOR UPDATE` 가 아니라 유니크 제약 위반 해석이라는 점, 옵션 ID 오름차순 차감이
  데드락 회피 계약이라는 점, `cancelOrder` 가 상태 전이를 재고 복구보다 먼저 하는 이유를 명시
- 페이지네이션 안정성(`PageableSupport`)·검증 자동화 절 신설, API 엔드포인트 표 추가
- "개선 여지" 절 신설

조사 중 확인한 사실 하나: `POST /orders` 는 주문 **생성**이 아니라 목록 검색이다(생성은 `POST /orders/create`).
엔드포인트 이름만 보면 생성으로 읽히므로 문서에 명시하고 개선 여지로 남겼다.
