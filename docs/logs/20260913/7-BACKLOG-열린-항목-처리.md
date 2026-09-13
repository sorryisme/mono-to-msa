# BACKLOG 열린 항목 처리 (로그인 비밀번호 비교 / 본문 없는 상품 검색)

- 일자: 2026-09-13
- 브랜치: main
- 입력: `docs/todo/BACKLOG.md` 열린 항목 2건 (사용자 지시로 처리)

## 1. `UserService.login` 의 비밀번호 비교를 `PasswordCipher.checkPassword` 로 교체

### 문제

`login` 은 저장된 salt 로 입력 비밀번호를 직접 재해싱한 뒤 `String.equals` 로 비교했다.

- `BCrypt.checkpw` 가 하는 일(해시에서 salt 를 꺼내 재해싱 후 상수 시간 비교)을 손으로 다시 구현한 것이다.
- `String.equals` 는 첫 불일치 바이트에서 조기 반환하므로 상수 시간 비교가 아니다.
- 그 결과 `PasswordCipher.checkPassword` 는 프로덕션 코드에서 아무도 쓰지 않는 상태였다.

### 조치

`src/main/java/com/sorryisme/fmarket/service/UserService.java`

```java
// before
String hashedPassword = PasswordCipher.encrypt(password, user.getSalt());
if (hashedPassword.equals(user.getPassword())) {

// after
if (PasswordCipher.checkPassword(password, user.getPassword())) {
```

BCrypt 해시는 salt 를 문자열 안에 포함하므로 `checkpw` 는 `user.getSalt()` 없이 동일한 판정을 한다.
`User.salt` 컬럼과 가입 경로(`saveUserByDto`)는 그대로 두었다 — 저장 포맷을 바꾸지 않았으므로 기존 계정도 그대로 로그인된다.

기존 `UserServiceTest` 의 로그인 3케이스(유저 없음 / 비밀번호 불일치 / 성공)가 그대로 통과해 회귀 검증이 된다.

## 2. 본문 없는 `POST /api/v1/products/search` 500 오류

### 문제

`@RequestBody(required = false)` 로 본문을 선택으로 열어 뒀지만 `ProductSearchDto.from` 이 `searchDto` 를 그대로 역참조해 NPE → 500 이 났다.

### 조치

`required = true` 로 되돌리는 대신 **본문 없음을 조건 없는 전체 목록 조회로 다루는 쪽**을 택했다. 근거:

- `required = false` 는 의도적으로 선언된 것이고,
- `ProductSpecification.search` 가 이미 "값이 없는 조건은 건너뛴다"로 동작하므로 빈 조건 DTO 를 그대로 받아들인다.

`src/main/java/com/sorryisme/fmarket/dto/request/ProductSearchDto.java`

```java
public static ProductSearchDto from(ProductSearchDto searchDto, Pageable pageable) {
  if (searchDto == null) {
    return ProductSearchDto.builder().pageable(pageable).build();
  }
  ...
}
```

### 테스트

`ProductControllerTest` 에서 "본문 없이 호출하면 500" 이라 적어 두고 비워 뒀던 자리를 실제 테스트로 교체했다.

- `searchesWithoutBody` — 본문 없이 POST 했을 때 200 이고, 서비스에 넘어간 조건의 `query`/`majorCategory`/`subcategory` 가 모두 null 이며 `pageable` 은 채워져 있음을 확인한다.

## 검증

`./gradlew spotlessApply check` — BUILD SUCCESSFUL.
컴파일·Spotless·전체 테스트에 더해 `jacocoTestCoverageVerification`(LINE 95% · BRANCH 90%) 게이트까지 통과했다.

## 변경 파일

- `src/main/java/com/sorryisme/fmarket/service/UserService.java`
- `src/main/java/com/sorryisme/fmarket/dto/request/ProductSearchDto.java`
- `src/test/java/com/sorryisme/fmarket/controller/ProductControllerTest.java`
