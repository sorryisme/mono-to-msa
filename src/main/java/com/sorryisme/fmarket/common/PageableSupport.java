package com.sorryisme.fmarket.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** 정렬 조건 없는 Pageable 에 PK 정렬을 붙여 페이지 경계가 흔들리지 않게 한다. */
public final class PageableSupport {

  private PageableSupport() {}

  public static Pageable withStableSort(Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
  }
}
