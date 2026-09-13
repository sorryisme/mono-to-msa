package com.sorryisme.fmarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.dto.request.CartRequestDto;
import com.sorryisme.fmarket.dto.response.CartResponseDto;
import com.sorryisme.fmarket.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** CartController 의 라우팅·바인딩·서비스 위임을 본다. 봉투 형식 계약은 ControllerResponseContractTest 가 따로 고정한다. */
@WebMvcTest(controllers = CartController.class)
class CartControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CartService cartService;
  @MockitoBean private SessionManager sessionManager;

  @Test
  @DisplayName("장바구니 담기는 요청 본문과 로그인 유저 ID 를 그대로 서비스에 넘긴다")
  void passesBodyAndLoginUserIdToService() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);
    when(cartService.addCart(any(CartRequestDto.class), eq(42L)))
        .thenReturn(
            CartResponseDto.builder()
                .id(10L)
                .cartId(1L)
                .productOptionId(1000L)
                .quantity(3)
                .build());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/cart/add")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productOptionId\":1000,\"quantity\":3}"))
            .andReturn();

    ArgumentCaptor<CartRequestDto> captor = ArgumentCaptor.forClass(CartRequestDto.class);
    verify(cartService).addCart(captor.capture(), eq(42L));
    assertThat(captor.getValue().getProductOptionId()).isEqualTo(1000L);
    assertThat(captor.getValue().getQuantity()).isEqualTo(3);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DisplayName("장바구니 삭제는 경로 변수 id 를 서비스에 넘기고 삭제한 id 를 200 으로 응답한다")
  void deletesCartDetailByPathVariable() throws Exception {
    when(cartService.deleteCartDetail(7L)).thenReturn(7L);

    MvcResult result = mockMvc.perform(delete("/api/v1/cart/7")).andReturn();

    verify(cartService).deleteCartDetail(7L);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("\"data\":7");
  }
}
