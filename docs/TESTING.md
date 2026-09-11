# 테스트 관례

[CLAUDE.md](../CLAUDE.md)에서 이관된 문서입니다.

- Spock(Groovy) 스펙은 `src/test/groovy`에 있으며, 순수 JUnit 테스트는 스모크 테스트(`FmarketApplicationTests`) 하나뿐입니다.
- 서비스 테스트는 매퍼 의존성을 `Mock()`으로 직접 대체하며 Spring 컨텍스트를 띄우지 않습니다.
- 매퍼 테스트는 `@MybatisTest` + `@AutoConfigureTestDatabase(replace = NONE)`으로 `src/test/resources/application.yml`에 설정된 실제 MySQL을 사용합니다.
- `testUtils/DomainFixture.java`가 도메인 픽스처(`User`, `Cart`, `CartDetail` 등) 생성을 한곳에 모아둡니다 — 새 테스트에서 도메인 객체를 직접 만들지 말고 이걸 재사용하세요.
