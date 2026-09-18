# Actuator · Micrometer 관측성 도입

- 작업일: 2026-09-18
- 배경: `docs/todo/260914-부하-테스트-자동화.md` 의 "4. 관측성" 과 `docs/review/260918-부하-테스트-자동화.md` 의 도입 순서 제안(2차: JVM·HikariCP 지표). 부하 테스트를 붙이기 전에 서버 쪽 지표를 먼저 확보한다.
- 범위: 의존성·설정만 추가했다. Java 소스는 변경하지 않았고 수집기(Prometheus 서버)나 대시보드는 붙이지 않았다.

## 변경 내용

### build.gradle

- `spring-boot-starter-actuator`, `io.micrometer:micrometer-registry-prometheus` 추가.
- `springBoot { buildInfo() }` 추가 — 어떤 빌드에서 측정했는지 `/actuator/info` 로 확인하기 위해서다.

Boot 4 에서는 Prometheus 자동설정이 `spring-boot-micrometer-metrics` 에 있고, 이 모듈은 actuator 스타터가 `spring-boot-starter-micrometer-metrics` 를 통해 이미 가져온다. 별도 스타터를 더 넣을 필요는 없었고 레지스트리 하나만 추가하면 `PrometheusScrapeEndpoint` 가 켜진다. HikariCP 풀 지표(`DataSourcePoolMetricsAutoConfiguration`)는 `spring-boot-jdbc` 에 있어 data-jpa 스타터만으로 이미 classpath 에 있다.

### src/main/resources/application.yml

`management` 절 신설.

- **관리 포트 분리** (`management.server.port: 8081`). 이 프로젝트에는 인증/인가 계층이 없다. 서비스 포트에 지표를 같이 열면 그대로 외부에 노출되고, 부하 테스트에서도 측정 대상 경로와 스크레이프 요청이 같은 커넥터를 공유하게 된다.
- **기본 접근 차단** (`management.endpoints.access.default: none`) 후 `health`, `info`, `metrics`, `prometheus` 만 `read-only` 로 개방. `env`, `heapdump`, `threaddump` 등은 열지 않는다.
- **readiness 그룹에 `db` 포함**. `readinessState` 만으로는 DB 연결을 보지 못한다. DataSource health 는 `sourceDataSource`/`replicaDataSource` 양쪽에 대해 잡히므로 부하 시작 전 두 연결을 한 번에 확인할 수 있다.
- **HTTP 서버 histogram** (`percentiles-histogram: http.server.requests: true`, 5ms~10s). k6 가 재는 값은 클라이언트 관점(네트워크·대기 포함)이므로 서버 관점 분포를 따로 남긴다. 상·하한을 줘 버킷 수가 무한정 늘지 않게 했다.
- **공통 태그** `application` (`management.metrics.tags`). 처음에는 `management.observations.key-values` 로 넣었는데, 이는 Observation 기반 지표에만 적용돼 `http_server_requests_*` 에만 라벨이 붙고 JVM·HikariCP 지표는 빠졌다(233개 중 54개). 리뷰 지적(`docs/review/260918-actuator-micrometer-metrics.md`)을 받아 `management.metrics.tags` 로 교체했고 236개 중 236개 전부에 붙는 것을 확인했다. 트레이싱이 없어 `observations.key-values` 는 제거했다.
- **HikariCP pool 이름 고정**: `spring.datasource.source.pool-name: fmarket-source`, `replica.pool-name: fmarket-replica`. 지정하지 않으면 양쪽이 `HikariPool-1`/`HikariPool-2` 로 잡혀 지표에서 어느 쪽이 source 인지 구분할 수 없다. 테스트 설정(`src/test/resources/application.yml`)에도 같은 이름을 넣었다.

### Dockerfile / docker-compose.yml

`EXPOSE 8080 8081`, java-app 에 `8081:8081` 포트 매핑 추가.

## 검증

설정 키 14개를 Boot 4.1.1 `spring-configuration-metadata.json` 과 대조해 전부 유효함을 먼저 확인했다. yml 의 오타는 예외 없이 조용히 무시되므로 기동만으로는 오타를 잡을 수 없다.

### 빌드·테스트

- `./gradlew check` → BUILD SUCCESSFUL. 전체 테스트 155개 통과, JaCoCo 게이트(LINE 95% / BRANCH 92%)와 Spotless 통과.
- `bash scripts/test-affected.sh` → 전체 테스트 통과.
- 테스트 소스는 변경하지 않았고 `@Disabled`/`@Ignore` 는 0건이다.

로컬에 설치된 `MySQL84` 윈도우 서비스가 3306 을 점유하고 있어 처음에는 컨테이너가 뜨지 않았고, 테스트 35개가 `Access denied for user 'sorry'@'localhost'` 로 실패했다. 서비스를 중지한 뒤 `docker compose up -d db-master db-replica` 로 정상화했다. 이번 변경과는 무관한 환경 문제였고, 변경을 stash 한 상태에서도 동일하게 실패함을 대조해 확인했다.

### 실제 MySQL master/replica 기동

`--MYSQL_MASTER_HOST=localhost --MYSQL_MASTER_PORT=3306 --MYSQL_REPLICA_HOST=localhost --MYSQL_REPLICA_PORT=3307` 로 기동. 복제 상태는 `Replica_IO_Running: Yes`, `Replica_SQL_Running: Yes`, `Seconds_Behind_Source: 0`.

- 기동 로그: `Tomcat started on port 8080`, `Tomcat started on port 8081`, `Exposing 4 endpoints beneath base path '/actuator'`.
- `GET :8081/actuator/health` → 200, `{"groups":["liveness","readiness"],"status":"UP"}`
- `hikaricp_connections{pool="fmarket-source"} 10.0`, `{pool="fmarket-replica"} 10.0` — 두 풀이 이름으로 구분된다.
- `POST :8080/api/v1/products/search` → 200 정상 응답.
- `GET :8080/actuator/prometheus` → 404 (서비스 포트에 노출되지 않음), `GET :8081/actuator/env` → 404 (`access: none`).

H2 in-memory 로 먼저 확인한 항목도 함께 남긴다. `http_server_requests_seconds_bucket{application="fmarket",uri="/api/v1/products/{id}",le="0.005"}` 생성 확인 — histogram, 공통 태그, URI 템플릿화가 모두 동작한다.

### readiness 가 replica 장애를 실제로 잡는가

`docker stop mysql-replica` 후 측정.

- `/actuator/health/readiness` → `{"status":"DOWN"}` 503. readiness 그룹이 replica 연결까지 본다는 것이 확인됐다.
- `/actuator/health/liveness` → UP 유지. 두 그룹이 의도대로 분리돼 있다.
- **단 DOWN 판정까지 90초가 걸렸다.** DataSource 빈 3개(`dataSource` 프록시, source, replica)를 차례로 검사하고 각각 Hikari `connection-timeout` 기본값 30초를 기다리기 때문이다. `docker start mysql-replica` 후에는 38ms 만에 UP 으로 복구됐다.

부하 테스트 실행 스크립트는 이 값을 알고 폴링 timeout 을 따로 둬야 한다. `connection-timeout` 을 줄이면 빨라지지만 부하 중 커넥션 대기 동작이 함께 바뀌므로, 풀 크기와 같이 실측 후 정하는 편이 맞다. 계획 문서의 "선행 작업 1" 에 항목으로 남겼다.

## 남은 것

- 스크레이프 주기와 시계열 보관 방식. 지금은 엔드포인트만 있고 저장하는 곳이 없다.
- MySQL exporter, 컨테이너 리소스 지표, replica 지연.
- Hibernate 통계(`generate_statistics`)는 오버헤드가 있어 넣지 않았다. 필요하면 부하 실행 프로파일에서만 켠다.
- readiness DOWN 판정 90초. 폴링 timeout 을 두거나 Hikari `connection-timeout` 을 조정해야 한다.
- Docker 이미지 빌드(`EXPOSE 8080 8081`)와 compose 의 8081 매핑은 컨테이너로 기동해 확인하지 않았다. 검증은 `bootRun` 으로 했다.
- 관리 포트는 부하 테스트용 Compose 에서도 고정 호스트 포트를 쓰지 않도록 조정해야 한다(계획 문서 "선행 작업 1. 실행 환경" 참고).

관련: `docs/todo/260914-부하-테스트-자동화.md`, `docs/review/260918-부하-테스트-자동화.md`
