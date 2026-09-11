package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.entity.IdempotencyKey
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyKeyRepositoryTest extends Specification {

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository

    private static final String UUID = "166f9067-2e5f-4932-e314-f438ae846d24"

    def "같은 UUID 를 두 번 저장하면 두 번째는 유니크 제약으로 실패한다"() {
        when:
        idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(UUID))
        idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(UUID))

        then:
        thrown(DataIntegrityViolationException)
    }
}
