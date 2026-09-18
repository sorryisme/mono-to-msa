# BACKLOG

지금 하지 않고 미뤄 둔 작업을 누적한다. 출처(plan 잔여 항목 / 작업 중 발견 / 리뷰 보류 지적)와 무관하게 여기에 모은다.

항목 형식:

`- [ ] {할 일} — 배경: {왜 필요한가} / 보류 사유: {지금 안 하는 이유} / 관련: {파일·문서 경로}`

처리한 항목은 지우지 않고 `- [x]` 로 바꾼 뒤 완료 산출물 로그 경로를 덧붙인다.

## 열린 항목

- [x] 부하 테스트 자동화 도입 (1차·2차) — 배경: 실제 HTTP 처리량, 세션 사용자 흐름, 주문 재고·멱등성·상태 전이 경합과 master/replica 병목을 반복 검증할 환경이 없다 / 처리: 격리 Compose + k6 시나리오 5개 + 사후 검증 SQL + PR smoke 워크플로 (`docs/logs/20260918/2-부하-테스트-자동화-1-2차.md`). 3차 항목은 아래로 분리 / 관련: `docs/todo/260914-부하-테스트-자동화.md`, `performance/README.md`
- [ ] 부하 테스트 3차: nightly 스케줄·stress·soak — 배경: PR smoke 는 기동 실패만 잡는다. 포화점(arrival-rate)과 장시간 누수(메모리·커넥션·GC·replica 지연)는 별도 실행이 필요하다 / 보류 사유: GitHub 호스팅 러너는 실행 간 편차가 커서 절대 TPS 게이트가 무의미하고, 사양 고정 러너 또는 동일 별도 환경이 먼저 필요하다. 같은 환경 3~5회 기준선도 아직 없다 / 관련: `docs/todo/260914-부하-테스트-자동화.md` "6. 단계 부하와 Soak", `.github/workflows/load-test.yml`
- [ ] 부하 테스트 관측성 확장: MySQL(커넥션 수·slow query·row lock wait·deadlock)·replica 지연·컨테이너 CPU/메모리 수집과 시계열 보관 — 배경: 지금은 Actuator 엔드포인트만 있고 스크레이프·보관이 없어 k6 결과와 서버 지표를 같은 실행 단위로 대조하기 어렵다 / 보류 사유: 수집기(Prometheus 등)·MySQL exporter·cAdvisor 를 Compose 에 더하는 규모이고, 1차·2차 결과가 먼저 나와야 무엇을 볼지 정할 수 있다 / 관련: `docs/todo/260914-부하-테스트-자동화.md` "4. 관측성", `performance/docker-compose.loadtest.yml`
- [ ] 부하 테스트 기준선 수립과 threshold 조정 — 배경: 현재 p95 1000/1500/3000ms 는 첫 자동화용 임시값이다 / 보류 사유: 같은 환경에서 3~5회 반복한 실측이 필요하다 / 관련: `performance/README.md` "기준선"
- [ ] 주문 목록 인덱스 `(user_id, order_date, id)` 필요성 판단 — 배경: `order` 테이블에 PK·FK 외 인덱스가 없다 / 보류 사유: 대표 데이터로 `EXPLAIN ANALYZE` 를 확인하고 쓰기 비용까지 본 뒤 결정한다 / 관련: `docs/todo/260914-부하-테스트-자동화.md` "인덱스·쿼리 확인 대상"
- [ ] 호출처 없는 엔티티 변경 메서드 정리 (`User.changePassword`, `User.softDelete`, `Store.updateStoreInfo`, `ProductReview.updateReview`) — 배경: 커버리지 제외 범위를 좁힌 뒤 미커버로 드러났고, 프로덕션 코드에서 호출하는 곳이 없다 / 보류 사유: 비밀번호 변경·회원 탈퇴·스토어·리뷰 수정 기능을 앞으로 만들지에 따라 "삭제"와 "기능 구현 + 테스트" 중 방향이 갈려 사용자 판단이 필요하다 / 관련: `docs/logs/20260914/1-커버리지-제외-범위-축소와-주문-입력-검증-수정.md`
- [ ] 커버리지 게이트(`check`)가 실제 MySQL 에 의존하지 않게 하기 — 배경: `repository/**`·`EntityMappingTest` 가 실제 MySQL 을 써서 DB 가 없으면 로컬 `check`·pre-push 가 전부 실패한다 / 보류 사유: Testcontainers 도입 또는 DB 테스트 태스크 분리 중 방식 결정이 필요하고, CI 는 이미 컨테이너로 DB 를 띄워 동작한다 / 관련: `docs/logs/20260914/1-커버리지-제외-범위-축소와-주문-입력-검증-수정.md`, `.github/workflows/verify.yml`
