package com.sorryisme.fmarket.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OrderDetail {

  private Long id;
  private Long orderId;
  private Long productOptionId;
  private Integer quantity;
  private BigDecimal price;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
