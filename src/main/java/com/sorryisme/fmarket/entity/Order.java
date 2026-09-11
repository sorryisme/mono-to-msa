package com.sorryisme.fmarket.entity;

import com.sorryisme.fmarket.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order 테이블. MySQL 예약어라 테이블명을 인용한다.
 *
 * <p>주문 상세는 주문이 생명주기를 소유하므로 cascade + orphanRemoval 로 묶는다. 주문자(user)는 생명주기가 독립적이라 FK 값으로만 들고 있다.
 */
@Entity
@Table(name = "\"order\"")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "order_date")
  private LocalDateTime orderDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OrderStatus status;

  @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalAmount;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderDetail> orderDetails = new ArrayList<>();

  @Builder
  private Order(
      Long id,
      Long userId,
      LocalDateTime orderDate,
      OrderStatus status,
      BigDecimal totalAmount,
      List<OrderDetail> orderDetails) {
    this.id = id;
    this.userId = userId;
    this.orderDate = orderDate == null ? LocalDateTime.now() : orderDate;
    this.status = status == null ? OrderStatus.PENDING : status;
    this.totalAmount = totalAmount;
    if (orderDetails != null) {
      orderDetails.forEach(this::addOrderDetail);
    }
  }

  /**
   * 주문 옵션과 요청 수량으로 PENDING 주문을 만든다. 총액은 옵션 판매가 x 수량의 합이다.
   *
   * @param productOptions 실제 존재하는 상품 옵션
   * @param requestQuantityMap productOptionId -> 요청 수량
   */
  public static Order of(
      List<ProductOption> productOptions, Map<Long, Integer> requestQuantityMap, Long userId) {
    List<OrderDetail> details =
        productOptions.stream()
            .map(
                option ->
                    OrderDetail.builder()
                        .productOptionId(option.getId())
                        .price(option.getSalePrice())
                        .quantity(requestQuantityMap.getOrDefault(option.getId(), 0))
                        .build())
            .toList();

    BigDecimal totalAmount =
        details.stream()
            .map(detail -> detail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return Order.builder()
        .userId(userId)
        .status(OrderStatus.PENDING)
        .totalAmount(totalAmount)
        .orderDetails(details)
        .build();
  }

  public List<OrderDetail> getOrderDetails() {
    return Collections.unmodifiableList(orderDetails);
  }

  /** 연관관계 편의 메서드. 양쪽 메모리 상태를 함께 맞춘다. */
  public void addOrderDetail(OrderDetail orderDetail) {
    orderDetails.add(orderDetail);
    orderDetail.assignOrder(this);
  }

  public void changeStatus(OrderStatus status) {
    this.status = status;
  }
}
