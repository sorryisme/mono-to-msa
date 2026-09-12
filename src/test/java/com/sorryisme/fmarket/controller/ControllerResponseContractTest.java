package com.sorryisme.fmarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.dto.request.CartRequestDto;
import com.sorryisme.fmarket.dto.request.UserRequestDto;
import com.sorryisme.fmarket.dto.response.CartResponseDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.UserResponseDto;
import com.sorryisme.fmarket.service.CartService;
import com.sorryisme.fmarket.service.OrderService;
import com.sorryisme.fmarket.service.ProductService;
import com.sorryisme.fmarket.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/API_RESPONSE.md 의 성공 응답 계약(봉투 형식·생성 201)과 로그인 필요 401 을 컨트롤러 슬라이스에서 고정한다. 서비스는 Mock 이고,
 * {@code @RequireLogin} AOP 는 슬라이스에 포함되지 않으므로 {@code @LoginUserId} 리졸버 경로만 검증한다.
 */
@WebMvcTest(
    controllers = {
      UserController.class,
      CartController.class,
      ProductController.class,
      OrderController.class
    })
class ControllerResponseContractTest {

  private static final JsonMapper MAPPER = new JsonMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private CartService cartService;
  @MockitoBean private ProductService productService;
  @MockitoBean private OrderService orderService;
  @MockitoBean private SessionManager sessionManager;

  @Test
  @DisplayName("회원 가입은 201 Created 와 OK 봉투로 응답한다")
  void signupRespondsWith201() throws Exception {
    when(userService.createUser(any(UserRequestDto.class)))
        .thenReturn(UserResponseDto.builder().id(1L).loginId("tester").build());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/user/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"loginId\":\"tester\",\"password\":\"1234\",\"name\":\"테스터\","
                            + "\"email\":\"t@t.com\",\"phoneNumber\":\"010-0000-0000\"}"))
            .andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(body.get("code")).isEqualTo("OK");
    assertThat(data(body).get("loginId")).isEqualTo("tester");
  }

  @Test
  @DisplayName("로그인한 유저의 장바구니 담기는 201 Created 로 응답한다")
  void addCartRespondsWith201() throws Exception {
    when(sessionManager.getUserId()).thenReturn(1L);
    when(cartService.addCart(any(CartRequestDto.class), eq(1L)))
        .thenReturn(
            CartResponseDto.builder().id(10L).cartId(1L).productOptionId(1L).quantity(2).build());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/cart/add")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productOptionId\":1,\"quantity\":2}"))
            .andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(body.get("code")).isEqualTo("OK");
    assertThat(data(body).get("id")).isEqualTo(10);
  }

  @Test
  @DisplayName("세션이 없는 @LoginUserId 요청은 401 LOGIN_REQUIRED 로 응답한다")
  void respondsWith401WhenSessionMissing() throws Exception {
    when(sessionManager.getUserId()).thenReturn(null);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/cart/add")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productOptionId\":1,\"quantity\":2}"))
            .andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(body.get("code")).isEqualTo("LOGIN_REQUIRED");
    verifyNoInteractions(cartService);
  }

  @Test
  @DisplayName("카테고리 목록 조회도 다른 응답과 같은 봉투로 감싼다")
  void wrapsCategoryListInSameEnvelope() throws Exception {
    when(productService.findMajorCategoryList())
        .thenReturn(
            List.of(
                MajorCategoryResponse.builder()
                    .majorCategoryId(1L)
                    .majorCategoryName("식품")
                    .build()));

    MvcResult result = mockMvc.perform(get("/api/v1/products/category")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body.get("code")).isEqualTo("OK");
    assertThat(dataList(body)).extracting("majorCategoryName").containsExactly("식품");
  }

  @Test
  @DisplayName("조회 성공은 200 OK 로 응답한다")
  void respondsWith200OnRead() throws Exception {
    when(productService.findMajorCategoryList()).thenReturn(List.of());

    MvcResult result = mockMvc.perform(get("/api/v1/products/category")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> json(MvcResult result) throws Exception {
    return MAPPER.readValue(
        result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> dataList(Map<String, Object> body) {
    return (List<Map<String, Object>>) body.get("data");
  }
}
