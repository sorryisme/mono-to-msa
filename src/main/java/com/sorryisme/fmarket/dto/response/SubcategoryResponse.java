package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.Subcategory;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubcategoryResponse {

  private Long subcategoryId;
  private String categoryName;
  private Long majorCategoryId;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SubcategoryResponse from(Subcategory subcategory) {
    return SubcategoryResponse.builder()
        .subcategoryId(subcategory.getId())
        .categoryName(subcategory.getCategoryName())
        .majorCategoryId(subcategory.getMajorCategory().getId())
        .description(subcategory.getDescription())
        .createdAt(subcategory.getCreatedAt())
        .updatedAt(subcategory.getUpdatedAt())
        .build();
  }
}
