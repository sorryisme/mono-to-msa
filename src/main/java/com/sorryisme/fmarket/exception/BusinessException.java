package com.sorryisme.fmarket.exception;

import com.sorryisme.fmarket.common.ErrorCode;
import lombok.Getter;

/**
 * 도메인 규칙 위반을 표현하는 유일한 예외. 상황은 예외 클래스가 아니라 {@link ErrorCode} 로 구분하며, HTTP 상태도 코드가 결정한다. 메시지를 생략하면
 * 코드의 기본 메시지를 쓴다.
 */
@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.getMessage());
  }

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
