package com.sorryisme.fmarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderResponseDto {

  private Long id;
  private Long userId;
  private String status;
  private BigDecimal totalAmount;
  private LocalDateTime orderDate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<OrderDetailResponseDto> orderDetails;
}
