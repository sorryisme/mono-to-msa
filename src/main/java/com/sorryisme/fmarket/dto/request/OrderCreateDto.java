package com.sorryisme.fmarket.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
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

  @NotNull
  @Size(min = 1)
  List<OrderItemRequestDto> orderItems;

  public Map<Long, Integer> toProductQuantityMap() {
    return orderItems.stream()
        .collect(
            Collectors.toMap(
                OrderItemRequestDto::getProductOptionId, OrderItemRequestDto::getQuantity));
  }
}
