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
public class ProductOption {

  private Long id;
  private Long productId;
  private String optionName;
  private BigDecimal originPrice;
  private BigDecimal salePrice;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
