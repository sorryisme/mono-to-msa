# SessionManager 테스트 추가 및 AppConstants 커버리지 제외

- 작업일: 2026-09-13
- 브랜치: `chore/jacoco-coverage`
- 범위: `src/test/java/com/sorryisme/fmarket/common/SessionManagerTest.java` (신규), `build.gradle`

## 배경

`common` 패키지가 라인 커버리지 80% 로 남아 있었다. 클래스별로 보니 `ErrorCode` 와
`PageableSupport` 는 이미 100% 였고, 빠진 것은 두 개였다.

| 클래스 | 이전 |
|---|---|
| `SessionManager` | 0/10 |
| `AppConstants` | 0/2 |

## 변경 내용

### SessionManagerTest (신규)

`HttpSession` 목을 물린 단위 테스트. 세션 키 문자열(`LOGIN_ID`)까지 검증한다.

| 테스트 | 확인하는 것 |
|---|---|
| `returnsStoredUserId` | 세션에 저장된 로그인 ID 반환 |
| `returnsNullWhenNotLoggedIn` | 속성이 없으면 `null` |
| `storesUserIdUnderLoginIdKey` | `LOGIN_ID` 키로 저장 |
| `removesUserIdOnLogout` | `LOGIN_ID` 키 제거 |

두 번째가 중요하다. `AuthenticationAspect` 는 `getUserId()` 가 `null` 인 것으로 비로그인을
판정한다(`docs/logs/20260913/2-aop-어스펙트-테스트-추가.md`). 여기서 `null` 대신 다른 값을
돌려주기 시작하면 인증 검사가 통째로 무력화되므로 계약으로 못 박았다.

### build.gradle — AppConstants 집계 제외

`classDirectories` exclude 에 `**/common/AppConstants.class` 추가.
`static final String` 상수 두 개만 있는 홀더라 테스트할 동작이 없다. 남는 줄은 암시적 기본
생성자와 클래스 선언이며, 이미 제외한 `dto`/`enums` 와 같은 성격이다. 수치를 채우려고
상수 값을 되읽는 테스트를 넣는 것은 검증하는 바가 없다.

## 검증

```
./gradlew spotlessApply clean test  → BUILD SUCCESSFUL
```

### 커버리지 변화

| 지표 | 이전 | 이후 |
|---|---|---|
| `common` 패키지 LINE | 80.0% | **100.0%** |
| 전체 LINE | 89.8% (282/314) | **91.7% (287/313)** |
| 전체 BRANCH | 94.1% | 94.1% |
| 전체 INSTRUCTION | 90.4% | **92.1%** |

전체 라인 수가 314 → 313 으로 줄어든 것은 `AppConstants` 를 집계에서 뺀 결과다.

## 남은 항목

`controller` 17.2%, `utils` 75%(암시적 기본 생성자 한 줄) — `docs/todo/BACKLOG.md` 참고.
