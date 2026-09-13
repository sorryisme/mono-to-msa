package com.sorryisme.fmarket.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

  private static final String LOGIN_ID = "LOGIN_ID";

  private final HttpSession session = mock(HttpSession.class);
  private final SessionManager sessionManager = new SessionManager(session);

  @Test
  @DisplayName("세션에 저장된 로그인 ID 를 돌려준다")
  void returnsStoredUserId() {
    when(session.getAttribute(LOGIN_ID)).thenReturn(7L);

    assertThat(sessionManager.getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("세션에 로그인 ID 가 없으면 null 을 돌려준다")
  void returnsNullWhenNotLoggedIn() {
    when(session.getAttribute(LOGIN_ID)).thenReturn(null);

    // AuthenticationAspect 는 이 null 로 비로그인을 판정한다. 다른 값을 돌려주기 시작하면
    // 인증 검사가 통째로 무력화된다.
    assertThat(sessionManager.getUserId()).isNull();
  }

  @Test
  @DisplayName("로그인 ID 를 LOGIN_ID 키로 세션에 저장한다")
  void storesUserIdUnderLoginIdKey() {
    sessionManager.setLoginUserId(7L);

    verify(session).setAttribute(LOGIN_ID, 7L);
  }

  @Test
  @DisplayName("로그아웃하면 LOGIN_ID 키를 세션에서 지운다")
  void removesUserIdOnLogout() {
    sessionManager.removeLoginUserId();

    verify(session).removeAttribute(LOGIN_ID);
  }
}
