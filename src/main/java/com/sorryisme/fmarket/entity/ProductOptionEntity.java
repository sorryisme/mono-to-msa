package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** product_option 테이블. 상품 참조는 FK 값으로만 들고 있다. */
@Entity
@Table(name = "product_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionEntity extends BaseTimeEntity {

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

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private ProductOptionEntity(
      Long id, Long productId, String optionName, BigDecimal originPrice, BigDecimal salePrice) {
    this.id = id;
    this.productId = productId;
    this.optionName = optionName;
    this.originPrice = originPrice;
    this.salePrice = salePrice;
  }

  public void changePrice(BigDecimal originPrice, BigDecimal salePrice) {
    this.originPrice = originPrice;
    this.salePrice = salePrice;
  }

  public void softDelete(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
