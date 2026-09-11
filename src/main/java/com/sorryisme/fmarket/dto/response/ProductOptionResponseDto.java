package com.sorryisme.fmarket.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductOptionResponseDto {

  private Long id;
  private String optionName;
  private BigDecimal originPrice;
  private BigDecimal salePrice;
}
