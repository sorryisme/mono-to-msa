package com.sorryisme.fmarket.entity

import com.sorryisme.fmarket.enums.OrderStatus
import com.sorryisme.fmarket.enums.UserRole
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.math.BigDecimal

/**
 * 엔티티 매핑이 실제 MySQL 스키마(schema.sql)와 맞는지 확인한다.
 * ddl-auto=validate 로 컨텍스트가 뜨는 것 자체가 컬럼 단위 검증이고,
 * 아래 테스트는 식별자 생성과 부모-자식 cascade 저장까지 확인한다.
 */
@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class EntityMappingTest extends Specification {

    @Autowired
    private EntityManager em

    def "유저를 저장하면 식별자가 생성되고 role 이 문자열로 저장된다"() {
        given:
        UserEntity user = UserEntity.builder()
                .loginId("jpa-user-1")
                .password("hashed")
                .salt("salt")
                .name("홍길동")
                .email("jpa-user-1@test.com")
                .role(UserRole.USER)
                .phoneNumber("010-1234-5678")
                .build()
        when:
        em.persist(user)
        em.flush()
        em.clear()
        UserEntity found = em.find(UserEntity, user.getId())
        then:
        user.getId() != null
        found.getLoginId() == "jpa-user-1"
        found.getRole() == UserRole.USER
        found.getCreatedAt() != null
    }

    def "주문을 저장하면 주문 상세까지 함께 저장된다"() {
        given:
        UserEntity user = UserEntity.builder()
                .loginId("jpa-user-2")
                .password("hashed")
                .salt("salt")
                .name("김철수")
                .email("jpa-user-2@test.com")
                .role(UserRole.USER)
                .build()
        em.persist(user)

        ProductEntity product = ProductEntity.builder()
                .productName("티셔츠")
                .description("설명")
                .build()
        em.persist(product)

        ProductOptionEntity option = ProductOptionEntity.builder()
                .productId(product.getId())
                .optionName("L")
                .originPrice(new BigDecimal("10000.00"))
                .salePrice(new BigDecimal("9000.00"))
                .build()
        em.persist(option)

        OrderEntity order = OrderEntity.builder()
                .userId(user.getId())
                .totalAmount(new BigDecimal("18000.00"))
                .orderDetails([
                        OrderDetailEntity.builder()
                                .productOptionId(option.getId())
                                .quantity(2)
                                .price(new BigDecimal("9000.00"))
                                .build()
                ])
                .build()
        when:
        em.persist(order)
        em.flush()
        em.clear()
        OrderEntity found = em.find(OrderEntity, order.getId())
        then:
        found.getStatus() == OrderStatus.PENDING
        found.getOrderDate() != null
        found.getOrderDetails().size() == 1
        found.getOrderDetails().get(0).getQuantity() == 2
        found.getOrderDetails().get(0).getOrder().getId() == order.getId()
    }

    def "장바구니 상세를 제거하면 orphanRemoval 로 함께 삭제된다"() {
        given:
        UserEntity user = UserEntity.builder()
                .loginId("jpa-user-3")
                .password("hashed")
                .salt("salt")
                .name("이영희")
                .email("jpa-user-3@test.com")
                .role(UserRole.USER)
                .build()
        em.persist(user)

        ProductEntity product = ProductEntity.builder().productName("바지").description("설명").build()
        em.persist(product)
        ProductOptionEntity option = ProductOptionEntity.builder()
                .productId(product.getId())
                .optionName("M")
                .originPrice(new BigDecimal("5000.00"))
                .salePrice(new BigDecimal("4500.00"))
                .build()
        em.persist(option)

        CartEntity cart = CartEntity.of(user.getId())
        cart.addCartDetail(CartDetailEntity.builder().productOptionId(option.getId()).quantity(1).build())
        em.persist(cart)
        em.flush()
        when:
        cart.removeCartDetail(cart.getCartDetails().get(0))
        em.flush()
        em.clear()
        then:
        em.find(CartEntity, cart.getId()).getCartDetails().isEmpty()
    }
}
