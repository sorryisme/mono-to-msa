package com.sorryisme.fmarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.dto.request.OrderCreateDto;
import com.sorryisme.fmarket.dto.request.OrderSearchDto;
import com.sorryisme.fmarket.dto.response.OrderListResponseDto;
import com.sorryisme.fmarket.dto.response.OrderResponseDto;
import com.sorryisme.fmarket.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * OrderController 의 라우팅·바인딩·서비스 위임을 본다.
 *
 * <p>{@code @RequireLogin} AOP 는 슬라이스에 포함되지 않으므로 여기서 검증되는 인증 경로는 {@code @LoginUserId} 리졸버까지다.
 */
@WebMvcTest(controllers = OrderController.class)
class OrderControllerTest {

  private static final String VALID_KEY = "550e8400-e29b-41d4-a716-446655440000";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderService orderService;
  @MockitoBean private SessionManager sessionManager;

  @Test
  @DisplayName("주문 목록 조회는 검색 조건에 로그인 유저 ID 와 페이지 정보를 채워 서비스에 넘긴다")
  void fillsUserIdAndPageableIntoSearchCondition() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);
    when(orderService.findAllOrderList(any(OrderSearchDto.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(OrderListResponseDto.builder().id(1L).status("PAID").build()),
                PageRequest.of(0, 20),
                1));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startPeriod\":\"2026-01-01\",\"endPeriod\":\"2026-01-31\"}"))
            .andReturn();

    ArgumentCaptor<OrderSearchDto> captor = ArgumentCaptor.forClass(OrderSearchDto.class);
    verify(orderService).findAllOrderList(captor.capture());
    // 컨트롤러가 OrderSearchDto.from 으로 조립하는 값들이다. 여기서 userId 가 빠지면
    // 남의 주문까지 조회된다.
    assertThat(captor.getValue().getUserId()).isEqualTo(42L);
    assertThat(captor.getValue().getStartPeriod()).isEqualTo("2026-01-01");
    assertThat(captor.getValue().getEndPeriod()).isEqualTo("2026-01-31");
    assertThat(captor.getValue().getPageable()).isNotNull();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("주문 취소는 경로 변수 id 를 서비스에 넘긴다")
  void cancelsOrderByPathVariable() throws Exception {
    when(orderService.cancelOrder(5L)).thenReturn(5L);

    MvcResult result = mockMvc.perform(put("/api/v1/orders/5/cancel")).andReturn();

    verify(orderService).cancelOrder(5L);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("\"data\":5");
  }

  @Test
  @DisplayName("구매 확정은 경로 변수 id 를 서비스에 넘긴다")
  void confirmsOrderByPathVariable() throws Exception {
    when(orderService.confirmOrder(5L)).thenReturn(5L);

    MvcResult result = mockMvc.perform(put("/api/v1/orders/5/confirm")).andReturn();

    verify(orderService).confirmOrder(5L);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("주문 단건 조회는 경로 변수 id 로 조회한 결과를 봉투에 담아 응답한다")
  void findsOneOrderByPathVariable() throws Exception {
    when(orderService.findOneOrder(5L))
        .thenReturn(OrderResponseDto.builder().id(5L).userId(42L).status("PAID").build());

    MvcResult result = mockMvc.perform(get("/api/v1/orders/5")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"PAID\"");
  }

  @Test
  @DisplayName("주문 생성은 Idempotency-Key 헤더와 로그인 유저 ID 를 서비스에 넘기고 201 로 응답한다")
  void passesIdempotencyKeyAndUserIdOnCreate() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);
    when(orderService.createOrder(eq(VALID_KEY), eq(42L), any(OrderCreateDto.class)))
        .thenReturn(100L);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/orders/create")
                    .header("Idempotency-Key", VALID_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderItems\":[{\"productOptionId\":1000,\"quantity\":2}]}"))
            .andReturn();

    ArgumentCaptor<OrderCreateDto> captor = ArgumentCaptor.forClass(OrderCreateDto.class);
    verify(orderService).createOrder(eq(VALID_KEY), eq(42L), captor.capture());
    assertThat(captor.getValue().toProductQuantityMap()).containsEntry(1000L, 2);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DisplayName("Idempotency-Key 헤더가 없으면 주문 생성 요청을 서비스까지 보내지 않는다")
  void rejectsCreateWithoutIdempotencyKeyHeader() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/orders/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderItems\":[{\"productOptionId\":1000,\"quantity\":2}]}"))
            .andReturn();

    // 키 값 자체의 유효성은 IdempotencyAspect 가 본다. 여기서 고정하는 것은 헤더가 아예
    // 없을 때 요청이 컨트롤러를 통과하지 못한다는 것이다.
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    verifyNoInteractions(orderService);
  }
}
