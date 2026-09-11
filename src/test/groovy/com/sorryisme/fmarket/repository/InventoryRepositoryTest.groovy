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

    def "productOptionId 목록으로 재고를 잠금 조회한다"() {
        when:
        List<Inventory> inventories = inventoryRepository.findAllByProductOptionIdInForUpdate([1L, 2L])

        then:
        inventories.size() >= 1
        inventories*.getProductOptionId().containsAll([1L, 2L])
        inventories.every { it.getQuantity() >= 0 }
    }

    def "잠금 조회한 재고의 수량 변경은 변경 감지로 반영된다"() {
        given:
        Inventory before = inventoryRepository.findAllByProductOptionIdInForUpdate([1L]).first()
        int beforeQuantity = before.getQuantity()

        when:
        before.increase(10)
        em.flush()
        em.clear()
        Inventory after = inventoryRepository.findAllByProductOptionIdInForUpdate([1L]).first()

        then:
        after.getQuantity() == beforeQuantity + 10
    }

    def "없는 옵션 id 로 조회하면 빈 목록을 돌려준다"() {
        expect:
        inventoryRepository.findAllByProductOptionIdInForUpdate([999999L]).isEmpty()
    }
}
