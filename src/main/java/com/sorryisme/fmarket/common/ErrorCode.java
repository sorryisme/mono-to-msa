package com.sorryisme.fmarket.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 코드의 단일 출처. enum 이름이 응답 본문의 {@code code} 가 되고, 각 항목이 HTTP 상태와 기본 메시지를 소유한다. 도메인별로 묶어 두었으니 새
 * 코드는 해당 도메인 섹션에 추가하고 docs/API_RESPONSE.md 의 표를 함께 갱신한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // ===== 공통 (요청 형식·프레임워크·안전망) =====
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
  INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

  // ===== 인증 =====
  LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 정보가 일치하지 않습니다."),

  // ===== 유저 =====
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없는 유저입니다."),
  DUPLICATE_USER(HttpStatus.CONFLICT, "이미 등록된 유저입니다."),

  // ===== 상품 =====
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없는 제품입니다."),
  PRODUCT_OPTION_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중이 아닌 상품 옵션이 포함되어 있습니다."),

  // ===== 재고 =====
  OUT_OF_STOCK(HttpStatus.CONFLICT, "재고 수량이 충분하지 않습니다."),

  // ===== 장바구니 =====
  CART_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없는 장바구니입니다."),

  // ===== 주문 =====
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없는 주문입니다."),
  ORDER_STATUS_NOT_CHANGEABLE(HttpStatus.CONFLICT, "변경이 불가한 주문 상태입니다."),
  IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더는 36자리 UUID 여야 합니다."),
  DUPLICATE_REQUEST(HttpStatus.CONFLICT, "중복된 요청입니다.");

  private final HttpStatus status;
  private final String message;
}
