package com.sorryisme.fmarket.service

import com.sorryisme.fmarket.dto.request.OrderCreateDto
import com.sorryisme.fmarket.dto.request.OrderItemRequestDto
import com.sorryisme.fmarket.dto.request.OrderSearchDto
import com.sorryisme.fmarket.dto.response.OrderListResponseDto
import com.sorryisme.fmarket.dto.response.OrderResponseDto
import com.sorryisme.fmarket.entity.Inventory
import com.sorryisme.fmarket.entity.Order
import com.sorryisme.fmarket.entity.ProductOption
import com.sorryisme.fmarket.enums.OrderStatus
import com.sorryisme.fmarket.enums.ProductStatus
import com.sorryisme.fmarket.exception.BusinessException
import com.sorryisme.fmarket.common.ErrorCode
import com.sorryisme.fmarket.repository.InventoryRepository
import com.sorryisme.fmarket.repository.OrderRepository
import com.sorryisme.fmarket.repository.ProductOptionRepository
import com.sorryisme.fmarket.testUtils.DomainFixture
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import spock.lang.Specification

import java.time.LocalDateTime

class OrderServiceTest extends Specification {

    OrderRepository orderRepository = Mock()
    InventoryRepository inventoryRepository = Mock()
    ProductOptionRepository productOptionRepository = Mock()
    OrderService orderService = new OrderService(orderRepository, inventoryRepository, productOptionRepository)

    List<ProductOption> productOptions
    List<Inventory> inventories

    private static final String UUID = "166f9067-2e5f-4932-e314-f438ae846d24"

    def setup() {
        productOptions = [
                DomainFixture.createProductOption(1L, 101L, "Option1", "900.00"),
                DomainFixture.createProductOption(2L, 102L, "Option2", "1800.00")
        ]
        inventories = DomainFixture.createInventories()
    }

    def "기간 조건이 있으면 기간 조회 메서드로, 없으면 사용자 기준으로 페이징 조회한다"() {
        given:
        OrderSearchDto orderSearchDto = createOrderSearchDto(startPeriod, endPeriod)
        Page<Order> page = new PageImpl<>([DomainFixture.createOrder(1L, OrderStatus.PENDING)])

        when:
        Page<OrderListResponseDto> result = orderService.findAllOrderList(orderSearchDto)

        then:
        periodCalls * orderRepository.findByUserIdAndOrderDateIn(1L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0), _ as Pageable) >> page
        plainCalls * orderRepository.findByUserId(1L, _ as Pageable) >> page
        result.getContent().size() == 1
        result.getContent().get(0).getStatus() == "PENDING"

        where:
        startPeriod  | endPeriod    | periodCalls | plainCalls
        "2025-01-01" | "2025-12-31" | 1           | 0
        null         | null         | 0           | 1
        ""           | ""           | 0           | 1
    }

    def "ID로 주문 조회 시 존재하면 상세를 포함한 데이터를 반환한다"() {
        given:
        orderRepository.findWithDetailsById(1L) >> Optional.of(DomainFixture.createOrder(1L, OrderStatus.PENDING))

        when:
        OrderResponseDto result = orderService.findOneOrder(1L)

        then:
        result.getId() == 1L
        result.getUserId() == 1L
        result.getTotalAmount() == new BigDecimal(10000)
        result.getOrderDetails().size() == 1
    }

    def "주문 조회시 주문이 존재하지 않으면 예외가 발생한다"() {
        given:
        orderRepository.findWithDetailsById(_ as Long) >> Optional.empty()

        when:
        orderService.findOneOrder(1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.ORDER_NOT_FOUND
    }

    def "주문 확정 시 상태가 COMPLETED 로 바뀌고 orderId가 리턴된다"() {
        given:
        Order order = DomainFixture.createOrder(1L, OrderStatus.PENDING)
        orderRepository.findById(1L) >> Optional.of(order)

        when:
        Long result = orderService.confirmOrder(1L)

        then:
        result == 1L
        order.getStatus() == OrderStatus.COMPLETED
    }

    def "주문 확정 시 주문이 없을 경우 에러가 발생한다"() {
        given:
        orderRepository.findById(_ as Long) >> Optional.empty()

        when:
        orderService.confirmOrder(1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.ORDER_NOT_FOUND
    }

    def "주문취소 시 재고가 복구되고 상태가 CANCELLED 로 바뀐다"() {
        given:
        Order order = DomainFixture.createOrder(1L, OrderStatus.PENDING)
        Inventory inventory = DomainFixture.createInventory(1L, 3)
        orderRepository.findByIdForUpdate(1L) >> Optional.of(order)
        inventoryRepository.findAllByProductOptionIdInForUpdate({ it.contains(1L) }) >> [inventory]

        when:
        Long result = orderService.cancelOrder(1L)

        then:
        result == 1L
        order.getStatus() == OrderStatus.CANCELLED
        inventory.getQuantity() == 3 + 5
    }

    def "주문취소 시 주문이 없을 경우 에러를 발생시킨다"() {
        given:
        orderRepository.findByIdForUpdate(1L) >> Optional.empty()

        when:
        orderService.cancelOrder(1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.ORDER_NOT_FOUND
    }

    def "주문취소 시 주문 상태가 변경 완료 상태 일때 에러가 발생된다."() {
        given:
        orderRepository.findByIdForUpdate(1L) >> Optional.of(DomainFixture.createOrder(1L, OrderStatus.COMPLETED))

        when:
        orderService.cancelOrder(1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.ORDER_STATUS_NOT_CHANGEABLE
        0 * inventoryRepository._
    }

    def "createOrder는 주문을 저장하고 재고를 차감한다"() {
        given:
        OrderCreateDto orderCreateDto = new OrderCreateDto([
                new OrderItemRequestDto(1L, 1),
                new OrderItemRequestDto(2L, 2)
        ])
        productOptionRepository.findAllByIdInAndStatus(_, ProductStatus.ON_SALE) >> productOptions
        inventoryRepository.findAllByProductOptionIdInForUpdate(_) >> inventories

        when:
        orderService.createOrder(UUID, 1L, orderCreateDto)

        then:
        1 * orderRepository.save({ Order o ->
            o.getOrderDetails().size() == 2 &&
                    o.getStatus() == OrderStatus.PENDING &&
                    o.getTotalAmount() == new BigDecimal("900.00") + new BigDecimal("1800.00") * 2
        })
        inventories[0].getQuantity() == 0
        inventories[1].getQuantity() == 0
    }

    def "createOrder는 재고 부족 시 예외를 발생시킨다"() {
        given:
        OrderCreateDto orderCreateDto = new OrderCreateDto([
                new OrderItemRequestDto(1L, 3),
                new OrderItemRequestDto(2L, 2)
        ])
        productOptionRepository.findAllByIdInAndStatus(_, ProductStatus.ON_SALE) >> productOptions
        inventoryRepository.findAllByProductOptionIdInForUpdate(_) >> inventories

        when:
        orderService.createOrder(UUID, 1L, orderCreateDto)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.OUT_OF_STOCK
    }

    def "createOrder는 판매중이 아닌 옵션이 섞여 있으면 주문을 저장하지 않고 거절한다"() {
        given:
        OrderCreateDto orderCreateDto = new OrderCreateDto([
                new OrderItemRequestDto(1L, 1),
                new OrderItemRequestDto(99L, 1)
        ])
        productOptionRepository.findAllByIdInAndStatus(_, ProductStatus.ON_SALE) >> [productOptions[0]]

        when:
        orderService.createOrder(UUID, 1L, orderCreateDto)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.PRODUCT_OPTION_NOT_ON_SALE
        0 * orderRepository.save(_)
        0 * inventoryRepository._
    }

    def "createOrder는 재고 행이 없는 옵션이면 예외를 발생시킨다"() {
        given:
        OrderCreateDto orderCreateDto = new OrderCreateDto([new OrderItemRequestDto(1L, 1)])
        productOptionRepository.findAllByIdInAndStatus(_, ProductStatus.ON_SALE) >> [productOptions[0]]
        inventoryRepository.findAllByProductOptionIdInForUpdate(_) >> []

        when:
        orderService.createOrder(UUID, 1L, orderCreateDto)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.OUT_OF_STOCK
    }

    private static OrderSearchDto createOrderSearchDto(String startPeriod, String endPeriod) {
        return OrderSearchDto.builder()
                .userId(1L)
                .startPeriod(startPeriod)
                .endPeriod(endPeriod)
                .pageable(Pageable.ofSize(10))
                .build()
    }
}
