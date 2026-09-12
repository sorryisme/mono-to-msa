package com.sorryisme.fmarket.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableSupportTest {

  @Test
  @DisplayName("정렬이 없으면 id 정렬을 붙인다")
  void addsIdSortWhenNoSortGiven() {
    Pageable result = PageableSupport.withStableSort(PageRequest.of(1, 20));

    assertThat(result.getPageNumber()).isEqualTo(1);
    assertThat(result.getPageSize()).isEqualTo(20);
    assertThat(result.getSort()).isEqualTo(Sort.by("id"));
  }

  @Test
  @DisplayName("정렬이 있어도 id 가 없으면 맨 뒤에 id 보조 정렬을 붙인다")
  void appendsIdSortAsTieBreaker() {
    Pageable result =
        PageableSupport.withStableSort(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "orderDate")));

    assertThat(result.getSort())
        .extracting(Sort.Order::getProperty)
        .containsExactly("orderDate", "id");
    assertThat(result.getSort().getOrderFor("orderDate").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("정렬에 id 가 이미 있으면 그대로 돌려준다")
  void returnsSameInstanceWhenIdSortPresent() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

    assertThat(PageableSupport.withStableSort(pageable)).isSameAs(pageable);
  }

  @Test
  @DisplayName("unpaged 는 그대로 돌려준다")
  void returnsUnpagedAsIs() {
    assertThat(PageableSupport.withStableSort(Pageable.unpaged()).isUnpaged()).isTrue();
  }
}
