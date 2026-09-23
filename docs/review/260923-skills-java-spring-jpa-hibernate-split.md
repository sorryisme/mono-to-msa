# 리뷰: skills 문서 고도화 (java-spring-jpa → java-spring + jpa-hibernate 분리)

- 대상: `main` 작업 트리의 미커밋 변경 (기준 커밋 `5f1ff09`)
  - 삭제: `.claude/skills/java-spring-jpa/SKILL.md`
  - 신규: `.claude/skills/java-spring/SKILL.md`, `.claude/skills/java-spring/references/good-bad-examples.md`
  - 신규: `.claude/skills/jpa-hibernate/SKILL.md`, `.claude/skills/jpa-hibernate/references/good-bad-examples.md`
  - 수정: `.claude/agents/java-spring-reviewer.md`, `CLAUDE.md`
- 수행 주체: Claude Code (메인 세션). 리뷰 후 사용자 요청으로 전 항목 수정
- 대조 근거: 삭제된 원본 스킬 전문, `application.yml`(`open-in-view: false`), `ReplicationRoutingDataSource`, `docs/ARCHITECTURE.md`, 로컬 Gradle 캐시의 `hibernate-core` 7.x

## 결론

분리 구조는 적절하다. 원본 204줄의 규칙이 두 스킬로 누락 없이 옮겨졌고(항목 단위 대조), 상호 참조·에이전트·CLAUDE.md 링크 경로도 모두 유효하다. Good/Bad 예시 18건의 코드와 설명에서 기술적 오류는 발견하지 못했다. 아래는 개선 제안이며 병합을 막는 결함은 없다.

## 발견 사항

### [P2] readOnly 를 "최적화 힌트"로만 설명 — 이 저장소에서는 데이터소스 라우팅 스위치
- 위치: `.claude/skills/java-spring/SKILL.md:79`
- 발생 조건: 에이전트를 거치지 않고 `java-spring` 스킬만 로드한 리뷰·구현 (에이전트는 ARCHITECTURE.md 를 함께 읽어 완화됨)
- 영향과 근거: `ReplicationRoutingDataSource.java:11` 이 `isCurrentTransactionReadOnly()` 로 master/replica 를 고른다(`docs/ARCHITECTURE.md:30-33`). readOnly 누락·오부착은 "최적화 차이"가 아니라 replica 쓰기 실패나 복제 지연에 따른 read-after-write 불일치로 이어진다. 본문은 이 점을 다루지 않고 예시 7(`references/good-bad-examples.md:177`) 끝에 한 문장만 있다.
- 수정 방향: 79행 뒤에 "readOnly 로 데이터소스를 라우팅하는 저장소(이 저장소 포함, `docs/ARCHITECTURE.md`)에서는 readOnly 가 대상 DB 를 바꾸므로 쓰기 경로·쓰기 직후 조회의 정합성을 함께 확인한다" 한 줄 추가.

### [P3] Hibernate 6.6 문서 링크·"Hibernate 6 계열" 표현 — 프로젝트는 Hibernate 7.x
- 위치: `jpa-hibernate/SKILL.md:42,47,61,75`, `jpa-hibernate/references/good-bad-examples.md:164,184,249`
- 근거: Spring Boot 4.1.1 → Hibernate ORM 7.x (Gradle 캐시 7.2.1 / 7.4.5). 링크된 동작 자체는 7.x 에서도 유지되지만, "버전에 맞춰 확인하라"는 문서가 다른 메이저 버전을 가리키는 것은 혼동 여지가 있다.
- 수정 방향: 링크를 7.x 가이드로 교체하거나, `org.hibernate.orm.jdbc.bind` 설명을 "Hibernate 6 이상"으로 바꾼다.

### [P3] Good/Bad 예시 안내 문장이 특정 절 아래에 들어가 있음
- 위치: `java-spring/SKILL.md:45` ("Java 및 프로젝트 구조" 절), `jpa-hibernate/SKILL.md:18` ("영속성 컨텍스트와 트랜잭션" 절)
- 영향: 문서 전체 예시(트랜잭션·페이징·로깅 / N+1·벌크·Lombok 등)를 안내하는 문장인데 한 절의 첫 줄에 있어, 해당 절을 건너뛰면 예시 존재를 놓친다.
- 수정 방향: 각 문서의 "JPA 작업 연결" / "적용 범위와 공통 절차" 절 끝이나 별도 "참고 예시" 절로 이동.

### [P3] 교차 참조가 절 이름 없이 모호함
- 위치: `jpa-hibernate/SKILL.md:59` ("공통 스킬의 실제 DB 검증 기준")
- 수정 방향: `[java-spring](../java-spring/SKILL.md)의 "로깅과 검증" 절` 처럼 명시 (같은 파일 54행은 이미 절 이름을 명시하고 있어 형식도 통일된다).

### [P3] 사용 예시의 `$스킬명` 호출 표기
- 위치: `java-spring/SKILL.md:171,177`, `jpa-hibernate/SKILL.md:66,71`
- 근거: `$name` 은 Codex 방식이며 Claude Code 에서는 `/java-spring` 으로 호출한다(원본에서 이어진 표기).
- 수정 방향: `/java-spring`, `/jpa-hibernate` 로 변경하거나 호출 표기 없이 요청 문장만 남긴다.

## 확인했으나 문제 없음

- 원본 → 분리본 규칙 이전: 영속 상태 변경 감지, 1차 캐시, `@DataJpaTest` DB 대체, SQL/바인딩 로그, LOB 옵션은 `jpa-hibernate` 로, 트랜잭션·페이징·인덱스·풀·마이그레이션은 `java-spring` 으로 누락 없이 이동.
- 순환 참조: 양쪽이 서로를 읽으라고 하지만 "같은 작업에서 이미 읽었다면 다시 읽지 않는다"로 종료 조건이 있음.
- 링크: `.claude/agents/java-spring-reviewer.md` 의 `../skills/...`, CLAUDE.md 의 저장소 루트 기준 링크, 스킬 간 `../` 링크 모두 실제 파일로 해석됨.
- 예시 정확성: 자기 호출 프록시(4), 복합 커서 괄호(5), OSIV off 준영속 변경(JPA 1), 컬렉션 fetch join + limit 분리(JPA 4), `@Modifying` flush/clear 옵션(JPA 5), 팀 이동 편의 메서드와 `removeMember` 의 일관성(JPA 2·10) 모두 서술과 코드가 일치. 이 저장소의 `open-in-view: false` 전제와도 맞음.
- 잔존 참조: `java-spring-jpa` 는 과거 리뷰 기록(`docs/review/260918-actuator-micrometer-metrics.md`)에만 남아 있으며 이력이므로 수정 대상 아님.

## 커밋 시 참고

- 현재 `main` 에서 작업 중이다. 커밋 전에 브랜치(예: `docs/skills-split-java-spring-jpa`)를 만든다.
- 삭제와 신규 파일을 함께 `git add` 해야 한다(신규 디렉터리는 아직 untracked).

## 조치 결과

| 항목 | 조치 |
|---|---|
| P2 readOnly 라우팅 | 수정함 |
| P3 Hibernate 7 링크 | 수정함 |
| P3 예시 안내 위치 | 수정함 |
| P3 교차 참조 명시 | 수정함 |
| P3 `$` 호출 표기 | 수정함 |
