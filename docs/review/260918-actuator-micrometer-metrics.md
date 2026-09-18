# Actuator·Micrometer 및 application.yml 검토

- 검토자: Codex
- 대상: `chore/actuator-micrometer-metrics`, HEAD `02ac2da` (로컬 main과 동일). 브랜치 고유 커밋은 없으며 미커밋 변경 6개 파일과 관련 작업 문서를 검토했다.
- 기준: CLAUDE.md, java-spring-jpa 스킬, 실제 DataSourceConfiguration·인증·필터·스키마·테스트 설정, Spring Boot 4.1.1 메타데이터와 공식 문서.
- 결과: 운영 배포 계획이 없는 토이프로젝트라는 사용자 판단에 따라 P1·P2 지적 3건과 관련 BACKLOG 항목을 삭제했다(2026-09-18). 검토·검증 기록은 유지한다.

## 정상으로 확인한 부분 및 제한

- Spring Boot 4.1.1 캐시 JAR의 설정 메타데이터에서 관리 포트, access, exposure, health 그룹, probes, build 정보, histogram 및 Observation 설정 키를 확인했다. 눈에 보이는 YAML 계층 오류는 발견하지 않았다.
- `source/replica.pool-name`은 각 HikariDataSource에 직접 바인딩되는 현재 @ConfigurationProperties 구조와 맞는다. 일반 단일 DataSource의 `spring.datasource.hikari` 구조로 옮길 필요가 없다.
- Actuator 및 Prometheus 의존성, buildInfo 설정에서 추가 결함은 확인하지 않았다.
- `gradlew.bat --offline compileJava`: BUILD SUCCESSFUL, UP-TO-DATE. 이번 실행에서 소스를 재컴파일했다는 의미는 아니다.
- `git diff --check`: Dockerfile·docker-compose.yml 추가 줄 5곳에 CRLF 관련 trailing whitespace를 보고했다.
- 전체 테스트·DB 기동·Docker 기동·HTTP 스크레이프는 재실행하지 않았다. 테스트 설정은 별도 application.yml이므로 기존 테스트 통과만으로 main의 management 설정이 검증되지는 않는다.
- 기존 작업 로그에 적힌 155개 테스트 통과 및 readiness 장애 판정 90초는 이전 작업자의 기록이며 이번 리뷰의 재현 결과가 아니다. readiness에 DB를 포함하는 선택 자체는 부하 테스트 사전 점검 목적에 맞지만 실제 폴링 타임아웃 검증은 필요하다.
- `MYSQL_PASSWORD` 필수 주입, 기본 호스트 db-master/db-replica는 Compose 실행 전제와 맞는다. 호스트 직접 실행에는 호스트명·replica 포트 환경변수 설정이 필요하다.

## 공식 근거

- [관리 서버 포트와 주소](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
- [모든 Meter의 공통 태그](https://docs.spring.io/spring-boot/reference/actuator/metrics.html#actuator.metrics.customizing.common-tags)
- [Observation 공통 태그](https://docs.spring.io/spring-boot/reference/actuator/observability.html#actuator.observability.common-tags)
