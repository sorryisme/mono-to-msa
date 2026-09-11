package com.sorryisme.fmarket.common.dto;

import com.sorryisme.fmarket.common.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 API 응답의 봉투. 성공/실패는 HTTP 상태가 말해주고, 본문 {@code code} 는 성공 시 {@code OK}, 실패 시 {@link ErrorCode}
 * 이름이다. 형식은 docs/API_RESPONSE.md 의 계약을 따른다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResponseDto<T> {

  public static final String SUCCESS_CODE = "OK";
  private static final String SUCCESS_MESSAGE = "성공했습니다.";

  private String code;
  private String message;
  private T data;

  public static <T> ResponseDto<T> success(T data) {
    return success(data, SUCCESS_MESSAGE);
  }

  public static <T> ResponseDto<T> success(T data, String message) {
    return new ResponseDto<>(SUCCESS_CODE, message, data);
  }

  public static ResponseDto<Object> error(ErrorCode errorCode) {
    return error(errorCode, errorCode.getMessage(), null);
  }

  public static ResponseDto<Object> error(ErrorCode errorCode, String message) {
    return error(errorCode, message, null);
  }

  public static ResponseDto<Object> error(ErrorCode errorCode, String message, Object data) {
    return new ResponseDto<>(errorCode.name(), message, data);
  }
}
