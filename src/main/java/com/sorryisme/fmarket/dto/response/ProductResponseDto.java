package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.entity.ProductReview;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductResponseDto {

  private Long id;
  private String description;
  private String thumbnail;
  private String catalog;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<ProductOptionResponseDto> options;
  private List<ProductReviewResponseDto> reviews;

  public static ProductResponseDto of(
      Product product, List<ProductOption> options, List<ProductReview> reviews) {
    return ProductResponseDto.builder()
        .id(product.getId())
        .description(product.getDescription())
        .thumbnail(product.getThumbnail())
        .catalog(product.getCatalog())
        .createdAt(product.getCreatedAt())
        .updatedAt(product.getUpdatedAt())
        .options(options.stream().map(ProductOptionResponseDto::from).toList())
        .reviews(reviews.stream().map(ProductReviewResponseDto::from).toList())
        .build();
  }
}
