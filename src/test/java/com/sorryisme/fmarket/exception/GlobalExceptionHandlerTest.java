package com.sorryisme.fmarket.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.dto.ResponseDto;
import com.sorryisme.fmarket.config.WebConfig;
import com.sorryisme.fmarket.resolver.LoginUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/API_RESPONSE.md 의 응답 계약을 고정한다. 실제 컨트롤러 대신 예외를 일으키는 프로브 컨트롤러를 띄워 핸들러만 검증한다. WebConfig 와
 * LoginUserIdResolver 는 SessionManager 에 의존하므로 슬라이스에서 제외한다.
 */
@WebMvcTest(
    controllers = ProbeController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {WebConfig.class, LoginUserIdResolver.class}))
@Import(ProbeController.class)
class GlobalExceptionHandlerTest {

  private static final JsonMapper MAPPER = new JsonMapper();

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("BusinessException 은 ErrorCode 의 HTTP 상태와 코드·기본 메시지로 응답한다")
  void mapsBusinessExceptionToErrorCodeStatus() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/order-not-found")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(body.get("code")).isEqualTo("ORDER_NOT_FOUND");
    assertThat(body.get("message")).isEqualTo(ErrorCode.ORDER_NOT_FOUND.getMessage());
    assertThat(body.get("data")).isNull();
  }

  @Test
  @DisplayName("BusinessException 에 메시지를 지정하면 기본 메시지 대신 그 메시지로 응답한다")
  void usesCustomMessageWhenGiven() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/out-of-stock-custom")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(body.get("code")).isEqualTo("OUT_OF_STOCK");
    assertThat(body.get("message")).isEqualTo("옵션 7 의 재고가 부족합니다.");
  }

  @Test
  @DisplayName("@Valid 실패는 400 INVALID_INPUT 과 필드 오류 목록으로 응답한다")
  void mapsValidationFailureToFieldErrors() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/probe/valid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\", \"quantity\":0}"))
            .andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(body.get("code")).isEqualTo("INVALID_INPUT");
    assertThat(body.get("message")).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    assertThat(dataList(body))
        .extracting(fieldError -> (String) fieldError.get("field"))
        .containsExactlyInAnyOrder("name", "quantity");
    assertThat(dataList(body))
        .allSatisfy(
            fieldError ->
                assertThat(fieldError.get("reason")).isInstanceOf(String.class).isNotEqualTo(""));
  }

  @Test
  @DisplayName("JSON 파싱 실패는 400 INVALID_INPUT 으로 응답한다")
  void mapsJsonParseFailure() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/probe/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(body.get("code")).isEqualTo("INVALID_INPUT");
    assertThat(body.get("data")).isNull();
  }

  @Test
  @DisplayName("필수 헤더 누락은 400 INVALID_INPUT 으로 응답한다")
  void mapsMissingRequiredHeader() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/header")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(body.get("code")).isEqualTo("INVALID_INPUT");
  }

  @Test
  @DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED 로 응답한다")
  void mapsUnsupportedMethod() throws Exception {
    MvcResult result = mockMvc.perform(post("/probe/order-not-found")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(405);
    assertThat(body.get("code")).isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  @DisplayName("매핑되지 않은 경로는 404 RESOURCE_NOT_FOUND 로 응답한다")
  void mapsUnmappedPath() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/no-such-path")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(body.get("code")).isEqualTo("RESOURCE_NOT_FOUND");
  }

  @Test
  @DisplayName("IllegalArgumentException 은 400 INVALID_INPUT 과 예외 메시지로 응답한다")
  void mapsIllegalArgumentException() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/illegal-argument")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(body.get("code")).isEqualTo("INVALID_INPUT");
    assertThat(body.get("message")).isEqualTo("수량은 1 이상이어야 합니다: 0");
  }

  @Test
  @DisplayName("IllegalStateException 은 409 INVALID_STATE 로 응답한다")
  void mapsIllegalStateException() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/illegal-state")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(body.get("code")).isEqualTo("INVALID_STATE");
    assertThat(body.get("message")).isEqualTo("삭제된 상품의 상태는 바꿀 수 없습니다.");
  }

  @Test
  @DisplayName("처리되지 않은 예외는 500 INTERNAL_ERROR 와 고정 메시지로 응답하고 예외 내용을 노출하지 않는다")
  void hidesInternalDetailsOnUnhandledException() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/boom")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    assertThat(body.get("code")).isEqualTo("INTERNAL_ERROR");
    assertThat(body.get("message"))
        .isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage())
        .asString()
        .doesNotContain("secret");
    assertThat(body.get("data")).isNull();
  }

  @Test
  @DisplayName("성공 응답은 code OK 와 data 로 구성된다")
  void successEnvelopeShape() throws Exception {
    MvcResult result = mockMvc.perform(get("/probe/ok")).andReturn();
    Map<String, Object> body = json(result);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body.get("code")).isEqualTo("OK");
    assertThat(body.get("data")).isEqualTo(42);
    assertThat(body).containsOnlyKeys("code", "message", "data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> json(MvcResult result) throws Exception {
    return MAPPER.readValue(
        result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> dataList(Map<String, Object> body) {
    return (List<Map<String, Object>>) body.get("data");
  }
}

/** 핸들러만 떼어 검증하기 위한 프로브 컨트롤러. 각 엔드포인트가 계약 표의 한 줄에 대응한다. */
@RestController
@RequestMapping("/probe")
class ProbeController {

  static class ValidBody {
    @NotBlank public String name;

    @Min(1L)
    public int quantity;
  }

  @GetMapping("/ok")
  ResponseDto<Integer> ok() {
    return ResponseDto.success(42);
  }

  @GetMapping("/order-not-found")
  void orderNotFound() {
    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
  }

  @GetMapping("/out-of-stock-custom")
  void outOfStock() {
    throw new BusinessException(ErrorCode.OUT_OF_STOCK, "옵션 7 의 재고가 부족합니다.");
  }

  @PostMapping("/valid")
  ResponseDto<String> valid(@RequestBody @Valid ValidBody body) {
    return ResponseDto.success(body.name);
  }

  @GetMapping("/header")
  ResponseDto<String> header(@RequestHeader("Idempotency-Key") String key) {
    return ResponseDto.success(key);
  }

  @GetMapping("/illegal-argument")
  void illegalArgument() {
    throw new IllegalArgumentException("수량은 1 이상이어야 합니다: 0");
  }

  @GetMapping("/illegal-state")
  void illegalState() {
    throw new IllegalStateException("삭제된 상품의 상태는 바꿀 수 없습니다.");
  }

  @GetMapping("/boom")
  void boom() {
    throw new RuntimeException("secret internal detail");
  }
}
