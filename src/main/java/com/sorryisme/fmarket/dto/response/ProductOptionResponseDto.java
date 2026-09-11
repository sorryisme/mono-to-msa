package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.ProductOption;
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

  public static ProductOptionResponseDto from(ProductOption option) {
    return ProductOptionResponseDto.builder()
        .id(option.getId())
        .optionName(option.getOptionName())
        .originPrice(option.getOriginPrice())
        .salePrice(option.getSalePrice())
        .build();
  }
}
