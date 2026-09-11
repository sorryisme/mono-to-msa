package com.sorryisme.fmarket.dto.response;

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
}
