package com.sorryisme.fmarket.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pageable 에 PK 보조 정렬을 붙여 같은 정렬 값 사이에서도 페이지 경계가 흔들리지 않게 한다. */
public final class PageableSupport {

  private static final String ID = "id";

  private PageableSupport() {}

  /** 정렬이 없으면 id 정렬을, 정렬이 있는데 id 가 빠져 있으면 맨 뒤에 id 보조 정렬을 붙인다. */
  public static Pageable withStableSort(Pageable pageable) {
    if (pageable.isUnpaged()) {
      return pageable;
    }
    Sort sort = pageable.getSort();
    if (sort.getOrderFor(ID) != null) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(ID)));
  }
}
