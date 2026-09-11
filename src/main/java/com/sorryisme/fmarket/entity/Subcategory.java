package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** subcategory 테이블. */
@Entity
@Table(name = "subcategory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subcategory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "subcategory_id")
  private Long id;

  @Column(name = "category_name", nullable = false, length = 100)
  private String categoryName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "major_category_id", nullable = false)
  private MajorCategory majorCategory;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Builder
  private Subcategory(Long id, String categoryName, String description) {
    this.id = id;
    this.categoryName = categoryName;
    this.description = description;
  }

  /** MajorCategory#addSubcategory 에서만 호출한다. */
  void assignMajorCategory(MajorCategory majorCategory) {
    this.majorCategory = majorCategory;
  }
}
