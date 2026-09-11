package com.sorryisme.fmarket.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductReview {

  private Long reviewId;
  private Long productId;
  private Integer rating;
  private String reviewText;
  private Long userId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
