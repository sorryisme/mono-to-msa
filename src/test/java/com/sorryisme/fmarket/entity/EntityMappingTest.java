package com.sorryisme.fmarket.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.enums.UserRole;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * 엔티티 매핑이 실제 MySQL 스키마(schema.sql)와 맞는지 확인한다. ddl-auto=validate 로 컨텍스트가 뜨는 것 자체가 컬럼 단위 검증이고, 아래
 * 테스트는 식별자 생성과 부모-자식 cascade 저장까지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class EntityMappingTest {

  @Autowired private EntityManager em;

  @Test
  @DisplayName("유저를 저장하면 식별자가 생성되고 role 이 문자열로 저장된다")
  void persistsUserWithGeneratedId() {
    User user =
        User.builder()
            .loginId("jpa-user-1")
            .password("hashed")
            .salt("salt")
            .name("홍길동")
            .email("jpa-user-1@test.com")
            .role(UserRole.USER)
            .phoneNumber("010-1234-5678")
            .build();

    em.persist(user);
    em.flush();
    em.clear();
    User found = em.find(User.class, user.getId());

    assertThat(user.getId()).isNotNull();
    assertThat(found.getLoginId()).isEqualTo("jpa-user-1");
    assertThat(found.getRole()).isEqualTo(UserRole.USER);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("주문을 저장하면 주문 상세까지 함께 저장된다")
  void cascadesOrderDetailsOnPersist() {
    User user =
        User.builder()
            .loginId("jpa-user-2")
            .password("hashed")
            .salt("salt")
            .name("김철수")
            .email("jpa-user-2@test.com")
            .role(UserRole.USER)
            .build();
    em.persist(user);

    Product product = Product.builder().productName("티셔츠").description("설명").build();
    em.persist(product);

    ProductOption option =
        ProductOption.builder()
            .productId(product.getId())
            .optionName("L")
            .originPrice(new BigDecimal("10000.00"))
            .salePrice(new BigDecimal("9000.00"))
            .build();
    em.persist(option);

    Order order =
        Order.builder()
            .userId(user.getId())
            .totalAmount(new BigDecimal("18000.00"))
            .orderDetails(
                List.of(
                    OrderDetail.builder()
                        .productOptionId(option.getId())
                        .quantity(2)
                        .price(new BigDecimal("9000.00"))
                        .build()))
            .build();

    em.persist(order);
    em.flush();
    em.clear();
    Order found = em.find(Order.class, order.getId());

    assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(found.getOrderDate()).isNotNull();
    assertThat(found.getOrderDetails()).hasSize(1);
    assertThat(found.getOrderDetails().get(0).getQuantity()).isEqualTo(2);
    assertThat(found.getOrderDetails().get(0).getOrder().getId()).isEqualTo(order.getId());
  }

  @Test
  @DisplayName("장바구니 상세를 제거하면 orphanRemoval 로 함께 삭제된다")
  void removesCartDetailAsOrphan() {
    User user =
        User.builder()
            .loginId("jpa-user-3")
            .password("hashed")
            .salt("salt")
            .name("이영희")
            .email("jpa-user-3@test.com")
            .role(UserRole.USER)
            .build();
    em.persist(user);

    Product product = Product.builder().productName("바지").description("설명").build();
    em.persist(product);
    ProductOption option =
        ProductOption.builder()
            .productId(product.getId())
            .optionName("M")
            .originPrice(new BigDecimal("5000.00"))
            .salePrice(new BigDecimal("4500.00"))
            .build();
    em.persist(option);

    Cart cart = Cart.of(user.getId());
    cart.addCartDetail(CartDetail.builder().productOptionId(option.getId()).quantity(1).build());
    em.persist(cart);
    em.flush();

    cart.removeCartDetail(cart.getCartDetails().get(0));
    em.flush();
    em.clear();

    assertThat(em.find(Cart.class, cart.getId()).getCartDetails()).isEmpty();
  }

  @Test
  @DisplayName("상품과 옵션은 status 가 기본 ON_SALE 로 저장되고 소프트 삭제가 문자열로 반영된다")
  void defaultsStatusAndReflectsSoftDelete() {
    Product product = Product.builder().productName("모자").description("설명").build();
    em.persist(product);
    ProductOption option =
        ProductOption.builder()
            .productId(product.getId())
            .optionName("FREE")
            .originPrice(new BigDecimal("3000.00"))
            .salePrice(new BigDecimal("2500.00"))
            .build();
    em.persist(option);
    em.flush();
    em.clear();

    assertThat(em.find(Product.class, product.getId()).getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
    assertThat(em.find(ProductOption.class, option.getId()).getStatus())
        .isEqualTo(ProductStatus.ON_SALE);

    em.find(Product.class, product.getId()).delete();
    em.find(ProductOption.class, option.getId()).suspend();
    em.flush();
    em.clear();

    assertThat(em.find(Product.class, product.getId()).getStatus())
        .isEqualTo(ProductStatus.DELETED);
    assertThat(em.find(ProductOption.class, option.getId()).getStatus())
        .isEqualTo(ProductStatus.SUSPENDED);
    assertThat(
            em.createNativeQuery("select status from product where id = :id")
                .setParameter("id", product.getId())
                .getSingleResult())
        .isEqualTo("DELETED");
  }
}
