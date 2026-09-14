package com.sorryisme.fmarket.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주문 생성 요청의 입력 경계. 여기서 통과한 값이 그대로 재고 차감 UPDATE 와 총액 계산에 들어간다. */
class OrderCreateDtoTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @Test
  @DisplayName("서로 다른 옵션과 1 이상 수량이면 통과하고 옵션별 수량 맵을 만든다")
  void acceptsValidItems() {
    OrderCreateDto dto = dtoOf(new OrderItemRequestDto(1L, 1), new OrderItemRequestDto(2L, 3));

    assertThat(validator.validate(dto)).isEmpty();
    assertThat(dto.toProductQuantityMap()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 1, 2L, 3));
  }

  @Test
  @DisplayName("항목 수량이 1 미만이면 거절한다 (항목 내부 제약까지 검사한다)")
  void rejectsQuantityBelowOne() {
    Set<ConstraintViolation<OrderCreateDto>> violations =
        validator.validate(dtoOf(new OrderItemRequestDto(1L, 0)));

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("orderItems[0].quantity");
  }

  @Test
  @DisplayName("음수 수량도 거절한다")
  void rejectsNegativeQuantity() {
    assertThat(validator.validate(dtoOf(new OrderItemRequestDto(1L, -5)))).hasSize(1);
  }

  @Test
  @DisplayName("항목의 옵션 ID 가 없거나 항목 자체가 null 이면 거절한다")
  void rejectsMissingOptionIdOrNullItem() {
    assertThat(validator.validate(dtoOf(new OrderItemRequestDto(null, 1))))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("orderItems[0].productOptionId");
    assertThat(validator.validate(dtoOf((OrderItemRequestDto) null)))
        .extracting(v -> v.getPropertyPath().toString())
        .singleElement(STRING)
        .startsWith("orderItems[0]");
  }

  @Test
  @DisplayName("같은 옵션이 두 번 오면 거절한다")
  void rejectsDuplicateOptionId() {
    Set<ConstraintViolation<OrderCreateDto>> violations =
        validator.validate(dtoOf(new OrderItemRequestDto(1L, 1), new OrderItemRequestDto(1L, 2)));

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("productOptionIdUnique");
  }

  @Test
  @DisplayName("항목 목록이 없거나 비어 있으면 거절하고, 중복 검사는 그 위반에 얹히지 않는다")
  void rejectsMissingOrEmptyItems() {
    assertThat(validator.validate(new OrderCreateDto(null)))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("orderItems");
    assertThat(validator.validate(new OrderCreateDto(List.of())))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("orderItems");
  }

  private static OrderCreateDto dtoOf(OrderItemRequestDto... items) {
    return new OrderCreateDto(Arrays.asList(items));
  }
}
