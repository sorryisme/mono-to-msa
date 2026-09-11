# API 응답 계약 전환: HTTP 상태 + 도메인 ErrorCode

- 날짜: 2026-09-11
- 브랜치: `refactor/mybatis-to-jpa`
- 계약 문서: [docs/API_RESPONSE.md](../../API_RESPONSE.md)

## 배경

- 모든 응답이 HTTP 200 이었고 본문 `statusCode` 에 404/409/500 을 담았다. 본문 숫자가 HTTP 상태와 같은 값이라 정보 가치가 없었다.
- `GlobalExceptionHandler` 가 `ResponseEntityExceptionHandler` 를 상속만 하고 오버라이드하지 않아 `@Valid` 실패·JSON 파싱 실패·405 는 실제 4xx 와 ProblemDetail 본문으로 나갔다. 클라이언트가 두 가지 형식을 다뤄야 했다.
- 로그인 필요가 400, 상태 충돌이 500 으로 매핑되는 등 상태 코드 선택이 예외 클래스마다 흩어져 있었다.

## 결정

- HTTP 상태는 "성공했는가, 누구 책임인가" 를, 본문 `code` 는 "도메인 관점의 원인" 을 답한다. 둘은 같은 정보를 싣지 않는다.
- 봉투는 `code`, `message`, `data` 세 필드. `success`, `statusCode` 제거. 성공은 `code: "OK"`.
- `common/ErrorCode` enum 이 코드 → HTTP 상태 → 기본 메시지의 단일 출처. 도메인 예외는 `exception/BusinessException(ErrorCode)` 하나로 통일.
- 프레임워크 예외(검증 실패, 파싱 실패, 405, 404)도 같은 봉투로 통일. ProblemDetail 은 쓰지 않는다.
- 생성 API 5개(회원 가입, 판매자 등록, 장바구니 담기, 주문 생성, 리뷰 작성)는 201 Created. `ResponseEntity` 는 상태가 동적일 때만 쓰고, 고정 상태는 `@ResponseStatus` 로 선언한다.
- 로그는 4xx WARN(스택 없음), 5xx ERROR(스택 포함).

## 변경 내용

### 신규
| 파일 | 내용 |
|---|---|
| `common/ErrorCode` | 17개 코드(4xx 16, 5xx 1). 각 항목이 `HttpStatus` 와 기본 메시지 보유 |
| `exception/BusinessException` | `ErrorCode` 필수, 메시지 생략 시 코드 기본 메시지 |
| `common/dto/FieldErrorDto` | `INVALID_INPUT` 응답의 `data` 항목(`field`, `reason`) |
| `docs/API_RESPONSE.md` | 응답 봉투, 코드 표, 예외 모델, 핸들러 매핑, 클라이언트 지침을 담은 계약 문서 |
| `test/.../exception/GlobalExceptionHandlerTest` | `@WebMvcTest` 11건. 프로브 컨트롤러로 코드별 상태·봉투·검증 실패 `data`·500 메시지 비노출 고정 |
| `test/.../controller/ControllerResponseContractTest` | `@WebMvcTest` 5건. 성공 봉투, 생성 201, 세션 없는 `@LoginUserId` 401, 카테고리 목록 봉투 |

### 수정
| 파일 | 내용 |
|---|---|
| `common/dto/ResponseDto` | `success`/`statusCode` 제거, `code` 추가. `success(...)`, `error(ErrorCode[, message[, data]])` 팩토리 |
| `exception/GlobalExceptionHandler` | `BusinessException` → `errorCode.status`. `handleMethodArgumentNotValid`·`handleExceptionInternal` 오버라이드로 프레임워크 예외를 봉투로 통일. `IllegalArgumentException` → 400 `INVALID_INPUT`, `IllegalStateException` → 409 `INVALID_STATE`, 그 외 → 500 `INTERNAL_ERROR` 고정 메시지 |
| `service/*`, `aop/*`, `resolver/LoginUserIdResolver`, `entity/Inventory` | 모든 throw 지점을 `BusinessException(ErrorCode.XXX)` 로 이관. 로그인 id 없음·비밀번호 불일치는 둘 다 `LOGIN_FAILED` 로 구분하지 않음. 재고 부족은 엔티티에서 `OUT_OF_STOCK` |
| `controller/*` | 생성 API 5개에 `@ResponseStatus(CREATED)`. `ProductController.findMajorCategoryList` 가 봉투 없이 `List` 를 반환하던 것을 `ResponseDto` 로 감쌈 |
| `build.gradle` | `spring-boot-webmvc-test` 추가(Boot 4 부터 `@WebMvcTest` 슬라이스 별도 모듈) |
| 서비스 테스트 4종 | `thrown(BusinessException)` + `e.errorCode == ErrorCode.XXX` 로 단언 교체(15건) |
| `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md` | 문서 목록·폴더 구조·핸들러 위치·테스트 관례 갱신 |

### 삭제
- `exception/NotFoundDataException`, `DuplicateDataException`, `RequireLoginException`, `UpdateFailException`. `UpdateFailException` 은 던지는 곳이 없던 미사용 클래스였다.

## 매핑 변경 요약 (클라이언트 영향)

| 상황 | 이전 | 이후 |
|---|---|---|
| 도메인 오류 전반 | HTTP 200 + `statusCode` | 실제 4xx/5xx + `code` |
| 로그인 필요 | 200 + 400 | 401 `LOGIN_REQUIRED` |
| 로그인 실패 | 200 + 400 | 401 `LOGIN_FAILED` |
| 주문 상태 충돌, 재고 부족, 판매중 아닌 옵션 | 200 + 400 | 409 |
| `@Valid` 실패 | 400 ProblemDetail | 400 `INVALID_INPUT` + 필드 오류 목록 |
| 생성 API | 200 | 201 |
| 카테고리 목록 | 봉투 없는 배열 | `OK` 봉투 |

axios, fetch 는 4xx/5xx 를 예외로 던지므로 항상 200 을 전제하던 호출부는 상태 코드 기준으로 오류 분기를 다시 써야 한다.

## 검증

- `./gradlew check` 통과. Spotless 포맷 검사 포함.
- 테스트 15개 클래스 84건 전부 성공. 신규 16건(핸들러 11, 컨트롤러 5) 은 구현 전 실패를 확인한 뒤 통과시켰다(TDD).
- 리포지토리·엔티티 매핑 테스트는 로컬 MySQL 로 실행됐다.

## 남은 판단·제한

- `IdempotencyAspect` 의 "파라미터가 String 이 아님" 은 애노테이션 오용(개발자 오류)이라 `IllegalArgumentException` 을 유지했다. 런타임에 400 으로 나가지만 정상 배포에서는 발생하지 않는 경로다.
- `@RequireLogin` AOP 는 `@WebMvcTest` 슬라이스에 포함되지 않아 컨트롤러 테스트는 `@LoginUserId` 리졸버 경로의 401 만 검증한다. AOP 경로는 `BusinessException(LOGIN_REQUIRED)` 를 던지므로 핸들러 테스트가 같은 결과를 보장한다.
- 프로브 컨트롤러(`ProbeController`)는 테스트 소스에만 있으며 `/probe/**` 경로로 예외를 재현한다. 운영 클래스패스에는 포함되지 않는다.
