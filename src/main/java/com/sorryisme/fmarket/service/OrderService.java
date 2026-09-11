package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.annotation.IdempotencyKeyParam;
import com.sorryisme.fmarket.annotation.Idempotent;
import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.PageableSupport;
import com.sorryisme.fmarket.dto.request.OrderCreateDto;
import com.sorryisme.fmarket.dto.request.OrderSearchDto;
import com.sorryisme.fmarket.dto.response.OrderListResponseDto;
import com.sorryisme.fmarket.dto.response.OrderResponseDto;
import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.entity.OrderDetail;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.InventoryRepository;
import com.sorryisme.fmarket.repository.OrderRepository;
import com.sorryisme.fmarket.repository.ProductOptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    return OrderResponseDto.from(order);
  }

  /**
   * 주문 확정. PENDING 인 주문만 COMPLETED 로 바꾼다.
   *
   * <p>결제가 붙으면 이 전이는 "결제 성공 콜백" 이 호출하게 된다. 그때도 조건부 UPDATE 이므로 PG 재전송으로 콜백이 두 번 와도 한 번만 확정된다.
   */
  @Transactional
  public Long confirmOrder(Long orderId) {
    if (!orderRepository.existsById(orderId))
      throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);

    int updated =
        orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING, OrderStatus.COMPLETED);
    if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_CHANGEABLE);

    return orderId;
  }

  /**
   * 주문 취소. 상태를 PENDING -> CANCELLED 로 조건부 전이시키고, 전이에 성공한 요청만 재고를 되돌린다.
   *
   * <p>상태 전이를 먼저 하는 이유는 재고 이중 복구를 막기 위해서다. 사용자 취소와 (추후) 결제 실패 처리·결제 만료 배치가 동시에 들어와도 UPDATE 에 성공하는
   * 요청은 하나뿐이므로 복구도 한 번만 일어난다.
   */
  @Transactional
  public Long cancelOrder(Long orderId) {

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    // 같은 옵션이 여러 상세에 걸쳐 있으면 수량을 합쳐 되돌린다.
    Map<Long, Integer> restoreQuantityMap =
        order.getOrderDetails().stream()
            .collect(
                Collectors.toMap(
                    OrderDetail::getProductOptionId, OrderDetail::getQuantity, Integer::sum));

    int updated =
        orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED);
    if (updated == 0) throw new BusinessException(ErrorCode.ORDER_STATUS_NOT_CHANGEABLE);

    restoreStock(orderId, restoreQuantityMap);

    return orderId;
  }

  /**
   * 주문 생성과 재고 확보.
   *
   * <p>결제를 붙일 때는 이 트랜잭션 안에서 PG 를 호출하면 안 된다. 외부 호출이 수 초 걸리는 동안 재고 행 잠금과 DB 커넥션을 함께 붙들게 되어, 결제 장애가
   * 커넥션 고갈로 번진다. 권장 흐름은 다음과 같다.
   *
   * <ol>
   *   <li>(이 메서드) 멱등성 키 확인 -> 조건부 재고 차감 -> 주문 저장 -> 커밋. 여기서 차감한 재고는 "판매 확정" 이 아니라 결제가 끝날 때까지의 예약으로
   *       본다.
   *   <li>트랜잭션 밖에서 PG 결제 요청. 주문 ID 기반 멱등성 키를 써서 타임아웃 재시도로 중복 결제가 되지 않게 한다.
   *   <li>결제 성공 -> {@link #confirmOrder(Long)}, 실패 -> {@link #cancelOrder(Long)} 로 조건부 상태 전이. 전이에
   *       성공한 요청만 재고를 복구한다.
   * </ol>
   *
   * <p>아직 결제 모듈이 없어 2·3 단계와, 일정 시간 결제가 끝나지 않은 주문을 만료시키는 배치·PG 대사 재처리는 구현하지 않았다. 상태 역시 PENDING 하나로
   * 두었다(DB enum 이 PENDING/COMPLETED/CANCELLED). 결제를 붙이는 시점에는 PENDING 의 의미가 "주문 생성됨/재고 예약됨/결제 진행 중"
   * 중 무엇인지 모호해지므로 PAYMENT_PENDING 같은 상태를 분리해야 한다.
   */
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
      throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_ON_SALE);

    // 2. 재고 확보. 검사와 차감을 DB 의 한 UPDATE 로 묶는다.
    //    옵션 ID 오름차순으로 차감해 서로 다른 주문이 같은 옵션들을 반대 순서로 잠그는 데드락을 피한다.
    requestQuantityMap.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              int affected = inventoryRepository.decreaseQuantity(entry.getKey(), entry.getValue());
              // 0 이면 재고 부족이거나 재고 행 자체가 없다. 어느 쪽이든 주문을 받을 수 없으므로 전체 롤백한다.
              if (affected == 0) throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            });

    // 3. 주문 생성 (주문 상세는 cascade 로 함께 저장)
    Order order = Order.of(productOptions, requestQuantityMap, userId);
    orderRepository.save(order);

    return order.getId();
  }

  /** 취소된 주문의 재고를 되돌린다. 재고 행이 사라진 옵션은 되돌릴 대상이 없으므로 취소 자체를 막지 않고 경고만 남긴다. */
  private void restoreStock(Long orderId, Map<Long, Integer> restoreQuantityMap) {
    restoreQuantityMap.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              int affected = inventoryRepository.increaseQuantity(entry.getKey(), entry.getValue());
              if (affected == 0)
                log.warn(
                    "재고 복구 대상 행이 없습니다. orderId={}, productOptionId={}, quantity={}",
                    orderId,
                    entry.getKey(),
                    entry.getValue());
            });
  }

  private static boolean hasPeriod(OrderSearchDto orderSearchDto) {
    return orderSearchDto.getStartPeriod() != null
        && !orderSearchDto.getStartPeriod().isEmpty()
        && orderSearchDto.getEndPeriod() != null
        && !orderSearchDto.getEndPeriod().isEmpty();
  }
}
