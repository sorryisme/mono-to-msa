package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.entity.Inventory
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/** data.sql 이 넣어 둔 product_option 1, 2 의 재고 행을 사용한다. */
@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryTest extends Specification {

    @Autowired
    InventoryRepository inventoryRepository
    @Autowired
    EntityManager em

    def "조건부 차감은 재고가 충분하면 1행을 갱신하고 수량을 줄인다"() {
        given:
        int before = quantityOf(1L)

        when:
        int affected = inventoryRepository.decreaseQuantity(1L, 10)
        em.clear()

        then:
        affected == 1
        quantityOf(1L) == before - 10
    }

    def "조건부 차감은 재고보다 많이 빼려 하면 0행을 갱신하고 수량을 그대로 둔다"() {
        given:
        int before = quantityOf(1L)

        when:
        int affected = inventoryRepository.decreaseQuantity(1L, before + 1)
        em.clear()

        then:
        affected == 0
        quantityOf(1L) == before
    }

    def "재고 행이 없는 옵션을 차감하면 0행이 갱신된다"() {
        expect:
        inventoryRepository.decreaseQuantity(999999L, 1) == 0
    }

    def "조건부 복구는 수량을 되돌린다"() {
        given:
        int before = quantityOf(2L)

        when:
        int affected = inventoryRepository.increaseQuantity(2L, 7)
        em.clear()

        then:
        affected == 1
        quantityOf(2L) == before + 7
    }

    def "재고 행이 없는 옵션을 복구하면 0행이 갱신된다"() {
        expect:
        inventoryRepository.increaseQuantity(999999L, 1) == 0
    }

    private int quantityOf(Long productOptionId) {
        return em.createQuery("select i from Inventory i where i.productOptionId = :id", Inventory)
                .setParameter("id", productOptionId)
                .getSingleResult()
                .getQuantity()
    }
}
