package com.sorryisme.fmarket.enums;

/**
 * order.status 컬럼의 enum('PENDING','COMPLETED','CANCELLED') 에 대응한다.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} 으로 상수 이름이 그대로 저장되므로 별도 값 필드를 두지 않는다.
 */
public enum OrderStatus {
  /** 결제 대기. 취소 가능. */
  PENDING,
  /** 확정. */
  COMPLETED,
  /** 취소. 재고를 되돌린다. */
  CANCELLED
}
