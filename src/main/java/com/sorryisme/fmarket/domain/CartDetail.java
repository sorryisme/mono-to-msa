package com.sorryisme.fmarket.domain;

import com.sorryisme.fmarket.dto.request.CartRequestDto;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartDetail {
  private long id;
  private long cartId;
  private long productOptionId;
  private int quantity;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static CartDetail from(CartRequestDto cartRequestDto, long cartId) {
    return CartDetail.builder()
        .cartId(cartId)
        .productOptionId(cartRequestDto.getProductOptionId())
        .quantity(cartRequestDto.getQuantity())
        .build();
  }
}
