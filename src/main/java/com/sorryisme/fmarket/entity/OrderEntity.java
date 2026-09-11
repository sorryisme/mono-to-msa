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
public class OrderEntity extends BaseTimeEntity {

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
  private List<OrderDetailEntity> orderDetails = new ArrayList<>();

  @Builder
  private OrderEntity(
      Long id,
      Long userId,
      LocalDateTime orderDate,
      OrderStatus status,
      BigDecimal totalAmount,
      List<OrderDetailEntity> orderDetails) {
    this.id = id;
    this.userId = userId;
    this.orderDate = orderDate == null ? LocalDateTime.now() : orderDate;
    this.status = status == null ? OrderStatus.PENDING : status;
    this.totalAmount = totalAmount;
    if (orderDetails != null) {
      orderDetails.forEach(this::addOrderDetail);
    }
  }

  public List<OrderDetailEntity> getOrderDetails() {
    return Collections.unmodifiableList(orderDetails);
  }

  /** 연관관계 편의 메서드. 양쪽 메모리 상태를 함께 맞춘다. */
  public void addOrderDetail(OrderDetailEntity orderDetail) {
    orderDetails.add(orderDetail);
    orderDetail.assignOrder(this);
  }

  public void changeStatus(OrderStatus status) {
    this.status = status;
  }
}
