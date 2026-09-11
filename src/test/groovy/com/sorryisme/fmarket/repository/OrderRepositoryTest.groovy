package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.entity.Order
import com.sorryisme.fmarket.enums.OrderStatus
import com.sorryisme.fmarket.testUtils.DomainFixture
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import java.time.LocalDateTime

/** 실제 MySQL(schema.sql + data.sql) 위에서 주문 리포지토리의 커스텀 쿼리를 검증한다. data.sql 의 user 1, product_option 1 을 사용한다. */
@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest extends Specification {

    @Autowired
    OrderRepository orderRepository
    @Autowired
    EntityManager em

    def "기간 조회는 from 이상 to 미만의 주문만 돌려준다"() {
        given:
        LocalDateTime base = LocalDateTime.of(2030, 6, 15, 12, 0)
        Order inRange = persistOrder(base)
        Order onTo = persistOrder(LocalDateTime.of(2030, 6, 16, 0, 0))
        Order before = persistOrder(LocalDateTime.of(2030, 6, 14, 23, 59))
        em.flush()

        when:
        Page<Order> page = orderRepository.findByUserIdAndOrderDateIn(
                1L, LocalDateTime.of(2030, 6, 15, 0, 0), LocalDateTime.of(2030, 6, 16, 0, 0), Pageable.ofSize(10))

        then:
        page.getContent()*.getId().contains(inRange.getId())
        !page.getContent()*.getId().contains(onTo.getId())
        !page.getContent()*.getId().contains(before.getId())
    }

    def "사용자 기준 페이징 조회는 총 개수를 함께 돌려준다"() {
        given:
        3.times { persistOrder(LocalDateTime.now()) }
        em.flush()

        when:
        Page<Order> page = orderRepository.findByUserId(1L, Pageable.ofSize(2))

        then:
        page.getContent().size() == 2
        page.getTotalElements() >= 3
    }

    def "상세 포함 조회는 주문 상세까지 한 번에 가져온다"() {
        given:
        Order saved = persistOrder(LocalDateTime.now())
        em.flush()
        em.clear()

        when:
        Order found = orderRepository.findWithDetailsById(saved.getId()).orElseThrow()

        then:
        found.getOrderDetails().size() == 1
        found.getOrderDetails()[0].getProductOptionId() == 1L
    }

    def "비관적 락 조회는 주문을 돌려주고 변경 감지로 상태가 반영된다"() {
        given:
        Order saved = persistOrder(LocalDateTime.now())
        em.flush()
        em.clear()

        when:
        Order locked = orderRepository.findByIdForUpdate(saved.getId()).orElseThrow()
        locked.changeStatus(OrderStatus.CANCELLED)
        em.flush()
        em.clear()

        then:
        orderRepository.findById(saved.getId()).orElseThrow().getStatus() == OrderStatus.CANCELLED
    }

    private Order persistOrder(LocalDateTime orderDate) {
        Order order = Order.builder()
                .userId(1L)
                .orderDate(orderDate)
                .totalAmount(new BigDecimal("500.00"))
                .orderDetails([DomainFixture.createOrderDetail(null, 1L, 5)])
                .build()
        return orderRepository.save(order)
    }
}
