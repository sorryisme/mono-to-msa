package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.entity.Cart;
import com.sorryisme.fmarket.entity.Store;
import com.sorryisme.fmarket.entity.User;
import com.sorryisme.fmarket.enums.UserRole;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private CartRepository cartRepository;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("유저를 저장하면 id 가 생성되고 role 은 기본값 USER 가 된다")
  void savesUserWithDefaultRole() {
    User saved = userRepository.save(DomainFixture.createUser());
    em.flush();
    em.clear();
    User found = userRepository.findById(saved.getId()).orElseThrow();

    assertThat(saved.getId()).isNotNull();
    assertThat(found.getRole()).isEqualTo(UserRole.USER);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("이름과 전화번호가 같은 유저가 있으면 true 를 돌려준다")
  void checksExistenceByNameAndPhoneNumber() {
    userRepository.save(DomainFixture.createUser());

    assertThat(userRepository.existsByNameAndPhoneNumber("테스트유저", "01012345678")).isTrue();
    assertThat(userRepository.existsByNameAndPhoneNumber("테스트유저", "00000000000")).isFalse();
  }

  @Test
  @DisplayName("로그인 아이디로 유저를 조회한다")
  void findsByLoginId() {
    userRepository.save(DomainFixture.createUser());

    User found = userRepository.findByLoginId("testUser").orElseThrow();

    assertThat(found.getSalt()).isEqualTo("testSalt");
    assertThat(found.getPassword()).isEqualTo("xptmxmqlalfqjsgh!");
    assertThat(userRepository.findByLoginId("no-such-user")).isEmpty();
  }

  @Test
  @DisplayName("유저 정보를 바꾸면 변경 감지로 반영된다")
  void updatesUserByDirtyChecking() {
    User saved = userRepository.save(DomainFixture.createUser());

    saved.updateProfile("업데이트된 이름", "updated_email@test.com", "01199999999");
    em.flush();
    em.clear();
    User found = userRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getName()).isEqualTo("업데이트된 이름");
    assertThat(found.getEmail()).isEqualTo("updated_email@test.com");
    assertThat(found.getPhoneNumber()).isEqualTo("01199999999");
  }

  @Test
  @DisplayName("상점을 저장하면 id 가 생성된다")
  void savesStore() {
    User user = userRepository.save(DomainFixture.createUser());

    Store store =
        storeRepository.save(
            Store.builder()
                .storeName("테스트 상점")
                .logoUrl("https://example.com/test.jpg")
                .description("테스트 상점 설명")
                .businessNumber("123-89-12345")
                .userId(user.getId())
                .build());

    assertThat(store.getId()).isNotNull();
  }

  @Test
  @DisplayName("유저 id 로 장바구니를 조회한다")
  void findsCartByUserId() {
    User user = userRepository.save(DomainFixture.createUser());
    Cart cart = cartRepository.save(Cart.of(user.getId()));

    assertThat(cartRepository.findByUserId(user.getId()).orElseThrow().getId())
        .isEqualTo(cart.getId());
    assertThat(cartRepository.findByUserId(-1L)).isEmpty();
  }
}
