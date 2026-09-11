package com.sorryisme.fmarket.aop;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@RequiredArgsConstructor
@Component
public class AuthenticationAspect {

  private final SessionManager sessionManager;

  @Before("@annotation(com.sorryisme.fmarket.annotation.RequireLogin)")
  public boolean authenticate() {

    Long loginId = sessionManager.getUserId();

    if (loginId == null) {
      throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
    }

    return true;
  }
}
