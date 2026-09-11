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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** product_option 테이블. 상품 참조는 FK 값으로만 들고 있다. 삭제 여부는 status 로 표현한다. */
@Entity
@Table(name = "product_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "option_name", nullable = false, length = 100)
  private String optionName;

  @Column(name = "origin_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal originPrice;

  @Column(name = "sale_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal salePrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProductStatus status;

  @Builder
  private ProductOption(
      Long id,
      Long productId,
      String optionName,
      BigDecimal originPrice,
      BigDecimal salePrice,
      ProductStatus status) {
    this.id = id;
    this.productId = productId;
    this.optionName = optionName;
    this.originPrice = originPrice;
    this.salePrice = salePrice;
    this.status = status == null ? ProductStatus.ON_SALE : status;
  }

  public void changePrice(BigDecimal originPrice, BigDecimal salePrice) {
    this.originPrice = originPrice;
    this.salePrice = salePrice;
  }

  /** 판매를 중지한다. 상세에는 남지만 주문에서 빠진다. */
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
      throw new IllegalStateException("삭제된 상품 옵션의 상태는 바꿀 수 없습니다.");
    }
  }
}
