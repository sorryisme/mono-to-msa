package com.sorryisme.fmarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.dto.request.SellerRequestDto;
import com.sorryisme.fmarket.dto.request.UserRequestDto;
import com.sorryisme.fmarket.dto.request.UserUpdateRequestDto;
import com.sorryisme.fmarket.dto.response.SellerResponseDto;
import com.sorryisme.fmarket.dto.response.UserResponseDto;
import com.sorryisme.fmarket.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** UserController 의 라우팅·바인딩·서비스 위임과 로그인 성공 시 세션 저장을 본다. */
@WebMvcTest(controllers = UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private SessionManager sessionManager;

  @Test
  @DisplayName("회원 가입은 요청 본문을 그대로 서비스에 넘기고 201 로 응답한다")
  void passesSignupBodyToService() throws Exception {
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

    ArgumentCaptor<UserRequestDto> captor = ArgumentCaptor.forClass(UserRequestDto.class);
    verify(userService).createUser(captor.capture());
    assertThat(captor.getValue().getLoginId()).isEqualTo("tester");
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DisplayName("회원 정보 수정은 요청 본문과 로그인 유저 ID 를 서비스에 넘긴다")
  void passesUpdateBodyAndLoginUserId() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);
    when(userService.updateUser(any(UserUpdateRequestDto.class), eq(42L))).thenReturn(42L);

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/user/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"테스터\",\"email\":\"t@t.com\",\"phoneNumber\":\"010-0000-0000\"}"))
            .andReturn();

    ArgumentCaptor<UserUpdateRequestDto> captor =
        ArgumentCaptor.forClass(UserUpdateRequestDto.class);
    verify(userService).updateUser(captor.capture(), eq(42L));
    assertThat(captor.getValue().getName()).isEqualTo("테스터");
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("판매자 가입은 상점 정보까지 담은 본문을 서비스에 넘기고 201 로 응답한다")
  void passesSellerSignupBodyToService() throws Exception {
    when(userService.createSeller(any(SellerRequestDto.class)))
        .thenReturn(SellerResponseDto.builder().id(1L).storeName("과일가게").build());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/seller/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"loginId\":\"seller\",\"password\":\"1234\",\"name\":\"판매자\","
                            + "\"email\":\"s@s.com\",\"phoneNumber\":\"010-1111-1111\","
                            + "\"storeName\":\"과일가게\",\"businessNumber\":\"123-45-67890\"}"))
            .andReturn();

    ArgumentCaptor<SellerRequestDto> captor = ArgumentCaptor.forClass(SellerRequestDto.class);
    verify(userService).createSeller(captor.capture());
    assertThat(captor.getValue().getStoreName()).isEqualTo("과일가게");
    assertThat(captor.getValue().getBusinessNumber()).isEqualTo("123-45-67890");
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DisplayName("로그인에 성공하면 세션에 유저 ID 를 저장한다")
  void storesUserIdInSessionOnLogin() throws Exception {
    when(userService.login("tester", "1234")).thenReturn(42L);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loginId\":\"tester\",\"password\":\"1234\"}"))
            .andReturn();

    // 이 호출이 빠지면 로그인 응답은 200 이지만 이후 요청은 전부 비로그인으로 취급된다.
    verify(sessionManager).setLoginUserId(42L);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("\"data\":42");
  }

  @Test
  @DisplayName("이메일 형식이 잘못된 회원 가입은 서비스까지 가지 않는다")
  void rejectsInvalidSignupBeforeService() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/user/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"loginId\":\"tester\",\"password\":\"1234\",\"name\":\"테스터\","
                            + "\"email\":\"not-an-email\",\"phoneNumber\":\"010-0000-0000\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    verifyNoInteractions(userService);
  }
}
