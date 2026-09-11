package com.sorryisme.fmarket.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductReviewResponseDto {

  private Long reviewId;
  private Long productId;
  private Long userId;
  private Integer rating;
  private String reviewText;
  private LocalDateTime createdAt;
}
