package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  /** 대상 옵션의 재고 행에 비관적 락(SELECT ... FOR UPDATE)을 걸고 가져온다. 수량 변경은 반환된 엔티티의 변경 감지로 반영된다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.productOptionId in :productOptionIds")
  List<Inventory> findAllByProductOptionIdInForUpdate(
      @Param("productOptionIds") Collection<Long> productOptionIds);
}
