package com.sorryisme.fmarket.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** major_category 테이블. 중분류는 대분류가 생명주기를 소유한다(스키마 ON DELETE CASCADE). */
@Entity
@Table(name = "major_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MajorCategory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "major_category_id")
  private Long id;

  @Column(name = "category_name", nullable = false, length = 100)
  private String categoryName;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @OneToMany(mappedBy = "majorCategory", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Subcategory> subcategories = new ArrayList<>();

  @Builder
  private MajorCategory(Long id, String categoryName, String description) {
    this.id = id;
    this.categoryName = categoryName;
    this.description = description;
  }

  public List<Subcategory> getSubcategories() {
    return Collections.unmodifiableList(subcategories);
  }

  /** 연관관계 편의 메서드. 양쪽 메모리 상태를 함께 맞춘다. */
  public void addSubcategory(Subcategory subcategory) {
    subcategories.add(subcategory);
    subcategory.assignMajorCategory(this);
  }
}
