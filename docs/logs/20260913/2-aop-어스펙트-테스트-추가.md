# aop 어스펙트 테스트 추가

- 작업일: 2026-09-13
- 브랜치: `chore/jacoco-coverage`
- 범위: `src/test/java/com/sorryisme/fmarket/aop/` (신규 테스트 2개)

## 배경

JaCoco 도입 직후 기준선 측정에서 `aop` 패키지 라인 커버리지가 0% 로 나왔다
(`docs/logs/20260913/1-JaCoco-커버리지-도입.md`). `AuthenticationAspect` 는 인증 통과
판정을, `IdempotencyAspect` 는 멱등성 키 검증과 중복 요청 차단을 담당한다. 둘 다 잘못
동작하면 인가 우회나 주문 중복 처리로 이어지는데 직접 검증하는 테스트가 없었다.

## 작성한 테스트

기존 서비스 테스트와 같은 방식(순수 JUnit 5 + Mockito `mock()`, 한글 `@DisplayName`)을
따랐다. 어스펙트는 스프링 컨텍스트 없이도 생성자 주입만으로 만들 수 있어 슬라이스 테스트가
필요하지 않다.

### AuthenticationAspectTest

- 세션에 로그인 ID 가 있으면 통과
- 세션에 로그인 ID 가 없으면 `BusinessException(LOGIN_REQUIRED)`

### IdempotencyAspectTest

어스펙트가 `MethodSignature` 에서 파라미터 애노테이션을 직접 훑으므로, 그 경로를 실제로
태우기 위해 테스트 클래스 안에 `@IdempotencyKeyParam` 을 붙인 `SampleTarget` 메서드들을
두고 리플렉션으로 `Method` 를 얻어 목 조인 포인트에 물렸다. 문자열 키를 인자로 넘기는
것만으로는 애노테이션 스캔 분기가 실행되지 않는다.

| 케이스 | 기대 |
|---|---|
| 36자 키 정상 | 키 저장 후 `proceed()` 실행, 저장된 키 값 검증 |
| 키 파라미터 없음 | `IDEMPOTENCY_KEY_INVALID`, 저장·`proceed()` 모두 없음 |
| 키 길이 36자 아님 | `IDEMPOTENCY_KEY_INVALID`, 저장·`proceed()` 모두 없음 |
| 키 파라미터가 String 아님 | `IllegalArgumentException` |
| `saveAndFlush` 가 `DataIntegrityViolationException` | `DUPLICATE_REQUEST`, `proceed()` 없음 |

마지막 케이스가 이 어스펙트의 핵심이다. 중복 판정을 애플리케이션 조회가 아니라 유니크 제약
위반에 맡기고 있으므로, 예외가 `DUPLICATE_REQUEST` 로 변환되고 **원래 메서드가 실행되지
않는다**는 것을 확인해야 중복 주문이 막힌다는 보장이 된다.

## 검증

```
docker compose up -d db-master db-replica
./gradlew spotlessApply test --tests 'com.sorryisme.fmarket.aop.*'  → BUILD SUCCESSFUL
./gradlew clean test                                                → BUILD SUCCESSFUL
```

### 커버리지 변화 (제외 패턴 적용, 전체 테스트 기준)

| 지표 | 이전 | 이후 |
|---|---|---|
| LINE | 80.6% (253/314) | **89.5% (281/314)** |
| BRANCH | 72.1% (49/68) | **94.1% (64/68)** |
| INSTRUCTION | 82.8% | **90.4%** |
| `aop` 패키지 LINE | 0.0% | **100.0%** |

## 남은 항목

`controller` 는 여전히 17.2% 다. 커버리지 집계에서 제외하는 방안을 논의했으나 이번에는
적용하지 않았고, 테스트 보강 항목으로 `docs/todo/BACKLOG.md` 에 남아 있다.
