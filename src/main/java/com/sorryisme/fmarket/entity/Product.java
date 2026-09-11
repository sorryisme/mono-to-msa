package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** product 테이블. 카테고리 코드는 현재 스키마대로 코드 값(int)으로 유지한다. */
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

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Product(
      Long id,
      String productName,
      String description,
      String thumbnail,
      Integer majorCategory,
      Integer subcategory,
      String catalog) {
    this.id = id;
    this.productName = productName;
    this.description = description;
    this.thumbnail = thumbnail;
    this.majorCategory = majorCategory;
    this.subcategory = subcategory;
    this.catalog = catalog;
  }

  public void softDelete(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
