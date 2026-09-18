package com.sorryisme.fmarket.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 요청마다 MDC(request_UUID, method, uri) 를 채우고 요청/응답을 로그로 남긴다.
 *
 * <p>{@code fmarket.http-log.body-enabled} 가 false 이면(부하 테스트 프로파일) 본문 캐싱 wrapper 자체를 만들지 않고 상태 코드와
 * 소요 시간만 남긴다. 로그 레벨만 낮추면 wrapper 생성과 본문 문자열화 비용이 그대로 남아 측정값을 왜곡하기 때문이다.
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MDCLoggingFilter implements Filter {

  private static final String MDC_REQUEST_UUID = "request_UUID";
  private static final String MDC_URI = "uri";
  private static final String MDC_METHOD = "method";

  // Spring Framework 7 에서 한도 없는 ContentCachingRequestWrapper 생성자가 제거되어
  // 캐시 상한을 명시해야 한다. 요청 바디는 로그로만 쓰이므로 상한을 두는 편이 안전하다.
  private static final int REQUEST_CACHE_LIMIT_BYTES = 64 * 1024;

  private final boolean bodyLoggingEnabled;

  public MDCLoggingFilter(
      @Value("${fmarket.http-log.body-enabled:true}") boolean bodyLoggingEnabled) {
    this.bodyLoggingEnabled = bodyLoggingEnabled;
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    MDC.put(MDC_REQUEST_UUID, UUID.randomUUID().toString());
    MDC.put(MDC_URI, request.getRequestURI());
    MDC.put(MDC_METHOD, request.getMethod());
    long startTime = System.currentTimeMillis();

    try {
      if (bodyLoggingEnabled) {
        doFilterWithBodyLogging(request, response, filterChain, startTime);
      } else {
        doFilterCompact(request, response, filterChain, startTime);
      }
    } finally {
      MDC.clear();
    }
  }

  private void doFilterWithBodyLogging(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      long startTime)
      throws IOException, ServletException {
    ContentCachingRequestWrapper wrappedRequest =
        new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT_BYTES);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(wrappedRequest, wrappedResponse);
    } finally {
      long processingTime = System.currentTimeMillis() - startTime;
      log.info(toString(wrappedRequest, wrappedResponse, processingTime));
      wrappedResponse.copyBodyToResponse();
    }
  }

  private void doFilterCompact(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      long startTime)
      throws IOException, ServletException {
    try {
      filterChain.doFilter(request, response);
    } finally {
      long processingTime = System.currentTimeMillis() - startTime;
      log.info("status={} elapsedMs={}", response.getStatus(), processingTime);
    }
  }

  private String getRequestBody(ContentCachingRequestWrapper requestWrapper) {
    byte[] body = requestWrapper.getContentAsByteArray();
    return new String(body, StandardCharsets.UTF_8);
  }

  private String getResponseBody(ContentCachingResponseWrapper responseWrapper) {
    byte[] body = responseWrapper.getContentAsByteArray();
    return new String(body, StandardCharsets.UTF_8);
  }

  private String toString(
      ContentCachingRequestWrapper wrappedRequest,
      ContentCachingResponseWrapper wrappedResponse,
      long elapsedTime) {
    return "|\n"
        + String.format(">>[USER_AGENT]: %s\n", wrappedRequest.getHeader("User-Agent"))
        + String.format(">>[REFERER]: %s\n", wrappedRequest.getHeader("Referer"))
        + String.format(">>[ORIGIN]: %s\n", wrappedRequest.getHeader("Origin"))
        + String.format(">>[Idempotency-Key]: %s\n", wrappedRequest.getHeader("Idempotency-Key"))
        + String.format(">>[REMOTE_ADDR]: %s\n", wrappedRequest.getRemoteAddr())
        + String.format(">>[REMOTE_HOST]: %s\n", wrappedRequest.getRemoteHost())
        + String.format(">>[REQUEST_BODY]: %s\n", getRequestBody(wrappedRequest))
        + String.format(">>[STATUS]: %s\n", wrappedResponse.getStatus())
        + String.format(">>[ProcessingTime]: %d\n", elapsedTime)
        + String.format(">>[RESPONSE_BODY]: %s", getResponseBody(wrappedResponse));
  }
}
