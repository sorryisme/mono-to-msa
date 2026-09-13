package com.sorryisme.fmarket.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductSearchDto {

  private String query;
  private Integer majorCategory;
  private Integer subcategory;
  private Pageable pageable;

  /** 요청 본문이 없으면(searchDto == null) 조건 없는 전체 목록 조회로 다룬다. */
  public static ProductSearchDto from(ProductSearchDto searchDto, Pageable pageable) {
    if (searchDto == null) {
      return ProductSearchDto.builder().pageable(pageable).build();
    }

    return ProductSearchDto.builder()
        .query(searchDto.getQuery())
        .majorCategory(searchDto.getMajorCategory())
        .subcategory(searchDto.getSubcategory())
        .pageable(pageable)
        .build();
  }
}
