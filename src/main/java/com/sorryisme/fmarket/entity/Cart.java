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

/** cart 테이블. 장바구니 상세는 장바구니가 생명주기를 소유한다. */
@Entity
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CartDetail> cartDetails = new ArrayList<>();

  @Builder
  private Cart(Long id, Long userId, List<CartDetail> cartDetails) {
    this.id = id;
    this.userId = userId;
    if (cartDetails != null) {
      cartDetails.forEach(this::addCartDetail);
    }
  }

  public static Cart of(Long userId) {
    return Cart.builder().userId(userId).build();
  }

  public List<CartDetail> getCartDetails() {
    return Collections.unmodifiableList(cartDetails);
  }

  /** 연관관계 편의 메서드. 양쪽 메모리 상태를 함께 맞춘다. */
  public void addCartDetail(CartDetail cartDetail) {
    cartDetails.add(cartDetail);
    cartDetail.assignCart(this);
  }

  /** 장바구니에서 항목을 제거한다. orphanRemoval 로 DELETE 까지 이어진다. */
  public void removeCartDetail(CartDetail cartDetail) {
    cartDetails.remove(cartDetail);
    cartDetail.assignCart(null);
  }
}
