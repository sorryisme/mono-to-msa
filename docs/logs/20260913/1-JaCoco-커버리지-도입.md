# JaCoco 코드 커버리지 도입

- 작업일: 2026-09-13
- 브랜치: `chore/jacoco-coverage`
- 범위: 빌드 설정(`build.gradle`), CI 워크플로(`.github/workflows/verify.yml`)

## 배경

테스트가 어느 영역을 실제로 덮고 있는지 수치로 볼 수단이 없었다. `refactor/spock-to-junit`
전환 직후라 테스트 구성이 바뀐 시점이기도 해서, 커버리지 측정 기준을 지금 만들어 두면
이후 테스트 보강의 효과를 추적할 수 있다.

## 결정 사항

**리포트만 도입하고 임계값 게이트는 걸지 않는다.** 현재 수치를 모르는 상태에서 임계값을
먼저 정하면 근거 없는 숫자가 되거나 빌드를 바로 깨뜨린다. 먼저 측정하고, 그 값을 기준선
삼아 나중에 게이트를 건다(→ `docs/todo/BACKLOG.md`).

**로직이 없는 코드는 집계에서 제외한다.** `entity` / `dto` / `enums` / `config` /
`annotation` / `FmarketApplication` 이 대상이다. Lombok 생성 코드와 단순 데이터 홀더가
포함되면 수치가 왜곡돼 "실제로 테스트된 로직의 비율"이라는 의미를 잃는다.

## 변경 내용

### build.gradle

- `plugins` 에 `id 'jacoco'` 추가, `jacoco { toolVersion = '0.8.13' }`
- `test` 태스크에 `finalizedBy jacocoTestReport` — 테스트를 돌리면 리포트가 함께 생성된다.
  별도 태스크를 기억할 필요가 없고, 테스트가 실패해도 finalizer 는 실행되므로 실패 시점의
  커버리지도 남는다.
- `jacocoTestReport` 에 `dependsOn test`, HTML·XML 리포트 활성화(CSV 는 끔).
  XML 은 지금 쓰지 않지만 Codecov·SonarQube 같은 수집 도구를 붙일 때 필요한 형식이다.
- `classDirectories` 에 위 제외 패턴 적용
- `jacocoTestCoverageVerification` 은 **`check` 에 연결하지 않음**

기존 검증 흐름(`scripts/verify-full.sh` 의 `./gradlew clean check`)은 손대지 않았다.
`check` → `test` → `jacocoTestReport` 로 이어지므로 스크립트 변경 없이 CI 에서도 리포트가 생긴다.

### .github/workflows/verify.yml

기존 "테스트 리포트 업로드" 스텝 뒤에 `coverage-report` 아티팩트 업로드 스텝 추가
(`build/reports/jacoco/test/html`, `if: always()`, `if-no-files-found: ignore`).

## 검증

MySQL(master/replica)을 Compose 로 띄운 뒤 전체 테스트를 실행했다.

```
docker compose up -d db-master db-replica
./gradlew clean test
→ BUILD SUCCESSFUL (Task :test, Task :jacocoTestReport 실행됨)
./gradlew spotlessCheck
→ BUILD SUCCESSFUL
```

- `build/reports/jacoco/test/html/index.html`, `jacocoTestReport.xml` 생성 확인
- XML 의 `<package>` 목록에 `entity` / `dto` / `enums` / `config` / `annotation` 이
  없는 것으로 제외 패턴 동작 확인

### 기준선 (전체 테스트, 제외 패턴 적용)

| 지표 | 커버 / 전체 | 비율 |
|---|---|---|
| INSTRUCTION | 1192 / 1439 | 82.8% |
| LINE | 253 / 314 | 80.6% |
| BRANCH | 49 / 68 | 72.1% |
| METHOD | 61 / 81 | 75.3% |
| CLASS | 15 / 20 | 75.0% |

패키지별 라인 커버리지:

| 패키지 | 라인 커버리지 |
|---|---|
| `service` | 100% |
| `repository` | 100% |
| `resolver` | 100% |
| `filter` | 100% |
| `exception` | 97.1% |
| `common` | 80.0% |
| `utils` | 50.0% |
| `controller` | 17.2% |
| `aop` | 0.0% |

`aop`(AuthenticationAspect, IdempotencyAspect)가 0% 다. 인증 통과 여부와 멱등성 키
처리는 잘못되면 보안·중복 결제로 이어지는 지점인데 직접 검증하는 테스트가 없다.
`controller` 17.2% 는 `ControllerResponseContractTest` 가 응답 봉투 계약만 확인하고
핸들러 본문을 거의 타지 않기 때문이다.

## 남긴 항목

- `aop` 패키지 테스트 보강 → `docs/todo/BACKLOG.md`
- 기준선(LINE 80%, BRANCH 72%)을 근거로 `jacocoTestCoverageVerification` 도입
  → `docs/todo/BACKLOG.md`
