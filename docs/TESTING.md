# 테스트 관례

[CLAUDE.md](../CLAUDE.md)에서 이관된 문서입니다.

- 테스트는 모두 JUnit 5 이고 `src/test/java` 한 곳에 있습니다. Mockito·AssertJ·JUnit 은 `spring-boot-starter-test` 가 가져오므로 테스트 의존성을 따로 선언하지 않습니다.
- 테스트 메서드 이름은 lowerCamelCase 로 쓰고, 의도는 `@DisplayName` 에 한국어 문장으로 남깁니다. 데이터 조합이 여러 개면 `@ParameterizedTest` + `@MethodSource` 를 씁니다.
- 웹 계층 계약(응답 봉투, 오류 코드 → HTTP 상태, 생성 201, 로그인 401)은 `@WebMvcTest`(`exception/GlobalExceptionHandlerTest`, `controller/ControllerResponseContractTest`)가 고정합니다. 서비스는 `@MockitoBean` 으로 대체하고, `SessionManager` 에 의존하는 `WebConfig`/`LoginUserIdResolver` 는 필요 시 `excludeFilters` 로 제외합니다. `ErrorCode` 를 추가하면 [API_RESPONSE.md](API_RESPONSE.md) 표와 이 테스트를 함께 갱신합니다.
- 서비스 테스트는 예외를 `assertThatThrownBy(...).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.XXX)` 로 단언합니다.
- 서비스 테스트는 리포지토리 의존성을 필드에서 `Mockito.mock()` 으로 직접 만들고 Spring 컨텍스트를 띄우지 않습니다. `@ExtendWith(MockitoExtension)` 을 쓰지 않는 이유는 strict stubbing 때문입니다 — 한 테스트에서 분기마다 다르게 쓰이는 스텁이 `UnnecessaryStubbingException` 으로 터지지 않게 느슨한 스텁을 유지합니다. `Optional` 을 돌려주는 메서드는 반드시 명시적으로 stub 하세요 (미지정 시 `Optional.empty()` 가 아니라 null 이 올 수 있습니다).
- 호출 순서가 계약인 곳은 `InOrder` 로 고정합니다 — `OrderServiceTest` 의 "옵션 ID 오름차순 재고 차감" 은 데드락 방지 규칙이므로 순서 검증을 지우지 마세요.
- 리포지토리 테스트는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`으로 `src/test/resources/application.yml`에 설정된 실제 MySQL을 사용합니다. 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백됩니다. `schema.sql`/`data.sql` 이 컨텍스트마다 다시 적용되므로 `data.sql` 의 시드(user 1, product 1, product_option 1·2, 카테고리 8종)를 전제로 써도 됩니다.
- `entity/EntityMappingTest` 는 `spring.jpa.hibernate.ddl-auto=validate` 로 컨텍스트를 띄워 모든 엔티티 매핑이 `schema.sql` 과 일치하는지 검증합니다. 엔티티나 스키마를 바꿨다면 이 테스트가 먼저 깨집니다.
- `testUtils/DomainFixture.java`가 엔티티 픽스처(`User`, `Cart`, `Order` 등) 생성을 한곳에 모아둡니다 — 새 테스트에서 엔티티를 직접 만들지 말고 이걸 재사용하세요. 단위 테스트에서 id 가 필요하면 id 를 받는 오버로드를 씁니다.
