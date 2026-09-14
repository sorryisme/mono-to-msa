package com.sorryisme.fmarket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DB 없이 주문 생성 규칙(총액 계산·기본 상태·연관관계)을 본다. 매핑 자체는 EntityMappingTest 가 본다. */
class OrderTest {

  @Test
  @DisplayName("주문 총액은 옵션 판매가 x 요청 수량의 합이고 상세에는 주문 시점 판매가가 복사된다")
  void calculatesTotalAmountFromSalePriceAndQuantity() {
    ProductOption option1 = DomainFixture.createProductOption(1L, 1L, "옵션1", "1500.50");
    ProductOption option2 = DomainFixture.createProductOption(2L, 1L, "옵션2", "3000");

    Order order = Order.of(List.of(option1, option2), Map.of(1L, 2, 2L, 3), 7L);

    // 1500.50 x 2 + 3000 x 3
    assertThat(order.getTotalAmount()).isEqualByComparingTo("12001.00");
    assertThat(order.getUserId()).isEqualTo(7L);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getOrderDetails())
        .extracting(
            OrderDetail::getProductOptionId, OrderDetail::getQuantity, OrderDetail::getPrice)
        .containsExactly(
            tuple(1L, 2, new BigDecimal("1500.50")), tuple(2L, 3, new BigDecimal("3000")));
  }

  @Test
  @DisplayName("주문 상세는 모두 생성된 주문을 가리킨다")
  void assignsOrderToEveryDetail() {
    ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션1", "1000");

    Order order = Order.of(List.of(option), Map.of(1L, 1), 7L);

    assertThat(order.getOrderDetails()).allSatisfy(d -> assertThat(d.getOrder()).isSameAs(order));
  }

  @Test
  @DisplayName("요청 수량에 없는 옵션은 수량 0 으로 들어가 총액에 더해지지 않는다")
  void treatsMissingQuantityAsZero() {
    ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션1", "1000");

    Order order = Order.of(List.of(option), Map.of(), 7L);

    assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(order.getOrderDetails()).extracting(OrderDetail::getQuantity).containsExactly(0);
  }

  @Test
  @DisplayName("상태와 주문일시를 주지 않으면 PENDING 과 현재 시각으로 채운다")
  void fillsDefaultStatusAndOrderDate() {
    LocalDateTime before = LocalDateTime.now();

    Order order = Order.builder().userId(1L).totalAmount(BigDecimal.ONE).build();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getOrderDate()).isAfterOrEqualTo(before);
    assertThat(order.getOrderDetails()).isEmpty();
  }

  @Test
  @DisplayName("상태와 주문일시를 주면 그대로 쓴다")
  void keepsGivenStatusAndOrderDate() {
    LocalDateTime orderDate = LocalDateTime.of(2026, 1, 1, 10, 0);

    Order order =
        Order.builder().userId(1L).status(OrderStatus.CANCELLED).orderDate(orderDate).build();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getOrderDate()).isEqualTo(orderDate);
  }
}
