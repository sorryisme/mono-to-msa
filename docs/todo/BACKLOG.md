# BACKLOG

지금 하지 않고 미뤄 둔 작업을 누적한다. 출처(plan 잔여 항목 / 작업 중 발견 / 리뷰 보류 지적)와 무관하게 여기에 모은다.

항목 형식:

`- [ ] {할 일} — 배경: {왜 필요한가} / 보류 사유: {지금 안 하는 이유} / 관련: {파일·문서 경로}`

처리한 항목은 지우지 않고 `- [x]` 로 바꾼 뒤 완료 산출물 로그 경로를 덧붙인다.

## 열린 항목

- [ ] `jacocoTestCoverageVerification` 을 `check` 에 연결하고 커버리지 하한을 강제 — 배경: JaCoco 를 리포트 전용으로 먼저 도입했고(2026-09-13) 전체 테스트 기준선은 LINE 80.6% / BRANCH 72.1% 로 측정됐다. 하한을 걸어야 수치가 조용히 내려가는 것을 막는다 / 보류 사유: 게이트를 바로 걸면 `aop`·`controller` 처럼 지금 낮은 패키지 때문에 빌드가 깨지거나, 반대로 기준을 느슨하게 잡아 의미 없는 숫자가 된다. 아래 테스트 보강 항목을 먼저 처리한 뒤 정한다 / 관련: `build.gradle`, `docs/logs/20260913/1-JaCoco-커버리지-도입.md`
- [ ] `aop` 패키지(AuthenticationAspect, IdempotencyAspect) 테스트 추가 — 배경: JaCoco 기준선 측정에서 라인 커버리지 0% 로 나왔다. 인증 통과 판정과 멱등성 키 처리는 잘못되면 인가 우회·중복 처리로 이어지는 지점인데 직접 검증하는 테스트가 없다 / 보류 사유: 이번 작업 범위는 커버리지 측정 도구 도입까지였고, 테스트 보강은 별도 작업 단위다 / 관련: `src/main/java/com/sorryisme/fmarket/aop/`, `docs/logs/20260913/1-JaCoco-커버리지-도입.md`
- [ ] `controller` 패키지 테스트 보강 — 배경: 라인 커버리지 17.2%. 현재 `ControllerResponseContractTest` 는 응답 봉투·상태 코드 계약만 확인해 핸들러 본문을 거의 타지 않는다 / 보류 사유: 위와 동일하게 별도 테스트 작업 단위 / 관련: `src/test/java/com/sorryisme/fmarket/controller/`, `docs/logs/20260913/1-JaCoco-커버리지-도입.md`

## 완료 항목

(없음)
