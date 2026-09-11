package com.sorryisme.fmarket.common.dto;

import org.springframework.validation.FieldError;

/** 입력 검증 실패({@code INVALID_INPUT}) 응답의 {@code data} 항목. */
public record FieldErrorDto(String field, String reason) {

  public static FieldErrorDto from(FieldError fieldError) {
    String reason = fieldError.getDefaultMessage();
    return new FieldErrorDto(fieldError.getField(), reason == null ? "올바르지 않은 값입니다." : reason);
  }
}
