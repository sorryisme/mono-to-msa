package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sorryisme.fmarket.entity.IdempotencyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyKeyRepositoryTest {

  private static final String UUID = "166f9067-2e5f-4932-e314-f438ae846d24";

  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;

  @Test
  @DisplayName("같은 UUID 를 두 번 저장하면 두 번째는 유니크 제약으로 실패한다")
  void rejectsDuplicateKey() {
    idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(UUID));

    assertThatThrownBy(() -> idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(UUID)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
