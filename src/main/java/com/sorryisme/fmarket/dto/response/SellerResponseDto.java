package com.sorryisme.fmarket.dto.response;

import com.sorryisme.fmarket.entity.Store;
import com.sorryisme.fmarket.entity.User;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SellerResponseDto extends UserResponseDto {

  private String storeName;
  private String businessNumber;
  private String logoUrl;
  private String description;
  private String createdAt;
  private String updatedAt;
  private String deletedAt;

  public static SellerResponseDto from(User user, Store store) {
    return SellerResponseDto.builder()
        .id(user.getId())
        .loginId(user.getLoginId())
        .name(user.getName())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .storeName(store.getStoreName())
        .businessNumber(store.getBusinessNumber())
        .logoUrl(store.getLogoUrl())
        .description(store.getDescription())
        .build();
  }
}
