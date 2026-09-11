package com.sorryisme.fmarket.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorryisme.fmarket.entity.Product;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 목록 한 건. 기존 응답과 JSON 키를 맞추기 위해 product_name 키를 유지한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductListResponseDto {

  private Long id;

  @JsonProperty("product_name")
  private String productName;

  private String description;
  private String thumbnail;
  private Integer majorCategory;
  private Integer subcategory;
  private String catalog;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;

  public static ProductListResponseDto from(Product product) {
    return ProductListResponseDto.builder()
        .id(product.getId())
        .productName(product.getProductName())
        .description(product.getDescription())
        .thumbnail(product.getThumbnail())
        .majorCategory(product.getMajorCategory())
        .subcategory(product.getSubcategory())
        .catalog(product.getCatalog())
        .createdAt(product.getCreatedAt())
        .updatedAt(product.getUpdatedAt())
        .deletedAt(product.getDeletedAt())
        .build();
  }
}
