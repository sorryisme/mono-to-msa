package com.sorryisme.fmarket.mapper;

import com.sorryisme.fmarket.domain.Inventory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper {
  List<Inventory> findStockQuantityForUpdate(List<Inventory> inventories);

  int increaseStockQuantity(Inventory inventory);

  int updateStockQuantity(List<Inventory> inventory);
}
