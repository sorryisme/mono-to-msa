package com.sorryisme.fmarket.exception;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.dto.FieldErrorDto;
import com.sorryisme.fmarket.common.dto.ResponseDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 docs/API_RESPONSE.md 의 봉투 형식으로 바꾼다. HTTP 상태는 {@link ErrorCode} 가 결정하고, 프레임워크 예외(검증 실패·파싱
 * 실패·405 등)도 같은 봉투로 통일한다. 4xx 는 WARN, 5xx 는 스택과 함께 ERROR 로 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  protected ResponseEntity<ResponseDto<Object>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    logByStatus(errorCode.getStatus(), errorCode, e);
    return ResponseEntity.status(errorCode.getStatus())
        .body(ResponseDto.error(errorCode, e.getMessage()));
  }

  /** 엔티티 인자 검증 안전망. 값 자체가 잘못된 것이므로 400 으로 본다. */
  @ExceptionHandler(IllegalArgumentException.class)
  protected ResponseEntity<ResponseDto<Object>> handleIllegalArgumentException(
      IllegalArgumentException e) {
    return respond(ErrorCode.INVALID_INPUT, e.getMessage(), e);
  }

  /** 엔티티 상태 규칙 안전망. 현재 상태와 충돌한 것이므로 409 로 본다. */
  @ExceptionHandler(IllegalStateException.class)
  protected ResponseEntity<ResponseDto<Object>> handleIllegalStateException(
      IllegalStateException e) {
    return respond(ErrorCode.INVALID_STATE, e.getMessage(), e);
  }

  /** 미처리 예외. 내부 정보를 노출하지 않도록 메시지는 고정 문구만 내린다. */
  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ResponseDto<Object>> handleException(Exception e) {
    return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), e);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldErrorDto> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream().map(FieldErrorDto::from).toList();
    log.warn("[{}] {} {}", ErrorCode.INVALID_INPUT, ex.getClass().getSimpleName(), fieldErrors);
    return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
        .headers(headers)
        .body(
            ResponseDto.error(
                ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), fieldErrors));
  }

  /**
   * ResponseEntityExceptionHandler 가 처리하는 나머지 프레임워크 예외의 공통 출구. 상태는 프레임워크가 정한 값을 유지하고 본문만 봉투로 바꾼다.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorCode errorCode = frameworkErrorCode(status);
    logByStatus(status, errorCode, ex);
    return ResponseEntity.status(status).headers(headers).body(ResponseDto.error(errorCode));
  }

  private static ErrorCode frameworkErrorCode(HttpStatusCode status) {
    if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) return ErrorCode.METHOD_NOT_ALLOWED;
    if (status.equals(HttpStatus.NOT_FOUND)) return ErrorCode.RESOURCE_NOT_FOUND;
    if (status.is4xxClientError()) return ErrorCode.INVALID_INPUT;
    return ErrorCode.INTERNAL_ERROR;
  }

  private ResponseEntity<ResponseDto<Object>> respond(
      ErrorCode errorCode, String message, Exception e) {
    logByStatus(errorCode.getStatus(), errorCode, e);
    return ResponseEntity.status(errorCode.getStatus()).body(ResponseDto.error(errorCode, message));
  }

  private static void logByStatus(HttpStatusCode status, ErrorCode errorCode, Exception e) {
    if (status.is5xxServerError()) {
      log.error("[{}] {}: {}", errorCode, e.getClass().getSimpleName(), e.getMessage(), e);
    } else {
      log.warn("[{}] {}: {}", errorCode, e.getClass().getSimpleName(), e.getMessage());
    }
  }
}
