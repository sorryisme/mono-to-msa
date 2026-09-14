package com.sorryisme.fmarket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateDto {

  // 원소에 @Valid 가 없으면 OrderItemRequestDto 의 @NotNull·@Min(1) 이 검사되지 않는다.
  // 음수 수량이 통과하면 조건부 차감 UPDATE 가 재고를 오히려 늘리고 총액이 음수가 된다.
  @NotNull
  @Size(min = 1)
  List<@Valid @NotNull OrderItemRequestDto> orderItems;

  /**
   * 같은 옵션이 두 번 오면 수량을 합칠지 한쪽을 버릴지 서버가 추측해야 한다. 추측하지 않고 400 으로 거절한다. 이 검사가 없으면 {@link
   * #toProductQuantityMap()} 의 키 충돌이 500 으로 번진다.
   */
  @AssertTrue(message = "같은 상품 옵션을 중복해 주문할 수 없습니다.")
  public boolean isProductOptionIdUnique() {
    if (orderItems == null) {
      return true;
    }
    List<Long> ids =
        orderItems.stream()
            .filter(Objects::nonNull)
            .map(OrderItemRequestDto::getProductOptionId)
            .filter(Objects::nonNull)
            .toList();
    return ids.stream().distinct().count() == ids.size();
  }

  public Map<Long, Integer> toProductQuantityMap() {
    return orderItems.stream()
        .collect(
            Collectors.toMap(
                OrderItemRequestDto::getProductOptionId, OrderItemRequestDto::getQuantity));
  }
}
