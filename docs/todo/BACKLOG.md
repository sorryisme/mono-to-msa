# BACKLOG

지금 하지 않고 미뤄 둔 작업을 누적한다. 출처(plan 잔여 항목 / 작업 중 발견 / 리뷰 보류 지적)와 무관하게 여기에 모은다.

항목 형식:

`- [ ] {할 일} — 배경: {왜 필요한가} / 보류 사유: {지금 안 하는 이유} / 관련: {파일·문서 경로}`

처리한 항목은 지우지 않고 `- [x]` 로 바꾼 뒤 완료 산출물 로그 경로를 덧붙인다.

## 열린 항목

- [ ] `UserService.login` 의 비밀번호 비교를 `PasswordCipher.checkPassword` 로 교체 — 배경: `login` 은 저장된 salt 로 직접 재해싱한 뒤 `String.equals` 로 비교한다. `BCrypt.checkpw` 가 하는 일을 손으로 다시 한 것이고 상수 시간 비교가 아니어서 타이밍 정보가 새어 나갈 여지가 있다. 그 결과 `PasswordCipher.checkPassword` 는 프로덕션 코드에서 아무도 쓰지 않는 상태다 / 보류 사유: 인증 경로 변경이라 utils 테스트 추가(2026-09-13) 범위를 넘어선다. 로그인 동작을 바꾸는 작업으로 따로 다룬다 / 관련: `src/main/java/com/sorryisme/fmarket/service/UserService.java:80`, `docs/logs/20260913/3-PasswordCipher-테스트-추가.md`
- [ ] `POST /api/v1/products/search` 를 요청 본문 없이 호출하면 500 이 난다 — 배경: `@RequestBody(required = false)` 로 본문을 선택으로 열어 뒀지만 `ProductSearchDto.from` 이 null 을 그대로 역참조해 NPE 가 난다. 조건 없는 전체 목록 조회로 쓰려는 의도로 보이는데 실제로는 동작하지 않고, 500 이라 클라이언트가 원인도 알 수 없다 / 보류 사유: controller 테스트 추가(2026-09-13) 중 발견했으나 소스 수정은 그 범위 밖이다. `from` 에서 null 을 빈 조건으로 다룰지, `required = true` 로 바꿀지 결정이 필요하다 / 관련: `src/main/java/com/sorryisme/fmarket/controller/ProductController.java:36`, `src/main/java/com/sorryisme/fmarket/dto/request/ProductSearchDto.java:20`, `docs/logs/20260913/5-controller-슬라이스-테스트-추가.md`

## 완료 항목

- [x] `jacocoTestCoverageVerification` 을 `check` 에 연결하고 커버리지 하한을 강제 — 배경: JaCoco 를 리포트 전용으로 먼저 도입해(2026-09-13) 기준선 LINE 80.6% / BRANCH 72.1% 를 측정했고, 테스트 보강 후 LINE 99.4% / BRANCH 94.1% 가 됐다 / 완료: 2026-09-13, LINE 95% · BRANCH 90% 하한을 `check` 에 연결 / 관련: `docs/logs/20260913/6-커버리지-임계값-게이트-도입.md`

- [x] `controller` 패키지 테스트 보강 — 배경: 라인 커버리지 17.2%. `ControllerResponseContractTest` 는 응답 봉투·상태 코드 계약만 확인해 핸들러 본문을 거의 타지 않았다 / 완료: 2026-09-13, 컨트롤러별 슬라이스 테스트 4개 추가로 라인 커버리지 17.2% → 100% / 관련: `docs/logs/20260913/5-controller-슬라이스-테스트-추가.md`

- [x] `aop` 패키지(AuthenticationAspect, IdempotencyAspect) 테스트 추가 — 배경: JaCoco 기준선 측정에서 라인 커버리지 0% 로 나왔다. 인증 통과 판정과 멱등성 키 처리는 잘못되면 인가 우회·중복 처리로 이어지는 지점인데 직접 검증하는 테스트가 없었다 / 완료: 2026-09-13, 라인 커버리지 0% → 100% / 관련: `docs/logs/20260913/2-aop-어스펙트-테스트-추가.md`
