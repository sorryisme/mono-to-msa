package com.sorryisme.fmarket.exception

import com.sorryisme.fmarket.common.dto.ResponseDto
import com.sorryisme.fmarket.config.WebConfig
import com.sorryisme.fmarket.resolver.LoginUserIdResolver
import com.sorryisme.fmarket.common.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * docs/API_RESPONSE.md 의 응답 계약을 고정한다.
 * 실제 컨트롤러 대신 예외를 일으키는 프로브 컨트롤러를 띄워 핸들러만 검증한다.
 * WebConfig 와 LoginUserIdResolver 는 SessionManager 에 의존하므로 슬라이스에서 제외한다.
 */
@WebMvcTest(
        controllers = ProbeController,
        excludeFilters = [@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [WebConfig, LoginUserIdResolver])])
@Import(ProbeController)
class GlobalExceptionHandlerTest extends Specification {

    @Autowired
    MockMvc mockMvc

    def "BusinessException 은 ErrorCode 의 HTTP 상태와 코드·기본 메시지로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/order-not-found")).andReturn()
        def body = json(result)

        then:
        result.response.status == 404
        body.code == "ORDER_NOT_FOUND"
        body.message == ErrorCode.ORDER_NOT_FOUND.message
        body.data == null
    }

    def "BusinessException 에 메시지를 지정하면 기본 메시지 대신 그 메시지로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/out-of-stock-custom")).andReturn()
        def body = json(result)

        then:
        result.response.status == 409
        body.code == "OUT_OF_STOCK"
        body.message == "옵션 7 의 재고가 부족합니다."
    }

    def "@Valid 실패는 400 INVALID_INPUT 과 필드 오류 목록으로 응답한다"() {
        when:
        def result = mockMvc.perform(post("/probe/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"name":"", "quantity":0}'))
                .andReturn()
        def body = json(result)

        then:
        result.response.status == 400
        body.code == "INVALID_INPUT"
        body.message == ErrorCode.INVALID_INPUT.message
        body.data*.field.sort() == ["name", "quantity"]
        body.data.every { it.reason instanceof String && !it.reason.isEmpty() }
    }

    def "JSON 파싱 실패는 400 INVALID_INPUT 으로 응답한다"() {
        when:
        def result = mockMvc.perform(post("/probe/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{not json'))
                .andReturn()
        def body = json(result)

        then:
        result.response.status == 400
        body.code == "INVALID_INPUT"
        body.data == null
    }

    def "필수 헤더 누락은 400 INVALID_INPUT 으로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/header")).andReturn()
        def body = json(result)

        then:
        result.response.status == 400
        body.code == "INVALID_INPUT"
    }

    def "지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED 로 응답한다"() {
        when:
        def result = mockMvc.perform(post("/probe/order-not-found")).andReturn()
        def body = json(result)

        then:
        result.response.status == 405
        body.code == "METHOD_NOT_ALLOWED"
    }

    def "매핑되지 않은 경로는 404 RESOURCE_NOT_FOUND 로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/no-such-path")).andReturn()
        def body = json(result)

        then:
        result.response.status == 404
        body.code == "RESOURCE_NOT_FOUND"
    }

    def "IllegalArgumentException 은 400 INVALID_INPUT 과 예외 메시지로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/illegal-argument")).andReturn()
        def body = json(result)

        then:
        result.response.status == 400
        body.code == "INVALID_INPUT"
        body.message == "수량은 1 이상이어야 합니다: 0"
    }

    def "IllegalStateException 은 409 INVALID_STATE 로 응답한다"() {
        when:
        def result = mockMvc.perform(get("/probe/illegal-state")).andReturn()
        def body = json(result)

        then:
        result.response.status == 409
        body.code == "INVALID_STATE"
        body.message == "삭제된 상품의 상태는 바꿀 수 없습니다."
    }

    def "처리되지 않은 예외는 500 INTERNAL_ERROR 와 고정 메시지로 응답하고 예외 내용을 노출하지 않는다"() {
        when:
        def result = mockMvc.perform(get("/probe/boom")).andReturn()
        def body = json(result)

        then:
        result.response.status == 500
        body.code == "INTERNAL_ERROR"
        body.message == ErrorCode.INTERNAL_ERROR.message
        !body.message.contains("secret")
        body.data == null
    }

    def "성공 응답은 code OK 와 data 로 구성된다"() {
        when:
        def result = mockMvc.perform(get("/probe/ok")).andReturn()
        def body = json(result)

        then:
        result.response.status == 200
        body.code == "OK"
        body.data == 42
        body.keySet() == ["code", "message", "data"] as Set
    }

    private static final JsonMapper MAPPER = new JsonMapper()

    private static Map json(def result) {
        MAPPER.readValue(result.response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map)
    }
}

@RestController
@RequestMapping("/probe")
class ProbeController {

    static class ValidBody {
        @NotBlank String name
        @Min(1L) int quantity
    }

    @GetMapping("/ok")
    ResponseDto<Integer> ok() { ResponseDto.success(42) }

    @GetMapping("/order-not-found")
    void orderNotFound() { throw new BusinessException(ErrorCode.ORDER_NOT_FOUND) }

    @GetMapping("/out-of-stock-custom")
    void outOfStock() { throw new BusinessException(ErrorCode.OUT_OF_STOCK, "옵션 7 의 재고가 부족합니다.") }

    @PostMapping("/valid")
    ResponseDto<String> valid(@RequestBody @Valid ValidBody body) { ResponseDto.success(body.name) }

    @GetMapping("/header")
    ResponseDto<String> header(@RequestHeader("Idempotency-Key") String key) { ResponseDto.success(key) }

    @GetMapping("/illegal-argument")
    void illegalArgument() { throw new IllegalArgumentException("수량은 1 이상이어야 합니다: 0") }

    @GetMapping("/illegal-state")
    void illegalState() { throw new IllegalStateException("삭제된 상품의 상태는 바꿀 수 없습니다.") }

    @GetMapping("/boom")
    void boom() { throw new RuntimeException("secret internal detail") }
}
