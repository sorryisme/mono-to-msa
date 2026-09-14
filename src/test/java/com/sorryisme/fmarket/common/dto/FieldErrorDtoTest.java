package com.sorryisme.fmarket.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

class FieldErrorDtoTest {

  @Test
  @DisplayName("검증 메시지를 사유로 옮긴다")
  void copiesDefaultMessage() {
    FieldError fieldError = new FieldError("dto", "quantity", "1 이상이어야 합니다");

    assertThat(FieldErrorDto.from(fieldError))
        .isEqualTo(new FieldErrorDto("quantity", "1 이상이어야 합니다"));
  }

  @Test
  @DisplayName("검증 메시지가 없으면 응답 사유가 null 로 나가지 않게 기본 문구를 쓴다")
  void fallsBackWhenMessageMissing() {
    FieldError fieldError = new FieldError("dto", "quantity", null);

    assertThat(FieldErrorDto.from(fieldError).reason()).isEqualTo("올바르지 않은 값입니다.");
  }
}
