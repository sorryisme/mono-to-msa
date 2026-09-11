package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  /**
   * 재고 확인과 차감을 한 UPDATE 로 처리한다. {@code quantity >= :quantity} 조건이 UPDATE 안에 들어 있어 DB 가 행 잠금을 짧게 잡은
   * 채 검사·차감을 원자적으로 수행하므로, 별도의 비관적 조회 락 없이도 초과 판매가 생기지 않는다.
   *
   * <p>inventory 는 product_option_id 당 한 행만 존재한다(스키마의 UNIQUE 제약).
   *
   * @return 1 이면 재고 확보 성공, 0 이면 재고 부족이거나 해당 옵션의 재고 행이 없음
   */
  @Modifying
  @Query(
      "update Inventory i set i.quantity = i.quantity - :quantity "
          + "where i.productOptionId = :productOptionId and i.quantity >= :quantity")
  int decreaseQuantity(
      @Param("productOptionId") Long productOptionId, @Param("quantity") int quantity);

  /**
   * 주문 취소·결제 실패 등으로 재고를 되돌린다. 복구는 상한 검사가 없으므로 조건 없이 더한다.
   *
   * @return 1 이면 복구 성공, 0 이면 해당 옵션의 재고 행이 없음
   */
  @Modifying
  @Query(
      "update Inventory i set i.quantity = i.quantity + :quantity "
          + "where i.productOptionId = :productOptionId")
  int increaseQuantity(
      @Param("productOptionId") Long productOptionId, @Param("quantity") int quantity);
}
