package com.sorryisme.fmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.dto.request.OrderCreateDto;
import com.sorryisme.fmarket.dto.request.OrderItemRequestDto;
import com.sorryisme.fmarket.dto.request.OrderSearchDto;
import com.sorryisme.fmarket.dto.response.OrderListResponseDto;
import com.sorryisme.fmarket.dto.response.OrderResponseDto;
import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.enums.OrderStatus;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.InventoryRepository;
import com.sorryisme.fmarket.repository.OrderRepository;
import com.sorryisme.fmarket.repository.ProductOptionRepository;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class OrderServiceTest {

  private static final String UUID = "166f9067-2e5f-4932-e314-f438ae846d24";

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
  private final ProductOptionRepository productOptionRepository =
      mock(ProductOptionRepository.class);
  private final OrderService orderService =
      new OrderService(orderRepository, inventoryRepository, productOptionRepository);

  private List<ProductOption> productOptions;

  @BeforeEach
  void setUp() {
    productOptions =
        List.of(
            DomainFixture.createProductOption(1L, 101L, "Option1", "900.00"),
            DomainFixture.createProductOption(2L, 102L, "Option2", "1800.00"));
  }

  static Stream<Arguments> periodConditions() {
    return Stream.of(
        Arguments.of("2025-01-01", "2025-12-31", 1, 0),
        Arguments.of(null, null, 0, 1),
        Arguments.of("", "", 0, 1));
  }

  @ParameterizedTest(name = "startPeriod={0}, endPeriod={1}")
  @MethodSource("periodConditions")
  @DisplayName("기간 조건이 있으면 기간 조회 메서드로, 없으면 사용자 기준으로 페이징 조회한다")
  void routesByPeriodCondition(
      String startPeriod, String endPeriod, int periodCalls, int plainCalls) {
    OrderSearchDto orderSearchDto = createOrderSearchDto(startPeriod, endPeriod);
    Page<Order> page = new PageImpl<>(List.of(DomainFixture.createOrder(1L, OrderStatus.PENDING)));
    when(orderRepository.findByUserIdAndOrderDateIn(
            eq(1L),
            eq(LocalDateTime.of(2025, 1, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 1, 1, 0, 0)),
            any(Pageable.class)))
        .thenReturn(page);
    when(orderRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

    Page<OrderListResponseDto> result = orderService.findAllOrderList(orderSearchDto);

    verify(orderRepository, times(periodCalls))
        .findByUserIdAndOrderDateIn(
            eq(1L),
            eq(LocalDateTime.of(2025, 1, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 1, 1, 0, 0)),
            any(Pageable.class));
    verify(orderRepository, times(plainCalls)).findByUserId(eq(1L), any(Pageable.class));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("ID로 주문 조회 시 존재하면 상세를 포함한 데이터를 반환한다")
  void returnsOrderWithDetails() {
    when(orderRepository.findWithDetailsById(1L))
        .thenReturn(Optional.of(DomainFixture.createOrder(1L, OrderStatus.PENDING)));

    OrderResponseDto result = orderService.findOneOrder(1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getUserId()).isEqualTo(1L);
    assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal(10000));
    assertThat(result.getOrderDetails()).hasSize(1);
  }

  @Test
  @DisplayName("주문 조회시 주문이 존재하지 않으면 예외가 발생한다")
  void throwsWhenOrderMissing() {
    when(orderRepository.findWithDetailsById(anyLong())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.findOneOrder(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  @DisplayName("주문 확정은 PENDING 인 주문만 COMPLETED 로 전이시킨다")
  void confirmsPendingOrder() {
    when(orderRepository.existsById(1L)).thenReturn(true);
    when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.COMPLETED))
        .thenReturn(1);

    Long result = orderService.confirmOrder(1L);

    verify(orderRepository).updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.COMPLETED);
    assertThat(result).isEqualTo(1L);
  }

  @Test
  @DisplayName("주문 확정 시 주문이 없을 경우 에러가 발생한다")
  void throwsWhenConfirmingMissingOrder() {
    when(orderRepository.existsById(anyLong())).thenReturn(false);

    assertThatThrownBy(() -> orderService.confirmOrder(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    verify(orderRepository, never())
        .updateStatusIfCurrent(anyLong(), any(OrderStatus.class), any(OrderStatus.class));
  }

  @Test
  @DisplayName("주문 확정 시 이미 상태가 바뀌어 전이에 실패하면 에러가 발생한다")
  void throwsWhenConfirmTransitionFails() {
    when(orderRepository.existsById(1L)).thenReturn(true);
    when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.COMPLETED))
        .thenReturn(0);

    assertThatThrownBy(() -> orderService.confirmOrder(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ORDER_STATUS_NOT_CHANGEABLE);
  }

  @Test
  @DisplayName("주문취소 시 상태가 CANCELLED 로 전이되고 재고가 복구된다")
  void cancelsOrderAndRestoresStock() {
    when(orderRepository.findWithDetailsById(1L))
        .thenReturn(Optional.of(DomainFixture.createOrder(1L, OrderStatus.PENDING)));
    when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.CANCELLED))
        .thenReturn(1);
    when(inventoryRepository.increaseQuantity(1L, 5)).thenReturn(1);

    Long result = orderService.cancelOrder(1L);

    verify(inventoryRepository).increaseQuantity(1L, 5);
    assertThat(result).isEqualTo(1L);
  }

  @Test
  @DisplayName("주문취소 시 주문이 없을 경우 에러를 발생시킨다")
  void throwsWhenCancellingMissingOrder() {
    when(orderRepository.findWithDetailsById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  @DisplayName("주문취소 시 상태 전이에 실패하면 에러가 발생하고 재고를 복구하지 않는다")
  void doesNotRestoreStockWhenCancelTransitionFails() {
    when(orderRepository.findWithDetailsById(1L))
        .thenReturn(Optional.of(DomainFixture.createOrder(1L, OrderStatus.COMPLETED)));
    when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.CANCELLED))
        .thenReturn(0);

    assertThatThrownBy(() -> orderService.cancelOrder(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ORDER_STATUS_NOT_CHANGEABLE);
    verifyNoInteractions(inventoryRepository);
  }

  @Test
  @DisplayName("주문취소 시 재고 행이 없어도 취소는 성공한다")
  void cancelSucceedsWithoutInventoryRow() {
    when(orderRepository.findWithDetailsById(1L))
        .thenReturn(Optional.of(DomainFixture.createOrder(1L, OrderStatus.PENDING)));
    when(orderRepository.updateStatusIfCurrent(1L, OrderStatus.PENDING, OrderStatus.CANCELLED))
        .thenReturn(1);
    when(inventoryRepository.increaseQuantity(1L, 5)).thenReturn(0);

    assertThat(orderService.cancelOrder(1L)).isEqualTo(1L);
  }

  @Test
  @DisplayName("createOrder는 옵션 ID 오름차순으로 재고를 차감하고 주문을 저장한다")
  void decreasesStockInOptionIdOrder() {
    OrderCreateDto orderCreateDto =
        new OrderCreateDto(List.of(new OrderItemRequestDto(2L, 2), new OrderItemRequestDto(1L, 1)));
    when(productOptionRepository.findAllByIdInAndStatus(any(), eq(ProductStatus.ON_SALE)))
        .thenReturn(productOptions);
    when(inventoryRepository.decreaseQuantity(anyLong(), anyInt())).thenReturn(1);

    orderService.createOrder(UUID, 1L, orderCreateDto);

    // 요청 순서와 무관하게 옵션 ID 오름차순으로 차감해야 데드락을 피할 수 있다.
    InOrder inOrder = inOrder(inventoryRepository);
    inOrder.verify(inventoryRepository).decreaseQuantity(1L, 1);
    inOrder.verify(inventoryRepository).decreaseQuantity(2L, 2);

    BigDecimal expectedTotal =
        new BigDecimal("900.00").add(new BigDecimal("1800.00").multiply(BigDecimal.valueOf(2)));
    verify(orderRepository)
        .save(
            argThat(
                (Order order) ->
                    order.getOrderDetails().size() == 2
                        && order.getStatus() == OrderStatus.PENDING
                        && order.getTotalAmount().compareTo(expectedTotal) == 0));
  }

  @Test
  @DisplayName("createOrder는 재고 차감이 0행이면 예외를 발생시키고 주문을 저장하지 않는다")
  void throwsWhenStockDecreaseAffectsNoRow() {
    OrderCreateDto orderCreateDto =
        new OrderCreateDto(List.of(new OrderItemRequestDto(1L, 3), new OrderItemRequestDto(2L, 2)));
    when(productOptionRepository.findAllByIdInAndStatus(any(), eq(ProductStatus.ON_SALE)))
        .thenReturn(productOptions);
    when(inventoryRepository.decreaseQuantity(1L, 3)).thenReturn(0);

    assertThatThrownBy(() -> orderService.createOrder(UUID, 1L, orderCreateDto))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OUT_OF_STOCK);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("createOrder는 판매중이 아닌 옵션이 섞여 있으면 주문을 저장하지 않고 거절한다")
  void rejectsOptionsNotOnSale() {
    OrderCreateDto orderCreateDto =
        new OrderCreateDto(
            List.of(new OrderItemRequestDto(1L, 1), new OrderItemRequestDto(99L, 1)));
    when(productOptionRepository.findAllByIdInAndStatus(any(), eq(ProductStatus.ON_SALE)))
        .thenReturn(List.of(productOptions.get(0)));

    assertThatThrownBy(() -> orderService.createOrder(UUID, 1L, orderCreateDto))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_ON_SALE);
    verify(orderRepository, never()).save(any(Order.class));
    verifyNoInteractions(inventoryRepository);
  }

  @Test
  @DisplayName("createOrder는 재고 행이 없는 옵션이면 예외를 발생시킨다")
  void throwsWhenInventoryRowMissing() {
    OrderCreateDto orderCreateDto = new OrderCreateDto(List.of(new OrderItemRequestDto(1L, 1)));
    when(productOptionRepository.findAllByIdInAndStatus(any(), eq(ProductStatus.ON_SALE)))
        .thenReturn(List.of(productOptions.get(0)));
    when(inventoryRepository.decreaseQuantity(1L, 1)).thenReturn(0);

    assertThatThrownBy(() -> orderService.createOrder(UUID, 1L, orderCreateDto))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OUT_OF_STOCK);
  }

  private static OrderSearchDto createOrderSearchDto(String startPeriod, String endPeriod) {
    return OrderSearchDto.builder()
        .userId(1L)
        .startPeriod(startPeriod)
        .endPeriod(endPeriod)
        .pageable(Pageable.ofSize(10))
        .build();
  }
}
