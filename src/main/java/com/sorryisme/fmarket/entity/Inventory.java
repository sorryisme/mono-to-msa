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

/**
 * inventory 테이블.
 *
 * <p>기존 MyBatis 도메인에는 PK 필드가 없고 product_option_id 로만 다뤘지만, 테이블에는 id PK 가 있으므로 엔티티에서는 이를 식별자로 매핑한다.
 */
@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_option_id", nullable = false)
  private Long productOptionId;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Inventory(Long id, Long productOptionId, Integer quantity) {
    this.id = id;
    this.productOptionId = productOptionId;
    this.quantity = quantity;
  }

  /** 재고 차감. 남은 수량보다 많이 빼려 하면 거절한다. */
  public void decrease(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다: " + amount);
    }
    if (this.quantity < amount) {
      throw new IllegalArgumentException("재고 수량이 충분하지 않습니다.");
    }
    this.quantity -= amount;
  }

  /** 주문 취소 등으로 재고를 되돌린다. */
  public void increase(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("복구 수량은 1 이상이어야 합니다: " + amount);
    }
    this.quantity += amount;
  }
}
