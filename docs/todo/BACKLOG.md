# BACKLOG

지금 하지 않고 미뤄 둔 작업을 누적한다. 출처(plan 잔여 항목 / 작업 중 발견 / 리뷰 보류 지적)와 무관하게 여기에 모은다.

항목 형식:

`- [ ] {할 일} — 배경: {왜 필요한가} / 보류 사유: {지금 안 하는 이유} / 관련: {파일·문서 경로}`

처리한 항목은 지우지 않고 `- [x]` 로 바꾼 뒤 완료 산출물 로그 경로를 덧붙인다.

## 열린 항목

- [ ] 호출처 없는 엔티티 변경 메서드 정리 (`User.changePassword`, `User.softDelete`, `Store.updateStoreInfo`, `ProductReview.updateReview`) — 배경: 커버리지 제외 범위를 좁힌 뒤 미커버로 드러났고, 프로덕션 코드에서 호출하는 곳이 없다 / 보류 사유: 비밀번호 변경·회원 탈퇴·스토어·리뷰 수정 기능을 앞으로 만들지에 따라 "삭제"와 "기능 구현 + 테스트" 중 방향이 갈려 사용자 판단이 필요하다 / 관련: `docs/logs/20260914/1-커버리지-제외-범위-축소와-주문-입력-검증-수정.md`
- [ ] 커버리지 게이트(`check`)가 실제 MySQL 에 의존하지 않게 하기 — 배경: `repository/**`·`EntityMappingTest` 가 실제 MySQL 을 써서 DB 가 없으면 로컬 `check`·pre-push 가 전부 실패한다 / 보류 사유: Testcontainers 도입 또는 DB 테스트 태스크 분리 중 방식 결정이 필요하고, CI 는 이미 컨테이너로 DB 를 띄워 동작한다 / 관련: `docs/logs/20260914/1-커버리지-제외-범위-축소와-주문-입력-검증-수정.md`, `.github/workflows/verify.yml`

## 완료 항목

- [x] `UserService.login` 의 비밀번호 비교를 `PasswordCipher.checkPassword` 로 교체 — 배경: `login` 은 저장된 salt 로 직접 재해싱한 뒤 `String.equals` 로 비교했다. `BCrypt.checkpw` 가 하는 일을 손으로 다시 한 것이고 상수 시간 비교가 아니어서 타이밍 정보가 새어 나갈 여지가 있었다 / 완료: 2026-09-13, `checkPassword` 로 교체. BCrypt 해시가 salt 를 포함하므로 저장 포맷·가입 경로는 그대로 두었다 / 관련: `docs/logs/20260913/7-BACKLOG-열린-항목-처리.md`
- [x] `POST /api/v1/products/search` 를 요청 본문 없이 호출하면 500 이 난다 — 배경: `@RequestBody(required = false)` 로 본문을 선택으로 열어 뒀지만 `ProductSearchDto.from` 이 null 을 그대로 역참조해 NPE 가 났다 / 완료: 2026-09-13, `from` 에서 null 을 빈 조건으로 다뤄 조건 없는 전체 목록 조회로 동작시키고(`required = false` 유지) 컨트롤러 테스트를 추가 / 관련: `docs/logs/20260913/7-BACKLOG-열린-항목-처리.md`

- [x] `jacocoTestCoverageVerification` 을 `check` 에 연결하고 커버리지 하한을 강제 — 배경: JaCoco 를 리포트 전용으로 먼저 도입해(2026-09-13) 기준선 LINE 80.6% / BRANCH 72.1% 를 측정했고, 테스트 보강 후 LINE 99.4% / BRANCH 94.1% 가 됐다 / 완료: 2026-09-13, LINE 95% · BRANCH 90% 하한을 `check` 에 연결 / 관련: `docs/logs/20260913/6-커버리지-임계값-게이트-도입.md`

- [x] `controller` 패키지 테스트 보강 — 배경: 라인 커버리지 17.2%. `ControllerResponseContractTest` 는 응답 봉투·상태 코드 계약만 확인해 핸들러 본문을 거의 타지 않았다 / 완료: 2026-09-13, 컨트롤러별 슬라이스 테스트 4개 추가로 라인 커버리지 17.2% → 100% / 관련: `docs/logs/20260913/5-controller-슬라이스-테스트-추가.md`

- [x] `aop` 패키지(AuthenticationAspect, IdempotencyAspect) 테스트 추가 — 배경: JaCoco 기준선 측정에서 라인 커버리지 0% 로 나왔다. 인증 통과 판정과 멱등성 키 처리는 잘못되면 인가 우회·중복 처리로 이어지는 지점인데 직접 검증하는 테스트가 없었다 / 완료: 2026-09-13, 라인 커버리지 0% → 100% / 관련: `docs/logs/20260913/2-aop-어스펙트-테스트-추가.md`
