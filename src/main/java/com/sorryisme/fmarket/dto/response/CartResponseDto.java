package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.CartDetail;
import lombok.*;

@Getter
@Builder
public class CartResponseDto {
  private Long id;
  private Long cartId;
  private Long productOptionId;
  private Integer quantity;

  public static CartResponseDto from(CartDetail cartDetail) {
    return CartResponseDto.builder()
        .id(cartDetail.getId())
        .cartId(cartDetail.getCart().getId())
        .productOptionId(cartDetail.getProductOptionId())
        .quantity(cartDetail.getQuantity())
        .build();
  }
}
