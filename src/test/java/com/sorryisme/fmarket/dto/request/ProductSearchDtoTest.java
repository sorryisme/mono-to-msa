package com.sorryisme.fmarket.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ProductSearchDtoTest {

  private final Pageable pageable = PageRequest.of(2, 10);

  @Test
  @DisplayName("요청 본문이 없으면 조건 없이 페이지 정보만 담는다")
  void buildsEmptyConditionWhenBodyMissing() {
    ProductSearchDto result = ProductSearchDto.from(null, pageable);

    assertThat(result.getQuery()).isNull();
    assertThat(result.getMajorCategory()).isNull();
    assertThat(result.getSubcategory()).isNull();
    assertThat(result.getPageable()).isEqualTo(pageable);
  }

  @Test
  @DisplayName("요청 본문이 있으면 조건을 복사하고 페이지 정보는 인자로 받은 값으로 덮는다")
  void copiesConditionAndOverridesPageable() {
    ProductSearchDto body =
        ProductSearchDto.builder()
            .query("셔츠")
            .majorCategory(1)
            .subcategory(3)
            .pageable(PageRequest.of(0, 99))
            .build();

    ProductSearchDto result = ProductSearchDto.from(body, pageable);

    assertThat(result.getQuery()).isEqualTo("셔츠");
    assertThat(result.getMajorCategory()).isEqualTo(1);
    assertThat(result.getSubcategory()).isEqualTo(3);
    assertThat(result.getPageable()).isEqualTo(pageable);
  }
}
