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

/** product_review 테이블. rating 은 DB CHECK 제약(1~5)을 그대로 따른다. */
@Entity
@Table(name = "product_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReviewEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "review_id")
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "rating")
  private Integer rating;

  @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
  private String reviewText;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Builder
  private ProductReviewEntity(
      Long id, Long productId, Integer rating, String reviewText, Long userId) {
    this.id = id;
    this.productId = productId;
    this.rating = rating;
    this.reviewText = reviewText;
    this.userId = userId;
  }

  public void updateReview(Integer rating, String reviewText) {
    this.rating = rating;
    this.reviewText = reviewText;
  }
}
