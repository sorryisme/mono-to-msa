package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.entity.Cart
import com.sorryisme.fmarket.entity.Store
import com.sorryisme.fmarket.entity.User
import com.sorryisme.fmarket.enums.UserRole
import com.sorryisme.fmarket.testUtils.DomainFixture
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends Specification {

    @Autowired
    UserRepository userRepository
    @Autowired
    StoreRepository storeRepository
    @Autowired
    CartRepository cartRepository
    @Autowired
    EntityManager em

    def "유저를 저장하면 id 가 생성되고 role 은 기본값 USER 가 된다"() {
        when:
        User saved = userRepository.save(DomainFixture.createUser())
        em.flush()
        em.clear()
        User found = userRepository.findById(saved.getId()).orElseThrow()

        then:
        saved.getId() != null
        found.getRole() == UserRole.USER
        found.getCreatedAt() != null
    }

    def "이름과 전화번호가 같은 유저가 있으면 true 를 돌려준다"() {
        given:
        userRepository.save(DomainFixture.createUser())

        expect:
        userRepository.existsByNameAndPhoneNumber("테스트유저", "01012345678")
        !userRepository.existsByNameAndPhoneNumber("테스트유저", "00000000000")
    }

    def "로그인 아이디로 유저를 조회한다"() {
        given:
        userRepository.save(DomainFixture.createUser())

        when:
        User found = userRepository.findByLoginId("testUser").orElseThrow()

        then:
        found.getSalt() == "testSalt"
        found.getPassword() == "xptmxmqlalfqjsgh!"
        userRepository.findByLoginId("no-such-user").isEmpty()
    }

    def "유저 정보를 바꾸면 변경 감지로 반영된다"() {
        given:
        User saved = userRepository.save(DomainFixture.createUser())

        when:
        saved.updateProfile("업데이트된 이름", "updated_email@test.com", "01199999999")
        em.flush()
        em.clear()
        User found = userRepository.findById(saved.getId()).orElseThrow()

        then:
        found.getName() == "업데이트된 이름"
        found.getEmail() == "updated_email@test.com"
        found.getPhoneNumber() == "01199999999"
    }

    def "상점을 저장하면 id 가 생성된다"() {
        given:
        User user = userRepository.save(DomainFixture.createUser())

        when:
        Store store = storeRepository.save(Store.builder()
                .storeName("테스트 상점")
                .logoUrl("https://naver.com/test.jpg")
                .description("테스트 상점 설명")
                .businessNumber("123-89-12345")
                .userId(user.getId())
                .build())

        then:
        store.getId() != null
    }

    def "유저 id 로 장바구니를 조회한다"() {
        given:
        User user = userRepository.save(DomainFixture.createUser())
        Cart cart = cartRepository.save(Cart.of(user.getId()))

        expect:
        cartRepository.findByUserId(user.getId()).orElseThrow().getId() == cart.getId()
        cartRepository.findByUserId(-1L).isEmpty()
    }
}
