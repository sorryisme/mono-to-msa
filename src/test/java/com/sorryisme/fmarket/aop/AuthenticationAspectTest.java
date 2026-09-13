package com.sorryisme.fmarket.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticationAspectTest {

  private final SessionManager sessionManager = mock(SessionManager.class);
  private final AuthenticationAspect authenticationAspect =
      new AuthenticationAspect(sessionManager);

  @Test
  @DisplayName("세션에 로그인 ID 가 있으면 통과한다")
  void passesWhenLoggedIn() {
    when(sessionManager.getUserId()).thenReturn(1L);

    assertThat(authenticationAspect.authenticate()).isTrue();
  }

  @Test
  @DisplayName("세션에 로그인 ID 가 없으면 LOGIN_REQUIRED 로 막는다")
  void throwsWhenNotLoggedIn() {
    when(sessionManager.getUserId()).thenReturn(null);

    assertThatThrownBy(authenticationAspect::authenticate)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_REQUIRED);
  }
}
