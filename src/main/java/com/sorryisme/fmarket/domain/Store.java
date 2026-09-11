package com.sorryisme.fmarket.domain;

import com.sorryisme.fmarket.dto.request.SellerRequestDto;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Store {

  private long storeId;
  private String storeName;
  private String logoUrl;
  private String description;
  private String businessNumber;
  private long userId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Store from(SellerRequestDto sellerRequestDto, long userId) {
    return Store.builder()
        .storeName(sellerRequestDto.getStoreName())
        .logoUrl(sellerRequestDto.getLogoUrl())
        .description(sellerRequestDto.getDescription())
        .businessNumber(sellerRequestDto.getBusinessNumber())
        .userId(userId)
        .build();
  }
}
