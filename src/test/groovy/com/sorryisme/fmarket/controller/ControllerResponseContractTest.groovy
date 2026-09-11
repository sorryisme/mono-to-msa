package com.sorryisme.fmarket.controller

import com.sorryisme.fmarket.common.SessionManager
import com.sorryisme.fmarket.dto.response.CartResponseDto
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse
import com.sorryisme.fmarket.dto.response.UserResponseDto
import com.sorryisme.fmarket.service.CartService
import com.sorryisme.fmarket.service.OrderService
import com.sorryisme.fmarket.service.ProductService
import com.sorryisme.fmarket.service.UserService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * docs/API_RESPONSE.md 의 성공 응답 계약(봉투 형식·생성 201)과 로그인 필요 401 을 컨트롤러 슬라이스에서 고정한다.
 * 서비스는 Mock 이고, @RequireLogin AOP 는 슬라이스에 포함되지 않으므로 @LoginUserId 리졸버 경로만 검증한다.
 */
@WebMvcTest(controllers = [UserController, CartController, ProductController, OrderController])
class ControllerResponseContractTest extends Specification {

    private static final JsonMapper MAPPER = new JsonMapper()

    @Autowired
    MockMvc mockMvc

    @SpringBean
    UserService userService = Mock()
    @SpringBean
    CartService cartService = Mock()
    @SpringBean
    ProductService productService = Mock()
    @SpringBean
    OrderService orderService = Mock()
    @SpringBean
    SessionManager sessionManager = Mock()

    def "회원 가입은 201 Created 와 OK 봉투로 응답한다"() {
        given:
        userService.createUser(_) >> UserResponseDto.builder().id(1L).loginId("tester").build()

        when:
        def result = mockMvc.perform(post("/api/v1/user/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"loginId":"tester","password":"1234","name":"테스터","email":"t@t.com","phoneNumber":"010-0000-0000"}'))
                .andReturn()
        def body = json(result)

        then:
        result.response.status == 201
        body.code == "OK"
        body.data.loginId == "tester"
    }

    def "로그인한 유저의 장바구니 담기는 201 Created 로 응답한다"() {
        given:
        sessionManager.getUserId() >> 1L
        cartService.addCart(_, 1L) >> CartResponseDto.builder().id(10L).cartId(1L).productOptionId(1L).quantity(2).build()

        when:
        def result = mockMvc.perform(post("/api/v1/cart/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"productOptionId":1,"quantity":2}'))
                .andReturn()
        def body = json(result)

        then:
        result.response.status == 201
        body.code == "OK"
        body.data.id == 10
    }

    def "세션이 없는 @LoginUserId 요청은 401 LOGIN_REQUIRED 로 응답한다"() {
        given:
        sessionManager.getUserId() >> null

        when:
        def result = mockMvc.perform(post("/api/v1/cart/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"productOptionId":1,"quantity":2}'))
                .andReturn()
        def body = json(result)

        then:
        result.response.status == 401
        body.code == "LOGIN_REQUIRED"
        0 * cartService._
    }

    def "카테고리 목록 조회도 다른 응답과 같은 봉투로 감싼다"() {
        given:
        productService.findMajorCategoryList() >> [MajorCategoryResponse.builder().majorCategoryId(1L).majorCategoryName("식품").build()]

        when:
        def result = mockMvc.perform(get("/api/v1/products/category")).andReturn()
        def body = json(result)

        then:
        result.response.status == 200
        body.code == "OK"
        body.data*.majorCategoryName == ["식품"]
    }

    def "조회 성공은 200 OK 로 응답한다"() {
        given:
        productService.findMajorCategoryList() >> []

        when:
        def result = mockMvc.perform(get("/api/v1/products/category")).andReturn()

        then:
        result.response.status == 200
    }

    private static Map json(def result) {
        MAPPER.readValue(result.response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map)
    }
}
