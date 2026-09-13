# PasswordCipher 테스트 추가

- 작업일: 2026-09-13
- 브랜치: `chore/jacoco-coverage`
- 범위: `src/test/java/com/sorryisme/fmarket/utils/PasswordCipherTest.java` (신규)

## 배경

JaCoco 측정에서 `utils` 패키지가 라인 커버리지 50% 로 남아 있었다. `encrypt` 와 `getSalt`
는 `UserServiceTest` 를 통해 간접적으로 덮여 있었지만, `checkPassword` 는 어디에서도
실행되지 않았다.

## 작성한 테스트

의존성이 없어 목 없이 BCrypt 를 실제로 호출하는 단위 테스트로 작성했다.

| 테스트 | 확인하는 것 |
|---|---|
| `encryptIsDeterministicForSameSalt` | 같은 비밀번호 + 같은 salt → 같은 해시 |
| `encryptDiffersBySalt` | salt 가 다르면 같은 비밀번호라도 다른 해시 |
| `getSaltReturnsNewValueEachTime` | 호출마다 다른 값, BCrypt salt 형식(`$2a$`) |
| `checkPasswordMatchesOnlyOriginal` | 원래 비밀번호만 `true`, 다른 비밀번호는 `false` |

첫 번째가 가장 중요하다. `UserService.login` 은 저장된 salt 로 비밀번호를 다시 해싱한 값이
저장된 해시와 같은지로 로그인을 판정한다. 즉 "같은 salt 면 해시가 재현된다"는 성질에 로그인
전체가 의존하는데, 그 전제를 직접 못 박아 둔 테스트가 없었다.

## 검증

```
./gradlew spotlessApply test --tests 'com.sorryisme.fmarket.utils.*'  → BUILD SUCCESSFUL
./gradlew clean test                                                  → BUILD SUCCESSFUL
```

### 커버리지 변화 (제외 패턴 적용, 전체 테스트 기준)

| 지표 | 이전 | 이후 |
|---|---|---|
| `utils` 패키지 LINE | 50.0% | **75.0%** |
| 전체 LINE | 89.5% (281/314) | **89.8% (282/314)** |
| 전체 BRANCH | 94.1% | 94.1% (변화 없음) |

`utils` 가 100% 가 되지 않은 이유는 `public class PasswordCipher` 의 암시적 기본 생성자
한 줄이 남기 때문이다. 정적 메서드만 있는 클래스라 실제로 호출될 일이 없고, 수치를 채우려고
클래스를 인스턴스화하는 테스트를 넣는 것은 검증하는 바가 없어 넣지 않았다.

## 작업 중 발견한 것

`PasswordCipher.checkPassword` 는 프로덕션 코드에서 아무도 쓰지 않는다.
`UserService.login`(`UserService.java:80`)이 저장된 salt 로 직접 재해싱한 뒤
`String.equals` 로 비교하고 있는데, 이는 `BCrypt.checkpw` 가 하는 일을 손으로 다시 한
것이고 상수 시간 비교가 아니다. 인증 경로를 바꾸는 변경이라 이번 테스트 추가 범위를 넘어서
`docs/todo/BACKLOG.md` 에 항목으로 남겼다.
