package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 목록 한 건. 상세 항목은 포함하지 않는다(단건 조회에서 제공). */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderListResponseDto {

  private Long id;
  private Long userId;
  private String status;
  private LocalDateTime orderDate;
  private BigDecimal totalAmount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static OrderListResponseDto from(Order order) {
    return OrderListResponseDto.builder()
        .id(order.getId())
        .userId(order.getUserId())
        .status(order.getStatus().name())
        .orderDate(order.getOrderDate())
        .totalAmount(order.getTotalAmount())
        .createdAt(order.getCreatedAt())
        .updatedAt(order.getUpdatedAt())
        .build();
  }
}
