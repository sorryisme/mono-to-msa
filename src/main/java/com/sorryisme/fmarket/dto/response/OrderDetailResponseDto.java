package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.OrderDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDetailResponseDto {

  private Long id;
  private Long productOptionId;
  private Integer quantity;
  private BigDecimal price;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static OrderDetailResponseDto from(OrderDetail orderDetail) {
    return OrderDetailResponseDto.builder()
        .id(orderDetail.getId())
        .productOptionId(orderDetail.getProductOptionId())
        .quantity(orderDetail.getQuantity())
        .price(orderDetail.getPrice())
        .createdAt(orderDetail.getCreatedAt())
        .updatedAt(orderDetail.getUpdatedAt())
        .build();
  }
}
