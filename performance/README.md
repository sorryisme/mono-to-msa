# 부하 테스트 (k6 + 격리 Compose)

실제 HTTP 요청으로 읽기 성능, 세션 기반 사용자 흐름, 주문 재고 경합, 멱등성, 취소·확정 경합을 반복 검증한다.
계획과 배경은 `docs/todo/260914-부하-테스트-자동화.md`, 구현 범위는 `docs/plan/260918-부하-테스트-1-2차.md` 참고.

## 실행

```bash
bash scripts/load-test.sh smoke                        # PR 과 같은 smoke (5 VU, 60초)
bash scripts/load-test.sh user-flow --vus 20 --duration 60s
bash scripts/load-test.sh order-contention --vus 100 --contention-stock 50
bash scripts/load-test.sh idempotency
bash scripts/load-test.sh order-race --rounds 50 --vus 10
```

필요한 것: Docker Desktop(Compose v2), JDK 21(Gradle 로 jar 를 만든다), openssl. **k6 를 호스트에 설치하지 않는다** — `grafana/k6` 컨테이너가
같은 Compose 네트워크 안에서 `http://java-app:8080` 을 호출한다. 호스트 포트를 하나도 열지 않으므로 개발용 `docker-compose.yml` 이 떠 있어도 충돌하지 않는다.

한 번 실행하면 다음이 순서대로 일어난다. 어느 단계든 실패하면 그 자리에서 멈추고 정리한 뒤 0 이 아닌 코드로 끝난다.

1. `./gradlew bootJar` → jar 만 담은 런타임 이미지 빌드 (`--skip-build` 로 재사용 가능)
2. `fmarket-load-<runId>` 프로젝트명으로 master/replica/app 기동 (실행마다 새 자격증명·새 볼륨)
3. master `read_only=0`, replica `read_only=1`, `Replica_IO/SQL_Running: Yes` 확인
4. `/actuator/health/readiness` 폴링 (readinessState + source/replica DataSource, 최대 180초)
5. fixture: 시드 사용자 1명을 회원가입 API 로 만들고 그 BCrypt 해시를 복사해 사용자 N 명 + 시나리오별 상품·옵션·재고 생성
6. fixture 가 replica 에 반영될 때까지 대기 (로그인·조회는 replica 를 읽는다)
7. k6 실행 (`--summary-export` + `k6-facts.txt`)
8. 활성 트랜잭션이 없어질 때까지 기다린 뒤 master 에서 시나리오별 사후 검증 SQL 실행. `FAIL` 행이 하나라도 있으면 실패
9. 앱·DB 로그 수집 후 `down -v --rmi local` (해당 프로젝트만). 종료 코드는 7·8 의 판정을 그대로 보존

산출물은 `build/loadtest/<runId>/` 에 남는다: `k6.log`, `summary.json`, `summary-export.json`, `k6-facts.txt`, `verify.txt`, `app.log`, `db.log`, 그리고 리포트 두 개.

### 리포트

- `report.md`: threshold 결과, 주요 수치, DB 사후 검증을 표로 정리한 마크다운. CI 에서는 Actions 실행 페이지 상단(job summary)에 그대로 게시된다.
- `report.html`: k6 web dashboard export. RPS·지연 percentile·VU·실패율의 시간축 그래프와 요약 표가 든 단일 HTML 파일이다. 아티팩트에서 내려받아 브라우저로 연다.
  집계 주기는 `K6_WEB_DASHBOARD_PERIOD`(기본 2초). 실행이 그보다 몇 배 짧으면 k6 가 "not enough data" 로 생성을 건너뛴다 — 수백 ms 에 끝나는 경합 시나리오는 원래 없고, smoke/user-flow 에는 있다.

리포트 생성은 판정 뒤에 실행되며 실패해도 종료 코드를 바꾸지 않는다.

### 방어 조건

- 스크립트는 외부 DB 주소·대상 URL 을 받지 않는다. `LOADTEST_TARGET_URL` 같은 변수가 있으면 시작을 거부한다.
- Compose 파일에 `container_name`·호스트 포트·`env_file` 이 없다. 정리 대상은 `fmarket-load-` 로 시작하는 자기 프로젝트뿐이다.
- `schema.sql` 이 기존 테이블을 DROP 하므로 이 구성을 공유·운영 DB 에 붙이면 안 된다.

## 측정 조건 (운영과 다른 점)

`loadtest` Spring 프로파일로 기동한다 (`src/main/resources/application-loadtest.yml`).

- `fmarket.http-log.body-enabled=false`: `MDCLoggingFilter` 가 요청/응답 본문 캐싱 wrapper 를 만들지 않고 `status`·`elapsedMs` 한 줄만 남긴다. MDC 는 유지.
- Hibernate SQL 로그 WARN, `format_sql=false`.
- 그 외(HikariCP 풀 크기 등)는 기본값 그대로다. 명시적 풀 예산은 측정 후 정한다.

즉 여기서 얻는 수치는 "본문 로깅을 끈 조건" 의 수치다. 운영 로깅 정책과 비교할 때는 이 차이를 함께 적는다.

## 판정 규칙 (공통)

`performance/k6/lib/thresholds.js`, `lib/http.js`.

| threshold | 적용 범위 | 값 |
|---|---|---|
| `checks{phase:main}` | 본 측정 구간 기능 check. `setup`/`login` 태그 요청은 제외 | `rate==1` |
| `http_req_failed{phase:main}` | 본 측정 HTTP 실패율. `responseCallback` 이 허용한 409 는 실패로 세지 않는다 | `rate<0.01` |
| `server_errors` | 5xx | `count==0` |
| `transport_errors` | 타임아웃·연결 실패 (status 0) | `count==0` |
| `unexpected_rejects` | 허용 목록 밖의 4xx, 또는 본문 `code` 가 기대와 다른 응답 | `count==0` |

예상된 업무 거절은 시나리오가 명시한 `code` 만 인정한다 (`classify(res, { OUT_OF_STOCK: 409 })`). 전역으로 409 를 허용하지 않는다.
`check()` 실패는 그 자체로 종료 코드를 만들지 않으므로 `checks{phase:main}: rate==1` threshold 가 이를 대신한다.

응답이 유실된 요청(`transport_errors`)은 DB 에 커밋됐을 수 있다. 사후 검증은 "DB 주문 수 ∈ [k6 201 수, k6 201 수 + 유실 수]" 로 판정하고 유실 수를 `INFO` 로 남긴다.

## 시나리오와 부하 모델

| 시나리오 | 모델 | 데이터 | 허용 거절 | 사후 검증 |
|---|---|---|---|---|
| `smoke` | 닫힌, constant-vus 5 × 60s, iteration 당 3 요청 + 0.5~1s 대기 | data.sql 상품 1~3, 로그인 없음 | 없음 | 없음. p95 < 1000ms |
| `user-flow` | 닫힌, constant-vus 20 × 60s, VU 당 전용 계정, 최초 iteration 만 로그인(`noCookiesReset`), iteration 당 4 요청 + 1s | 옵션 20개 × 재고 1,000,000 에 분산 | 없음 | 주문 수 = 201 수, 재고 차감 합 = 주문 수량 합, 전부 PENDING. p95 < 1500ms |
| `order-contention` | per-vu-iterations, VU 100 × 1회, 수량 1~3. setup 에서 전원 로그인 후 주문만 발사(시작 신호 정렬 없음) | 옵션 1개, 재고 50 | `OUT_OF_STOCK` | 성공 수량 합 ≤ 초기 재고, 최종 재고 ≥ 0, 초기−최종 = 성공 수량 합, DB 주문 수 = 201 수 |
| `idempotency` | duplicate: 그룹 10 × VU 5 (같은 사용자·본문·키 동시 전송). failurePath: VU 5, 옵션 2개(후순위 품절) → 같은 키 재시도 | 전용 옵션 2개 | `DUPLICATE_REQUEST`, `OUT_OF_STOCK` | 그룹당 주문 1건·키 1건, 재고 = 초기−그룹 수, failurePath 주문 0건, 키 잔존 여부는 계약대로 |
| `order-race` | setup 에서 PENDING 주문 50개 생성. cancel/confirm 두 executor(shared-iterations, VU 10)가 같은 순번 주문을 동시 전송 | 옵션 1개 | `ORDER_STATUS_NOT_CHANGEABLE` | PENDING 없음, CANCELLED+COMPLETED = 50, 재고 = 초기−COMPLETED |

VU 수는 동시 사용자 수이지 요청률이 아니다. iteration 하나가 여러 요청을 보내므로 요약의 `http_reqs` rate(RPS)와 `iterations` rate 를 구분해 읽는다.
포화점 탐색용 arrival-rate 시나리오와 nightly/stress/soak 는 아직 없다 (BACKLOG).

### 멱등키 실패 경로의 재시도 계약

`IdempotencyAspect` 는 키를 `saveAndFlush` 한 뒤 `proceed()` 하며 트랜잭션 advice 와의 순서를 지정하지 않는다. 따라서 "키 저장 후 주문 실패" 시 키가
주문과 함께 롤백되는지(재시도 허용)와 키만 남는지(영구 거절)는 실행으로 확인한다.
2026-09-18 실측: 첫 요청 5건 모두 `OUT_OF_STOCK`, 같은 키 재시도 5건 모두 다시 `OUT_OF_STOCK`(`DUPLICATE_REQUEST` 0건), `idempotency_keys` 잔존 0건,
선행 옵션 재고 원복. 즉 **키가 주문과 같은 트랜잭션에서 롤백되어 재시도가 허용된다(`RETRY_ALLOWED`)**. 트랜잭션 인터셉터가 aspect 바깥에서
실행되고 있다는 뜻이며, 스크립트 기본값을 이 계약으로 고정했다. `--retry-contract PERMANENT_REJECT` 또는 `''`(관측만)로 바꿀 수 있다.
aspect 순서를 명시하지 않은 코드는 그대로이므로, 이 계약이 깨지면 `fail_keys_per_retry_contract` 검증이 실패로 알려 준다.

## CI

`.github/workflows/load-test.yml`

- `pull_request`: smoke. GitHub 호스팅 러너는 실행 간 편차가 커서 절대 TPS 를 게이트하지 않는다. threshold 는 기동 실패·심각한 회귀만 잡는 임시 안전선이다.
- `workflow_dispatch`: `scenario`, `vus`, `duration`, `users` 만 입력받는다.
- 결과는 `load-test-<scenario>-<run>` 아티팩트로 남는다.

## 기준선

threshold 숫자(p95 1000/1500/3000ms 등)는 첫 자동화 실행을 위한 임시값이다. 같은 환경에서 3~5회 돌려 기준선을 만든 뒤 조정한다.
3~5회는 출발점일 뿐, SLO 나 작은 성능 차이의 통계적 입증으로 해석하지 않는다.
