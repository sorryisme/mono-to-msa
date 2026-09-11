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

/** cart_detail 테이블. 장바구니는 객체 연관, 상품 옵션은 FK 값으로 매핑한다. */
@Entity
@Table(name = "cart_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartDetail extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cart_id", nullable = false)
  private Cart cart;

  @Column(name = "product_option_id", nullable = false)
  private Long productOptionId;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Builder
  private CartDetail(Long id, Long productOptionId, Integer quantity) {
    this.id = id;
    this.productOptionId = productOptionId;
    this.quantity = quantity;
  }

  public void changeQuantity(int quantity) {
    if (quantity < 1) {
      throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
    }
    this.quantity = quantity;
  }

  /** Cart 의 연관관계 편의 메서드에서만 호출한다. */
  void assignCart(Cart cart) {
    this.cart = cart;
  }
}
