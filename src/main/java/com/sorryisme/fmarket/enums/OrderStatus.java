package com.sorryisme.fmarket.enums;

/**
 * order.status 컬럼의 enum('PENDING','COMPLETED','CANCELLED') 에 대응한다.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} 으로 상수 이름이 그대로 저장되므로 별도 값 필드를 두지 않는다.
 *
 * <p>결제 모듈이 붙기 전까지는 PENDING 하나가 "주문 생성됨 / 재고 예약됨 / 결제 진행 중" 을 모두 뜻한다. 결제를 붙일 때는 이 의미를 분리해 재고 예약만 끝난
 * PAYMENT_PENDING 과 결제까지 끝난 PAID 를 따로 두고, 일정 시간 결제가 끝나지 않은 PAYMENT_PENDING 주문을 만료시켜 재고를 복구하는 배치를 함께
 * 둔다. DB enum 컬럼도 같이 바꿔야 하므로 지금은 상태를 늘리지 않았다.
 */
public enum OrderStatus {
  /** 결제 대기. 취소 가능. */
  PENDING,
  /** 확정. */
  COMPLETED,
  /** 취소. 재고를 되돌린다. */
  CANCELLED
}
