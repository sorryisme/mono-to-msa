# Spring Boot 3.3.5 → 4.1.1 업그레이드

- 작업일: 2026-09-11
- 결과: 전체 테스트 59/59 통과, 앱 기동 및 API 응답 확인 완료

## 배경

기존 3.3.5 는 OSS 지원이 2025-06-30, 상용 연장 지원마저 2026-06-30 에 종료되어
작업 시점 기준 어떤 경로로도 보안 패치를 받을 수 없는 상태였다.

Spring Boot 에는 OSS LTS 가 없다. 모든 마이너는 릴리스 후 13개월만 지원되고,
"LTS" 로 불릴 만한 것은 메이저의 마지막 마이너(2.7, 3.5)에 붙는 5년 **상용** 연장 지원뿐이다.
따라서 실질적 기준은 "OSS 지원이 남아 있는 최신 버전" 이며, 작업 시점에 그 조건을 만족하는 것은
4.1(2027-07-31 까지)뿐이었다. 4.0 은 2026-12-31 에 종료되어 목적지가 될 수 없었다.

## 진행 순서

베이스라인이 레드인 상태에서 버전을 올리면 "프레임워크 이동으로 깨진 것" 과
"원래 깨져 있던 것" 을 구분할 수 없으므로, 단계마다 그린을 확인하며 진행했다.

| 단계 | 내용 | 결과 |
|---|---|---|
| 1 | 테스트 DB 설정 정합성 수정 | 59/59 |
| 2 | 3.3.5 → 3.5.16 (경유지) | 59/59 |
| 3 | Gradle 9.7.1 + Spotless 8.10.2 (툴체인 격리 검증) | 59/59 |
| 4 | 3.5.16 → 4.1.1 | 59/59 |

## 1단계 — 테스트가 처음부터 깨져 있던 원인

작업 시작 시점에 이미 59개 중 30개가 실패하고 있었고, 원인은 두 가지가 겹쳐 있었다.

### ① `master.cnf` 가 통째로 무시되고 있었다

매퍼 SQL 의 `"USER"` 큰따옴표 식별자와 대문자 테이블명은 프로젝트의 `master.cnf` 가 설정하는
`ANSI_QUOTES` / `lower_case_table_names=1` 을 전제로 작성된 것이다. 그런데 Windows 바인드
마운트가 컨테이너 안에서 0777 로 보여 MySQL 이 보안상 설정 파일을 버리고 있었다.

```
mysql: [Warning] World-writable config file '/etc/mysql/conf.d/master.cnf' is ignored.
```

**매퍼 SQL 은 처음부터 옳았다.** 설정이 조용히 증발한 것이 원인이었다.

CLI 인자는 권한 검사를 받지 않고 파일보다 우선하므로, `docker-compose.yml` 의 `command:` 로
같은 값을 중복 지정해 해결했다. cnf 마운트는 정상 동작하는 Linux/CI 를 위해 남겨두었다.

> `lower_case_table_names` 는 **초기화 시점에만** 적용된다. 기존 볼륨을 가진 환경에서는
> `docker compose down -v` 없이 이 수정이 적용되지 않는다. 팀 공유 필요.

### ② 테스트 컨텍스트가 두 경로로 갈린다

- `@SpringBootTest` — 전체 컨텍스트. `DataSourceConfiguration` 이 바인딩하는
  `spring.datasource.source` / `replica` prefix 를 사용
- `@MybatisTest` — 테스트 슬라이스. `TypeExcludeFilter` 가 일반 `@Configuration` 을 제외하므로
  `DataSourceAutoConfiguration` 이 읽는 **플랫 프로퍼티**가 별도로 필요

둘 중 하나만 정의하면 반대쪽이 깨진다. 플랫 프로퍼티가 없으면 슬라이스가 임베디드 H2 로 조용히
폴백해 MySQL DDL 파싱에 실패하는데, 이 증상이 원인 규명의 결정적 단서였다.
`src/test/resources/application.yml` 에 두 형태를 모두 정의하고 이유를 주석으로 남겼다.

## 4단계 — Boot 4 에서 실제로 걸린 것들

### `spring-boot-starter-aop` 소멸

Boot 4 모듈 재편으로 아티팩트 자체가 사라졌다. 후속은 `spring-boot-starter-aspectj`.

### `ContentCachingRequestWrapper` 단일 인자 생성자 제거

3.5.16 단계에서 `[removal]` 경고로 먼저 드러났고, 4.1.1 에서 컴파일 에러가 되었다.
한도 없는 캐싱이 제거되고 상한이 필수가 되었다. 요청 바디는 로그 용도로만 쓰이므로
64KB 상한을 명시했다 (`MDCLoggingFilter`).

### 테스트 슬라이스 패키지 이동

```
org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
  → org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
```

매퍼 테스트 7개 파일의 import 수정.

### Spock — JUnit 전환은 불필요했다

Boot 4.0 마이그레이션 가이드는 *"Spring Boot's Spock integration has been removed as Spock
does not yet support Groovy 5"* 라고 명시하며, 이 때문에 사전 검토에서는 Spock 12개 스펙의
JUnit 전환을 3단계의 선행 조건으로 잡았다.

실제로는 **`spock-core:2.4-groovy-5.0` 변형이 이미 배포되어 있었고 정상 동작했다.**
가이드의 서술이 그 시점 기준이었던 것으로, Groovy 4 변형(`2.4-groovy-4.0`)은 Boot 4 BOM 이
관리하는 Groovy 5.0.8 을 거부하지만 Groovy 5 변형으로 바꾸면 그대로 통과한다.

```
IncompatibleGroovyVersionException: Spock 2.4.0-groovy-4.0 is not compatible with Groovy 5.0.8
```

전면 전환을 선제적으로 시작하지 않고 "무엇이 실제로 깨지는지" 부터 확인한 덕분에
회피할 수 있었던 작업이다.

### Jackson 3

`com.fasterxml.jackson` → `tools.jackson` (3.1.5). 코드에 직접 import 가 없어 컴파일은
영향받지 않았으나, 테스트가 HTTP 직렬화를 전혀 커버하지 않아 실제 기동 후 확인했다.
한글 UTF-8, `LocalDateTime` ISO-8601, 중첩 객체, `ResponseDto` 래퍼 모두 정상.

## 최종 버전

| 항목 | 이전 | 이후 |
|---|---|---|
| Spring Boot | 3.3.5 | 4.1.1 |
| Spring Framework | 6.1.x | 7.0.9 |
| Jackson | 2.x (`com.fasterxml`) | 3.1.5 (`tools.jackson`) |
| Gradle | 8.10.2 | 9.7.1 |
| Spotless | 6.25.0 | 8.10.2 |
| dependency-management | 1.1.6 | 1.1.7 |
| mybatis-spring-boot-starter | 3.0.3 | 4.1.0 |
| Spock | 2.3-groovy-4.0 | 2.4-groovy-5.0 |
| AOP 스타터 | spring-boot-starter-aop | spring-boot-starter-aspectj |
| Java | 21 | 21 (변경 없음) |

## 변경 파일

- `build.gradle` — 버전 및 AOP 스타터 교체
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.7.1
- `docker-compose.yml` — db-master/db-replica 에 cnf 설정을 CLI 인자로 중복 지정
- `src/test/resources/application.yml` — 플랫 + source/replica 프로퍼티 병행 정의
- `src/main/java/.../filter/MDCLoggingFilter.java` — 캐시 상한 명시
- `src/test/groovy/.../mapper/*.groovy` (7개) — `AutoConfigureTestDatabase` import 경로

매퍼 XML 과 `schema.sql` 은 변경하지 않았다.

## 검증

```
bash scripts/test-affected.sh
  → 컴파일 / spotlessCheck / 테스트 모두 통과
  → tests=59 skipped=0 failures=0 errors=0

./gradlew dependencies --configuration runtimeClasspath
  → spring-boot-starter-web 4.1.1, spring-core 7.0.9,
    tools.jackson.core:jackson-databind 3.1.5, mybatis-spring-boot-starter 4.1.0

./gradlew bootRun
  → Started FmarketApplication in 2.513 seconds
  → GET  /api/v1/products/category  200, 중첩 카테고리 JSON 정상
  → POST /api/v1/user/signup        200, 요청 역직렬화 및 DB 저장 확인
```

## 자격증명 정리

초기 커밋 직전, 실제 DB 비밀번호가 `application.yml` 의 기본값
(`${MYSQL_PASSWORD:실제값}`)으로 들어가 이력에 남으려던 것을 발견해 함께 정리했다.
`.env` 는 gitignore 대상이었지만 같은 문자열이 yml 기본값으로 중복돼 있었다.

- `application.yml` (main/test) 의 비밀번호 기본값 제거 → `${MYSQL_PASSWORD}`
  값이 없으면 빈 비밀번호로 조용히 접속을 시도하는 대신 기동 단계에서 즉시 실패한다.
- `build.gradle` 이 `.env` 를 읽어 `test` / `bootRun` 태스크에 주입한다.
  두 JVM 모두 `.env` 를 자동으로 읽지 않기 때문이다.
- CI 는 워크플로에서 매 실행마다 임의 비밀번호를 생성해 주입하므로 영향 없다.

프로젝트 자체 시크릿 스캐너와 pre-commit hook 은 이 경우를 잡지 못했다.
`secret-scan.sh` 의 설정 파일 규칙이 값 첫 글자가 `$` 면 환경변수 참조로 보고 통째로
건너뛰기 때문에, 환경변수처럼 보이지만 기본값에 실제 비밀번호가 박힌 형태가 빠져나갔다.

`${VAR:기본값}` 의 기본값 자리를 검사하는 규칙을 추가했다.

- 잡는 것: `password: ${MYSQL_PASSWORD:실제값}` (유출됐던 형태)
- 통과: `${MYSQL_PASSWORD}` (기본값 없음), `${MYSQL_HOST:localhost}` /
  `${MYSQL_PORT:3306}` / `${MYSQL_USER:sorry}` 등 비밀번호 계열이 아닌 키
- 추적 파일 139개 전체 오탐 0건 확인

## 남은 사항

- **replication 미검증.** 테스트는 master 단독으로 통과한다. replica 연동은 이번 범위 밖.
- **HTTP 계층 테스트 부재.** 컨트롤러/직렬화를 커버하는 테스트가 없어 Jackson 3 검증을
  수동 기동으로 대체했다. MockMvc 기반 테스트 추가를 권장한다.
- **`lower_case_table_names` 재초기화 필요.** 기존 볼륨을 가진 환경은 `down -v` 가 필요하다.
- **`spring-data-commons` 명시적 의존성** (`build.gradle`, 페이지네이션 용도) 은 그대로 두었다.
  Boot 4 BOM 에서 정상 해석되나, 스타터 경유로 정리할 여지가 있다.
