package com.sorryisme.fmarket.dto.response;

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
}
