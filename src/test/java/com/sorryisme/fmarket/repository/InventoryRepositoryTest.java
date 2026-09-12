package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.entity.Inventory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** data.sql 이 넣어 둔 product_option 1, 2 의 재고 행을 사용한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryTest {

  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("조건부 차감은 재고가 충분하면 1행을 갱신하고 수량을 줄인다")
  void decreasesWhenStockSufficient() {
    int before = quantityOf(1L);

    int affected = inventoryRepository.decreaseQuantity(1L, 10);
    em.clear();

    assertThat(affected).isEqualTo(1);
    assertThat(quantityOf(1L)).isEqualTo(before - 10);
  }

  @Test
  @DisplayName("조건부 차감은 재고보다 많이 빼려 하면 0행을 갱신하고 수량을 그대로 둔다")
  void doesNotDecreaseBeyondStock() {
    int before = quantityOf(1L);

    int affected = inventoryRepository.decreaseQuantity(1L, before + 1);
    em.clear();

    assertThat(affected).isZero();
    assertThat(quantityOf(1L)).isEqualTo(before);
  }

  @Test
  @DisplayName("재고 행이 없는 옵션을 차감하면 0행이 갱신된다")
  void decreaseAffectsNoRowWhenInventoryMissing() {
    assertThat(inventoryRepository.decreaseQuantity(999999L, 1)).isZero();
  }

  @Test
  @DisplayName("조건부 복구는 수량을 되돌린다")
  void increasesQuantity() {
    int before = quantityOf(2L);

    int affected = inventoryRepository.increaseQuantity(2L, 7);
    em.clear();

    assertThat(affected).isEqualTo(1);
    assertThat(quantityOf(2L)).isEqualTo(before + 7);
  }

  @Test
  @DisplayName("재고 행이 없는 옵션을 복구하면 0행이 갱신된다")
  void increaseAffectsNoRowWhenInventoryMissing() {
    assertThat(inventoryRepository.increaseQuantity(999999L, 1)).isZero();
  }

  private int quantityOf(Long productOptionId) {
    return em.createQuery(
            "select i from Inventory i where i.productOptionId = :id", Inventory.class)
        .setParameter("id", productOptionId)
        .getSingleResult()
        .getQuantity();
  }
}
