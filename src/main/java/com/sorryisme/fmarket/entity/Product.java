package com.sorryisme.fmarket.entity;

import com.sorryisme.fmarket.enums.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * product 테이블. 카테고리 코드는 현재 스키마대로 코드 값(int)으로 유지한다.
 *
 * <p>삭제 여부는 deleted_at 대신 status 로 표현한다. 판매중지·삭제 같은 라이프사이클을 하나의 상태로 다루기 위해서다.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_name", length = 255)
  private String productName;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "thumbnail", length = 255)
  private String thumbnail;

  @Column(name = "major_category")
  private Integer majorCategory;

  @Column(name = "subcategory")
  private Integer subcategory;

  @Column(name = "catalog", columnDefinition = "TEXT")
  private String catalog;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProductStatus status;

  @Builder
  private Product(
      Long id,
      String productName,
      String description,
      String thumbnail,
      Integer majorCategory,
      Integer subcategory,
      String catalog,
      ProductStatus status) {
    this.id = id;
    this.productName = productName;
    this.description = description;
    this.thumbnail = thumbnail;
    this.majorCategory = majorCategory;
    this.subcategory = subcategory;
    this.catalog = catalog;
    this.status = status == null ? ProductStatus.ON_SALE : status;
  }

  /** 판매를 중지한다. 상세는 노출되지만 목록·주문에서 빠진다. */
  public void suspend() {
    ensureNotDeleted();
    this.status = ProductStatus.SUSPENDED;
  }

  /** 판매를 재개한다. */
  public void resume() {
    ensureNotDeleted();
    this.status = ProductStatus.ON_SALE;
  }

  /** 소프트 삭제. 되돌리지 않는다. */
  public void delete() {
    this.status = ProductStatus.DELETED;
  }

  public boolean isDeleted() {
    return this.status == ProductStatus.DELETED;
  }

  private void ensureNotDeleted() {
    if (isDeleted()) {
      throw new IllegalStateException("삭제된 상품의 상태는 바꿀 수 없습니다.");
    }
  }
}
