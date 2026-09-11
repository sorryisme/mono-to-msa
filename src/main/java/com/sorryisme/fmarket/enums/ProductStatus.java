package com.sorryisme.fmarket.enums;

/**
 * product.status / product_option.status 컬럼의 enum('ON_SALE','SUSPENDED','DELETED') 에 대응한다.
 *
 * <p>품절은 재고 수량에서 파생되므로 상태로 저장하지 않는다. DELETED 는 소프트 삭제이며, 고객 조회 경로에서는 제외된다.
 */
public enum ProductStatus {
  /** 판매중. 목록 노출·주문 가능. */
  ON_SALE,
  /** 판매중지. 상세는 볼 수 있지만 목록 노출·주문은 막는다. */
  SUSPENDED,
  /** 삭제(소프트). 모든 고객 조회에서 제외. */
  DELETED
}
