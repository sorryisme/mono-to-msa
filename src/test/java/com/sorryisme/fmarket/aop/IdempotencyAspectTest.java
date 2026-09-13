package com.sorryisme.fmarket.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.annotation.IdempotencyKeyParam;
import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.entity.IdempotencyKey;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.IdempotencyKeyRepository;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class IdempotencyAspectTest {

  private static final String VALID_KEY = "550e8400-e29b-41d4-a716-446655440000";

  private final IdempotencyKeyRepository idempotencyKeyRepository =
      mock(IdempotencyKeyRepository.class);
  private final IdempotencyAspect idempotencyAspect =
      new IdempotencyAspect(idempotencyKeyRepository);

  @Test
  @DisplayName("정상 키면 키를 저장하고 원래 메서드를 실행한다")
  void savesKeyAndProceeds() throws Throwable {
    ProceedingJoinPoint joinPoint = joinPointFor("withKey", VALID_KEY, 1L);
    when(joinPoint.proceed()).thenReturn("주문 결과");

    Object result = idempotencyAspect.handleIdempotency(joinPoint);

    ArgumentCaptor<IdempotencyKey> saved = ArgumentCaptor.forClass(IdempotencyKey.class);
    verify(idempotencyKeyRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(VALID_KEY);
    assertThat(result).isEqualTo("주문 결과");
  }

  @Test
  @DisplayName("키 파라미터가 없으면 IDEMPOTENCY_KEY_INVALID 로 막는다")
  void throwsWhenKeyParamMissing() throws Throwable {
    ProceedingJoinPoint joinPoint = joinPointFor("withoutKey", 1L);

    assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID);

    verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    verify(joinPoint, never()).proceed();
  }

  @Test
  @DisplayName("키 길이가 36자가 아니면 저장도 실행도 하지 않는다")
  void throwsWhenKeyLengthInvalid() throws Throwable {
    ProceedingJoinPoint joinPoint = joinPointFor("withKey", "too-short", 1L);

    assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID);

    verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    verify(joinPoint, never()).proceed();
  }

  @Test
  @DisplayName("키 파라미터가 String 이 아니면 잘못된 사용으로 보고 IllegalArgumentException 을 던진다")
  void throwsWhenKeyParamNotString() {
    ProceedingJoinPoint joinPoint = joinPointFor("withNonStringKey", 1L);

    assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("같은 키가 이미 저장돼 있으면 DUPLICATE_REQUEST 로 막고 원래 메서드를 실행하지 않는다")
  void throwsWhenKeyDuplicated() throws Throwable {
    ProceedingJoinPoint joinPoint = joinPointFor("withKey", VALID_KEY, 1L);
    when(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKey.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThatThrownBy(() -> idempotencyAspect.handleIdempotency(joinPoint))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.DUPLICATE_REQUEST);

    verify(joinPoint, never()).proceed();
  }

  /**
   * 어스펙트는 메서드 시그니처의 파라미터 애노테이션을 직접 훑는다. 그 경로를 실제로 태우기 위해 아래 SampleTarget 의 메서드를 리플렉션으로 가져와 조인 포인트에
   * 물린다.
   */
  private ProceedingJoinPoint joinPointFor(String methodName, Object... args) {
    Method method = findMethod(methodName);

    MethodSignature signature = mock(MethodSignature.class);
    when(signature.getMethod()).thenReturn(method);

    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(joinPoint.getArgs()).thenReturn(args);

    return joinPoint;
  }

  private Method findMethod(String methodName) {
    for (Method method : SampleTarget.class.getDeclaredMethods()) {
      if (method.getName().equals(methodName)) {
        return method;
      }
    }
    throw new IllegalStateException("테스트용 메서드를 찾지 못했습니다: " + methodName);
  }

  /** 어스펙트가 읽을 파라미터 애노테이션만 제공하는 테스트용 대상. */
  @SuppressWarnings("unused")
  static class SampleTarget {

    void withKey(@IdempotencyKeyParam String idempotencyKey, Long userId) {}

    void withoutKey(Long userId) {}

    void withNonStringKey(@IdempotencyKeyParam Long idempotencyKey) {}
  }
}
