package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.ProductReview;
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

  public static ProductReviewResponseDto from(ProductReview review) {
    return ProductReviewResponseDto.builder()
        .reviewId(review.getId())
        .productId(review.getProductId())
        .userId(review.getUserId())
        .rating(review.getRating())
        .reviewText(review.getReviewText())
        .createdAt(review.getCreatedAt())
        .build();
  }
}
