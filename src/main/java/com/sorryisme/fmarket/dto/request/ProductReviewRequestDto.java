package com.sorryisme.fmarket.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductReviewRequestDto {
  private Long productId;
  private Integer rating;
  private String reviewText;
}
