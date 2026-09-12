package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 실제 MySQL(schema.sql + data.sql) 위에서 주문 리포지토리의 커스텀 쿼리를 검증한다. data.sql 의 user 1, product_option 1 을
 * 사용한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

  @Autowired private OrderRepository orderRepository;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("기간 조회는 from 이상 to 미만의 주문만 돌려준다")
  void filtersByHalfOpenPeriod() {
    Order inRange = persistOrder(LocalDateTime.of(2030, 6, 15, 12, 0));
    Order onTo = persistOrder(LocalDateTime.of(2030, 6, 16, 0, 0));
    Order before = persistOrder(LocalDateTime.of(2030, 6, 14, 23, 59));
    em.flush();

    Page<Order> page =
        orderRepository.findByUserIdAndOrderDateIn(
            1L,
            LocalDateTime.of(2030, 6, 15, 0, 0),
            LocalDateTime.of(2030, 6, 16, 0, 0),
            Pageable.ofSize(10));

    assertThat(page.getContent())
        .extracting(Order::getId)
        .contains(inRange.getId())
        .doesNotContain(onTo.getId(), before.getId());
  }

  @Test
  @DisplayName("사용자 기준 페이징 조회는 총 개수를 함께 돌려준다")
  void returnsTotalCountWithPage() {
    for (int i = 0; i < 3; i++) {
      persistOrder(LocalDateTime.now());
    }
    em.flush();

    Page<Order> page = orderRepository.findByUserId(1L, Pageable.ofSize(2));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("상세 포함 조회는 주문 상세까지 한 번에 가져온다")
  void fetchesOrderDetailsTogether() {
    Order saved = persistOrder(LocalDateTime.now());
    em.flush();
    em.clear();

    Order found = orderRepository.findWithDetailsById(saved.getId()).orElseThrow();

    assertThat(found.getOrderDetails()).hasSize(1);
    assertThat(found.getOrderDetails().get(0).getProductOptionId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("조건부 상태 전이는 현재 상태가 일치할 때만 1행을 갱신한다")
  void transitionsStatusWhenCurrentMatches() {
    Order saved = persistOrder(LocalDateTime.now());
    em.flush();
    em.clear();

    int affected =
        orderRepository.updateStatusIfCurrent(
            saved.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);
    em.clear();

    assertThat(affected).isEqualTo(1);
    assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  @DisplayName("이미 상태가 바뀐 주문에 같은 전이를 다시 시도하면 0행이 갱신된다")
  void secondTransitionAffectsNoRow() {
    Order saved = persistOrder(LocalDateTime.now());
    em.flush();
    em.clear();
    orderRepository.updateStatusIfCurrent(
        saved.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);
    em.clear();

    int second =
        orderRepository.updateStatusIfCurrent(
            saved.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);

    assertThat(second).isZero();
  }

  @Test
  @DisplayName("없는 주문 ID 로 상태 전이를 시도하면 0행이 갱신된다")
  void transitionOnMissingOrderAffectsNoRow() {
    assertThat(
            orderRepository.updateStatusIfCurrent(
                999999L, OrderStatus.PENDING, OrderStatus.CANCELLED))
        .isZero();
  }

  private Order persistOrder(LocalDateTime orderDate) {
    Order order =
        Order.builder()
            .userId(1L)
            .orderDate(orderDate)
            .totalAmount(new BigDecimal("500.00"))
            .orderDetails(List.of(DomainFixture.createOrderDetail(null, 1L, 5)))
            .build();
    return orderRepository.save(order);
  }
}
