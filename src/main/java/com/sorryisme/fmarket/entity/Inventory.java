package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * inventory 테이블.
 *
 * <p>기존 MyBatis 도메인에는 PK 필드가 없고 product_option_id 로만 다뤘지만, 테이블에는 id PK 가 있으므로 엔티티에서는 이를 식별자로 매핑한다.
 * 재고는 옵션의 생명주기를 따르므로 독립된 삭제 상태를 두지 않는다.
 *
 * <p>수량 증감은 엔티티 변경 감지가 아니라 {@link com.sorryisme.fmarket.repository.InventoryRepository} 의 조건부
 * UPDATE 로만 한다. "재고가 음수가 되지 않는다"는 불변식을 애플리케이션이 아니라 DB 가 UPDATE 조건으로 지키게 해서, 동시 주문에서도 조회와 차감 사이에 끼어들
 * 틈이 없게 하기 위함이다. 따라서 여기에는 수량 변경 메서드를 두지 않는다.
 */
@Entity
@Table(
    name = "inventory",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_inventory_product_option_id",
            columnNames = "product_option_id"))
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

  @Builder
  private Inventory(Long id, Long productOptionId, Integer quantity) {
    this.id = id;
    this.productOptionId = productOptionId;
    this.quantity = quantity;
  }
}
