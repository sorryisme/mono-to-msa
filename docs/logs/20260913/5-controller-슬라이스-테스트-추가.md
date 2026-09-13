# controller 슬라이스 테스트 추가

- 작업일: 2026-09-13
- 브랜치: `chore/jacoco-coverage`
- 범위: `src/test/java/com/sorryisme/fmarket/controller/` (신규 테스트 4개)

## 배경

컨트롤러 4개에 핸들러가 14개인데 그중 3개만 `ControllerResponseContractTest` 를 통해 덮여
있어 `controller` 패키지 라인 커버리지가 17.2% 였다.

| 컨트롤러 | 이전 |
|---|---|
| `CartController` | 4/8 |
| `OrderController` | 0/14 |
| `ProductController` | 2/18 |
| `UserController` | 4/18 |

## 작성한 테스트

컨트롤러별 `@WebMvcTest` 슬라이스 테스트 4개를 추가했다:
`CartControllerTest`, `OrderControllerTest`, `ProductControllerTest`, `UserControllerTest`.

기존 `ControllerResponseContractTest` 는 그대로 뒀다. 그것은 `docs/API_RESPONSE.md` 의
**봉투 형식 계약**을 컨트롤러 전반에서 고정하는 문서이고, 새 테스트는 **컨트롤러별 라우팅·
파라미터 바인딩·서비스 위임**을 본다. 역할이 달라 합치면 둘 다 읽기 어려워진다.

각 핸들러마다 확인하는 것:

- 경로·HTTP 메서드가 그 핸들러에 매핑되는가
- 경로변수 / 요청본문 / `@LoginUserId` / 헤더가 서비스에 올바른 인자로 전달되는가
  (`ArgumentCaptor`·`eq()` 로 값까지 검증)
- 응답 상태와 봉투

특히 값을 확인한 곳:

- **`OrderController.findAllOrderList`** — `OrderSearchDto.from` 으로 조립된 검색 조건에
  로그인 유저 ID 가 실제로 채워지는지. 여기서 `userId` 가 빠지면 남의 주문까지 조회된다.
- **`UserController.login`** — 로그인 성공 시 `sessionManager.setLoginUserId(id)` 가
  호출되는지. 이 호출이 빠지면 응답은 200 이지만 이후 요청이 전부 비로그인으로 취급된다.
- **`OrderController.createOrder`** — `Idempotency-Key` 헤더가 없으면 서비스까지 가지
  못하고 400 이 되는지. 키 값 자체의 유효성은 `IdempotencyAspectTest` 가 본다.

`@RequireLogin` AOP 는 `@WebMvcTest` 슬라이스에 포함되지 않으므로, 이 테스트들이 덮는
인증 경로는 `@LoginUserId` 리졸버까지다. 어스펙트 자체는
`docs/logs/20260913/2-aop-어스펙트-테스트-추가.md` 에서 따로 덮었다.

## 작업 중 발견한 결함

`POST /api/v1/products/search` 를 **요청 본문 없이 호출하면 500** 이 난다.

`ProductController.findAllProductList` 는 `@RequestBody(required = false)` 로 본문을
선택으로 열어 뒀지만, 곧바로 호출하는 `ProductSearchDto.from` 이 `searchDto.getQuery()` 로
null 을 역참조한다. 조건 없는 전체 목록 조회로 쓰려는 의도로 보이는데 실제로는 동작하지
않고, 500 이라 클라이언트는 원인도 알 수 없다.

이 경우를 테스트로 고정하지 않았다. 현재 동작(500)을 기대값으로 적으면 결함이 사양으로
굳는다. 대신 테스트 파일에 왜 비워 뒀는지 주석으로 남기고 `docs/todo/BACKLOG.md` 에
결함으로 올렸다. `from` 에서 null 을 빈 조건으로 다룰지, `required = true` 로 바꿀지는
API 계약 결정이라 별도로 다룬다.

## 검증

```
./gradlew spotlessApply clean test  → BUILD SUCCESSFUL
```

### 커버리지 변화

| 지표 | 이전 | 이후 |
|---|---|---|
| `controller` 패키지 LINE | 17.2% | **100.0%** |
| 전체 LINE | 91.7% (287/313) | **99.4% (311/313)** |
| 전체 INSTRUCTION | 92.1% | **99.5%** |
| 전체 BRANCH | 94.1% | 94.1% |

컨트롤러 4개 모두 라인 100% 가 됐다.

## 남은 항목

- 전체에서 안 덮인 2줄은 `PasswordCipher` 의 암시적 기본 생성자와 `exception` 패키지 일부다
  (의도적으로 남긴 것 — `docs/logs/20260913/3-PasswordCipher-테스트-추가.md` 참고).
- 이제 `jacocoTestCoverageVerification` 하한을 걸 수 있는 상태다 →
  `docs/todo/BACKLOG.md`
