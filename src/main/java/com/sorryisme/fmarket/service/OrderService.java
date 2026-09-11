package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.annotation.IdempotencyKeyParam;
import com.sorryisme.fmarket.annotation.Idempotent;
import com.sorryisme.fmarket.common.PageableSupport;
import com.sorryisme.fmarket.dto.request.OrderCreateDto;
import com.sorryisme.fmarket.dto.request.OrderSearchDto;
import com.sorryisme.fmarket.dto.response.OrderListResponseDto;
import com.sorryisme.fmarket.dto.response.OrderResponseDto;
import com.sorryisme.fmarket.entity.Inventory;
import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.entity.OrderDetail;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.exception.NotFoundDataException;
import com.sorryisme.fmarket.repository.InventoryRepository;
import com.sorryisme.fmarket.repository.OrderRepository;
import com.sorryisme.fmarket.repository.ProductOptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final InventoryRepository inventoryRepository;
  private final ProductOptionRepository productOptionRepository;

  @Transactional(readOnly = true)
  public Page<OrderListResponseDto> findAllOrderList(OrderSearchDto orderSearchDto) {
    Pageable pageable = PageableSupport.withStableSort(orderSearchDto.getPageable());
    Long userId = orderSearchDto.getUserId();

    Page<Order> orders;
    if (hasPeriod(orderSearchDto)) {
      // endPeriod 당일 주문까지 포함되도록 다음날 0시 미만으로 조회한다.
      LocalDateTime from = LocalDate.parse(orderSearchDto.getStartPeriod()).atStartOfDay();
      LocalDateTime to = LocalDate.parse(orderSearchDto.getEndPeriod()).plusDays(1).atStartOfDay();
      orders = orderRepository.findByUserIdAndOrderDateIn(userId, from, to, pageable);
    } else {
      orders = orderRepository.findByUserId(userId, pageable);
    }

    return orders.map(OrderListResponseDto::from);
  }

  @Transactional(readOnly = true)
  public OrderResponseDto findOneOrder(Long id) {
    Order order =
        orderRepository
            .findWithDetailsById(id)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 주문입니다."));

    return OrderResponseDto.from(order);
  }

  @Transactional
  public Long confirmOrder(Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 주문입니다."));

    order.changeStatus(OrderStatus.COMPLETED);
    return orderId;
  }

  @Transactional
  public Long cancelOrder(Long orderId) {

    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 주문입니다."));

    if (order.getStatus() != OrderStatus.PENDING)
      throw new IllegalArgumentException("변경이 불가한 상태입니다");

    // 같은 옵션이 여러 상세에 걸쳐 있으면 수량을 합쳐 되돌린다.
    Map<Long, Integer> restoreQuantityMap =
        order.getOrderDetails().stream()
            .collect(
                Collectors.toMap(
                    OrderDetail::getProductOptionId, OrderDetail::getQuantity, Integer::sum));

    inventoryRepository
        .findAllByProductOptionIdInForUpdate(restoreQuantityMap.keySet())
        .forEach(
            inventory ->
                inventory.increase(restoreQuantityMap.get(inventory.getProductOptionId())));

    order.changeStatus(OrderStatus.CANCELLED);
    return orderId;
  }

  @Idempotent
  @Transactional
  public Long createOrder(
      @IdempotencyKeyParam String idempotencyKey, Long userId, OrderCreateDto orderCreateDto) {

    // 1. 판매중 옵션만 조회. 하나라도 빠지면(없음·판매중지·삭제) 주문을 거절한다.
    Map<Long, Integer> requestQuantityMap = orderCreateDto.toProductQuantityMap();
    List<ProductOption> productOptions =
        productOptionRepository.findAllByIdInAndStatus(
            requestQuantityMap.keySet(), ProductStatus.ON_SALE);
    if (productOptions.size() != requestQuantityMap.size())
      throw new IllegalArgumentException("판매 중이 아닌 상품 옵션이 포함되어 있습니다.");

    // 2. 주문 생성 (주문 상세는 cascade 로 함께 저장)
    Order order = Order.of(productOptions, requestQuantityMap, userId);
    orderRepository.save(order);

    // 3. 재고 행 잠금 후 수량 검증·차감
    Map<Long, Inventory> inventoryMap =
        inventoryRepository
            .findAllByProductOptionIdInForUpdate(requestQuantityMap.keySet())
            .stream()
            .collect(Collectors.toMap(Inventory::getProductOptionId, Function.identity()));

    requestQuantityMap.forEach(
        (productOptionId, quantity) -> {
          Inventory inventory = inventoryMap.get(productOptionId);
          if (inventory == null) throw new IllegalArgumentException("재고 수량이 충분하지 않습니다.");
          inventory.decrease(quantity);
        });

    return order.getId();
  }

  private static boolean hasPeriod(OrderSearchDto orderSearchDto) {
    return orderSearchDto.getStartPeriod() != null
        && !orderSearchDto.getStartPeriod().isEmpty()
        && orderSearchDto.getEndPeriod() != null
        && !orderSearchDto.getEndPeriod().isEmpty();
  }
}
