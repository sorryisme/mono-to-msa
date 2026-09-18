package com.sorryisme.fmarket.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

class MDCLoggingFilterTest {

  private static MockHttpServletRequest postRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/create");
    request.setContent("{\"orderItems\":[]}".getBytes(StandardCharsets.UTF_8));
    request.setContentType("application/json");
    request.addHeader("Idempotency-Key", "k");
    return request;
  }

  /** 다음 필터가 받은 요청/응답 객체와 그 시점의 MDC 값을 잡아 둔다. */
  private static final class CapturingChain implements FilterChain {
    ServletRequest request;
    ServletResponse response;
    final AtomicReference<String> mdcMethod = new AtomicReference<>();
    final AtomicReference<String> mdcUri = new AtomicReference<>();
    final AtomicReference<String> mdcRequestId = new AtomicReference<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res) throws java.io.IOException {
      this.request = req;
      this.response = res;
      mdcMethod.set(MDC.get("method"));
      mdcUri.set(MDC.get("uri"));
      mdcRequestId.set(MDC.get("request_UUID"));
      res.getWriter().write("{\"code\":\"OK\"}");
    }
  }

  @Test
  @DisplayName("본문 로깅이 켜져 있으면(기본값) ContentCaching wrapper 로 감싸 다음 필터에 넘기고 응답 본문을 복원한다")
  void wrapsRequestAndResponseWhenBodyLoggingEnabled() throws Exception {
    MDCLoggingFilter filter = new MDCLoggingFilter(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    CapturingChain chain = new CapturingChain();

    filter.doFilter(postRequest(), response, chain);

    assertThat(chain.request).isInstanceOf(ContentCachingRequestWrapper.class);
    assertThat(chain.response).isInstanceOf(ContentCachingResponseWrapper.class);
    // wrapper 가 잡아 둔 응답 본문이 실제 응답으로 복사돼야 클라이언트가 빈 본문을 받지 않는다.
    assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"OK\"}");
  }

  @Test
  @DisplayName("본문 로깅이 꺼져 있으면(loadtest) wrapper 없이 원본 요청/응답을 그대로 넘긴다")
  void passesOriginalObjectsWhenBodyLoggingDisabled() throws Exception {
    MDCLoggingFilter filter = new MDCLoggingFilter(false);
    MockHttpServletRequest request = postRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    CapturingChain chain = new CapturingChain();

    filter.doFilter(request, response, chain);

    // 부하 측정을 왜곡하는 본문 복사·문자열화 경로를 타지 않아야 한다.
    assertThat(chain.request).isSameAs(request);
    assertThat(chain.response).isSameAs(response);
    assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"OK\"}");
  }

  @Test
  @DisplayName("본문 로깅 여부와 무관하게 MDC(request_UUID, method, uri) 를 채우고 요청이 끝나면 비운다")
  void fillsAndClearsMdcRegardlessOfBodyLogging() throws Exception {
    for (boolean enabled : new boolean[] {true, false}) {
      MDCLoggingFilter filter = new MDCLoggingFilter(enabled);
      CapturingChain chain = new CapturingChain();

      filter.doFilter(postRequest(), new MockHttpServletResponse(), chain);

      assertThat(chain.mdcMethod.get()).isEqualTo("POST");
      assertThat(chain.mdcUri.get()).isEqualTo("/api/v1/orders/create");
      assertThat(chain.mdcRequestId.get()).isNotBlank();
      assertThat(MDC.get("request_UUID")).isNull();
      assertThat(MDC.get("uri")).isNull();
    }
  }

  @Test
  @DisplayName("다음 필터가 예외를 던져도 MDC 를 비운다")
  void clearsMdcWhenChainThrows() {
    MDCLoggingFilter filter = new MDCLoggingFilter(false);
    FilterChain throwingChain =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(postRequest(), new MockHttpServletResponse(), throwingChain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get("request_UUID")).isNull();
  }
}
