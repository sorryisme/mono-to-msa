# API 응답 계약

f-market REST API 의 응답 형식과 오류 코드 계약입니다. 이 문서는 클라이언트와의 약속이므로,
여기 적힌 형식·코드·HTTP 상태를 바꿀 때는 반드시 문서를 먼저 갱신하고 클라이언트 영향 범위를 확인합니다.
아키텍처 전반은 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고하세요.

## 원칙

두 층으로 결과를 표현합니다. 각 층은 서로 다른 질문에 답하며, 같은 정보를 두 번 싣지 않습니다.

| 층 | 답하는 질문 | 소비자 |
|---|---|---|
| HTTP 상태 코드 | 요청이 성공했는가, 실패했다면 누구 책임인가(클라이언트 4xx / 서버 5xx) | 브라우저, 게이트웨이, 모니터링, 재시도 로직 등 본문을 모르는 외부 |
| 본문 `code` | 실패했다면 도메인 관점에서 정확히 무슨 이유인가 | 우리 클라이언트(화면 분기, 사용자 안내) |

- HTTP 상태는 `ErrorCode` 가 결정합니다. 컨트롤러나 예외 핸들러가 상태를 임의로 고르지 않습니다.
- 본문 `code` 는 HTTP 상태의 복사본이 아닙니다. 같은 404 라도 `USER_NOT_FOUND` 와 `ORDER_NOT_FOUND` 는 구분됩니다.
- 성공/실패 여부는 HTTP 상태만으로 판단할 수 있어야 합니다. 본문에 `success` 같은 중복 플래그를 두지 않습니다.
- 모든 응답(성공·실패·프레임워크 오류)은 같은 봉투(envelope) 형식입니다. 클라이언트는 한 가지 파서만 가지면 됩니다.

## 응답 봉투

```json
{
  "code": "OK",
  "message": "성공했습니다.",
  "data": { }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 결과 코드. 성공은 항상 `OK`, 실패는 아래 `ErrorCode` 표의 값 |
| `message` | string | 사람이 읽는 설명. 화면에 그대로 노출 가능해야 하며, 예외 스택이나 내부 식별자를 담지 않음 |
| `data` | any / null | 성공 시 페이로드. 실패 시 원칙적으로 `null`, 입력 검증 실패만 필드 오류 목록을 담음 |

구현 클래스는 `common/dto/ResponseDto<T>` 하나입니다. `success`, `statusCode` 필드는 두지 않습니다.

### 성공 응답

| 상황 | HTTP 상태 | 비고 |
|---|---|---|
| 조회, 수정, 삭제, 취소, 확정, 로그인 | 200 OK | 컨트롤러가 `ResponseDto` 를 직접 반환 |
| 리소스 생성(회원 가입, 판매자 등록, 장바구니 담기, 주문 생성, 리뷰 작성) | 201 Created | `@ResponseStatus(HttpStatus.CREATED)` |

`ResponseEntity` 는 상태 코드가 요청마다 달라지거나 헤더를 함께 내려야 할 때만 씁니다.
상태가 고정된 엔드포인트는 `ResponseDto<T>` 를 직접 반환하고 `@ResponseStatus` 로 상태를 선언합니다.
`data` 가 없는 성공(예: 삭제)은 `data` 에 대상 id 를 담습니다.

```json
{ "code": "OK", "message": "성공했습니다.", "data": 42 }
```

### 실패 응답

```json
{ "code": "ORDER_NOT_FOUND", "message": "찾을 수 없는 주문입니다.", "data": null }
```

입력 검증 실패(`INVALID_INPUT`)만 `data` 에 필드 오류 목록을 담습니다.

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "data": [
    { "field": "quantity", "reason": "1 이상이어야 합니다" }
  ]
}
```

## ErrorCode

`common/ErrorCode` enum 이 코드 → HTTP 상태 → 기본 메시지 매핑의 단일 출처입니다. enum 과 아래 표는 같은 도메인 순서로 묶여 있습니다.
새 오류 상황이 생기면 해당 도메인 섹션에 항목을 추가하고 표를 함께 갱신합니다. 컨트롤러 슬라이스 테스트가 이 표를 고정합니다.

### 공통 (요청 형식·프레임워크·안전망)

| code | HTTP | 발생 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | `@Valid` 실패, JSON 파싱 실패, 필수 파라미터 누락, 수량 0 이하, 주문 항목에 같은 상품 옵션 중복 등 값 자체가 잘못된 경우 |
| `RESOURCE_NOT_FOUND` | 404 | 매핑되지 않은 경로 등 프레임워크가 404 로 판정한 요청 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `INVALID_STATE` | 409 | 도메인 코드에 해당하지 않는 상태 규칙 위반(엔티티 `IllegalStateException` 안전망) |
| `INTERNAL_ERROR` | 500 | 처리되지 않은 모든 예외. 메시지는 고정 문구이며 예외 내용을 노출하지 않음 |

### 인증

| code | HTTP | 발생 상황 |
|---|---|---|
| `LOGIN_REQUIRED` | 401 | 세션 없음(`@RequireLogin`, `@LoginUserId`) |
| `LOGIN_FAILED` | 401 | 로그인 id 또는 비밀번호 불일치. 어느 쪽이 틀렸는지 구분하지 않음 |

### 유저

| code | HTTP | 발생 상황 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | 유저 없음 |
| `DUPLICATE_USER` | 409 | 이미 등록된 유저(이름 + 전화번호) |

### 상품

| code | HTTP | 발생 상황 |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 또는 삭제됨 |
| `PRODUCT_OPTION_NOT_ON_SALE` | 409 | 주문 항목에 판매 중이 아닌 옵션 포함 |

### 재고

| code | HTTP | 발생 상황 |
|---|---|---|
| `OUT_OF_STOCK` | 409 | 재고 부족 또는 재고 행 없음 |

### 장바구니

| code | HTTP | 발생 상황 |
|---|---|---|
| `CART_NOT_FOUND` | 404 | 장바구니 항목 없음 |

### 주문

| code | HTTP | 발생 상황 |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_STATUS_NOT_CHANGEABLE` | 409 | 현재 주문 상태에서 허용되지 않는 취소/확정 |
| `IDEMPOTENCY_KEY_INVALID` | 400 | 주문 생성 시 `Idempotency-Key` 헤더 누락 또는 36자 UUID 형식이 아님 |
| `DUPLICATE_REQUEST` | 409 | 같은 `Idempotency-Key` 로 이미 처리된 요청 |

### 코드 명명 규칙

- 대문자 스네이크 케이스, `{대상}_{상태}` 또는 `{상태}_{대상}` 형태로 읽히게 짓습니다.
- 클라이언트가 화면 분기를 다르게 해야 할 때만 새 코드를 만듭니다. 같은 안내로 충분하면 기존 코드를 재사용합니다.
- 한 번 공개된 코드는 이름을 바꾸거나 HTTP 상태를 바꾸지 않습니다. 의미가 달라지면 새 코드를 추가하고 옛 코드는 사용처를 제거한 뒤 삭제합니다.

## 예외 모델

- `exception/BusinessException` 하나가 모든 도메인 오류를 표현합니다. `ErrorCode` 를 필수로 받고, 메시지를 생략하면 코드의 기본 메시지를 씁니다.
- `NotFoundDataException`, `DuplicateDataException`, `RequireLoginException`, `UpdateFailException` 은 `BusinessException` + `ErrorCode` 로 대체하고 제거합니다. 예외 클래스 종류가 아니라 코드가 상황을 구분합니다.
- 서비스·AOP·리졸버는 `throw new BusinessException(ErrorCode.ORDER_NOT_FOUND)` 형태로 던집니다.
- 엔티티는 두 종류를 구분합니다. 도메인 규칙 위반(재고 부족)은 `BusinessException(OUT_OF_STOCK)` 을 던지고, 호출자의 프로그래밍 오류에 가까운 인자 검증(수량 0 이하)은 `IllegalArgumentException` 을 유지합니다. `entity → exception` 의존은 허용됩니다(`scripts/arch-check.sh` 는 entity 가 controller/service/repository 를 참조하는 것만 막습니다).
- 서비스 테스트는 `thrown(BusinessException)` 뒤에 `e.errorCode == ErrorCode.XXX` 를 검증합니다. 예외 타입보다 코드가 더 강한 단언입니다.

## 예외 핸들러 (`exception/GlobalExceptionHandler`)

`ResponseEntityExceptionHandler` 를 상속하고 다음 순서로 매핑합니다. 모든 경로가 같은 `ResponseDto` 봉투를 반환합니다.

| 예외 | 처리 |
|---|---|
| `BusinessException` | `errorCode.status` 로 응답, 본문 `code`/`message` 는 예외에서 |
| `MethodArgumentNotValidException` (`@Valid`) | 400 `INVALID_INPUT`, `data` 에 필드 오류 목록 |
| `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MissingRequestHeaderException` | 400 `INVALID_INPUT` |
| `HttpRequestMethodNotSupportedException` | 405 `METHOD_NOT_ALLOWED` |
| `NoResourceFoundException`, `NoHandlerFoundException` | 404 `RESOURCE_NOT_FOUND` |
| `ResponseEntityExceptionHandler` 가 처리하는 그 외 예외 | `handleExceptionInternal` 을 오버라이드해 상태는 프레임워크 값을 유지하고 본문만 봉투로 교체. 코드는 상태로 정한다(405→`METHOD_NOT_ALLOWED`, 404→`RESOURCE_NOT_FOUND`, 그 외 4xx→`INVALID_INPUT`, 5xx→`INTERNAL_ERROR`) |
| `IllegalArgumentException` | 400 `INVALID_INPUT` (엔티티 인자 검증 안전망) |
| `IllegalStateException` | 409 `INVALID_STATE` |
| `Exception` | 500 `INTERNAL_ERROR`, 고정 메시지 |

ProblemDetail(RFC 9457) 은 쓰지 않습니다. 프레임워크 오류까지 하나의 봉투로 통일하는 것이 이 프로젝트의 선택이며,
`spring.mvc.problemdetails.enabled` 는 켜지 않습니다.

### 로그 레벨

[CODE_STYLE.md](CODE_STYLE.md) 의 로깅 기준을 따릅니다.

| HTTP | 레벨 | 내용 |
|---|---|---|
| 4xx | `WARN` | 코드와 메시지만. 스택 트레이스 없음 |
| 5xx | `ERROR` | 스택 트레이스 포함 |

메시지에 요청 본문이나 개인정보를 넣지 않습니다. 요청 추적은 `MDCLoggingFilter` 의 MDC 값으로 합니다.

## 클라이언트 처리 지침

1. HTTP 상태가 2xx 면 `data` 를 사용합니다. `code` 는 `OK` 로 고정이므로 검사할 필요가 없습니다.
2. 4xx/5xx 면 `code` 로 분기합니다. 알 수 없는 코드는 `message` 를 그대로 노출하고 일반 오류로 처리합니다.
3. 401 은 로그인 화면으로 보냅니다. `LOGIN_REQUIRED` 와 `LOGIN_FAILED` 의 안내 문구만 다릅니다.
4. 409 `DUPLICATE_REQUEST` 는 이미 처리된 요청이므로 재시도하지 않습니다.
5. 500 은 재시도 대상이며 `message` 는 항상 고정 문구입니다.

axios, fetch 같은 클라이언트는 4xx/5xx 를 예외로 던지므로, 기존에 항상 200 을 전제로 작성된 호출부는 이 계약으로 옮길 때 오류 분기를 상태 코드 기준으로 재작성해야 합니다.

## 테스트 약속

- `exception/GlobalExceptionHandlerTest` 가 `@WebMvcTest` 로 코드별 HTTP 상태, 봉투 형식, 검증 실패 시 `data` 구조, 미처리 예외의 메시지 비노출을 검증합니다. 실제 컨트롤러 대신 예외를 일으키는 테스트 전용 프로브 컨트롤러를 띄웁니다.
- `controller/ControllerResponseContractTest` 가 성공 봉투, 생성 API 의 201, 세션 없는 `@LoginUserId` 요청의 401 을 검증합니다.
- `ErrorCode` 항목을 추가하면 이 문서의 표와 위 테스트를 함께 갱신합니다.

## 설계 배경

- 이전에는 모든 응답이 HTTP 200 이었고 본문 `statusCode` 에 404/409/500 을 담았습니다. 한편 `@Valid` 실패나 405 는 프레임워크 기본 처리로 실제 4xx 와 다른 본문 형식으로 나가, 클라이언트가 두 가지 형식을 다뤄야 했습니다.
- `statusCode` 가 HTTP 상태와 같은 숫자여서 본문 필드로서의 정보 가치가 없었습니다. 본문 코드가 의미를 가지려면 HTTP 상태가 구분하지 못하는 도메인 원인을 담아야 하므로 문자열 enum 으로 바꿨습니다.
- 로그인 필요가 400 으로, 상태 충돌이 500 으로 매핑되는 등 상태 코드 선택이 예외 클래스마다 흩어져 있었습니다. `ErrorCode` 가 상태를 소유하게 해서 매핑을 한 곳에 모았습니다.
