package com.sorryisme.fmarket.aop;

import com.sorryisme.fmarket.annotation.IdempotencyKeyParam;
import com.sorryisme.fmarket.entity.IdempotencyKey;
import com.sorryisme.fmarket.exception.DuplicateDataException;
import com.sorryisme.fmarket.repository.IdempotencyKeyRepository;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

  private final IdempotencyKeyRepository idempotencyKeyRepository;

  @Around("@annotation(com.sorryisme.fmarket.annotation.Idempotent)")
  public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    Object[] args = joinPoint.getArgs();
    String idempotencyKey = null;

    Annotation[][] paramAnnotations = method.getParameterAnnotations();

    for (int i = 0; i < paramAnnotations.length; i++) {
      for (Annotation annotation : paramAnnotations[i]) {
        if (annotation instanceof IdempotencyKeyParam) {
          Object arg = args[i];

          if (!(arg instanceof String)) {
            throw new IllegalArgumentException("Idempotency-Key 파라미터는 String 타입이어야 합니다.");
          }

          idempotencyKey = (String) arg;
          break;
        }
      }

      if (idempotencyKey != null) {
        break;
      }
    }

    if (idempotencyKey == null) throw new IllegalArgumentException("Idempotency-Key 헤더가 필요합니다.");
    if (idempotencyKey.length() != 36)
      throw new IllegalArgumentException("idempotencyKey 키는 36자리여야 합니다.");

    // 유니크 제약 위반이 곧 중복 요청이다. 즉시 flush 해서 INSERT 결과를 이 자리에서 확정한다.
    try {
      idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(idempotencyKey));
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateDataException("중복된 요청입니다.");
    }

    return joinPoint.proceed();
  }
}
