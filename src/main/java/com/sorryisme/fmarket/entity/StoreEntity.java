package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** store 테이블. 소유자(user)는 FK 값으로만 들고 있다. */
@Entity
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "store_id")
  private Long id;

  @Column(name = "store_name", nullable = false, length = 100)
  private String storeName;

  @Column(name = "logo_url", length = 255)
  private String logoUrl;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "business_number", nullable = false, length = 20)
  private String businessNumber;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Builder
  private StoreEntity(
      Long id,
      String storeName,
      String logoUrl,
      String description,
      String businessNumber,
      Long userId) {
    this.id = id;
    this.storeName = storeName;
    this.logoUrl = logoUrl;
    this.description = description;
    this.businessNumber = businessNumber;
    this.userId = userId;
  }

  public void updateStoreInfo(String storeName, String logoUrl, String description) {
    this.storeName = storeName;
    this.logoUrl = logoUrl;
    this.description = description;
  }
}
