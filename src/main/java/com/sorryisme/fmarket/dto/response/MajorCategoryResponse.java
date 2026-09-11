package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.MajorCategory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MajorCategoryResponse {

  private Long majorCategoryId;
  private String majorCategoryName;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<SubcategoryResponse> subcategories;

  public static MajorCategoryResponse from(MajorCategory majorCategory) {
    return MajorCategoryResponse.builder()
        .majorCategoryId(majorCategory.getId())
        .majorCategoryName(majorCategory.getCategoryName())
        .description(majorCategory.getDescription())
        .createdAt(majorCategory.getCreatedAt())
        .updatedAt(majorCategory.getUpdatedAt())
        .subcategories(
            majorCategory.getSubcategories().stream().map(SubcategoryResponse::from).toList())
        .build();
  }
}
