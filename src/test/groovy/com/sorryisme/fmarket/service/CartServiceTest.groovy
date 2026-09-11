package com.sorryisme.fmarket.service

import com.sorryisme.fmarket.dto.request.CartRequestDto
import com.sorryisme.fmarket.dto.response.CartResponseDto
import com.sorryisme.fmarket.entity.CartDetail
import com.sorryisme.fmarket.entity.Cart
import com.sorryisme.fmarket.exception.BusinessException
import com.sorryisme.fmarket.common.ErrorCode
import com.sorryisme.fmarket.repository.CartDetailRepository
import com.sorryisme.fmarket.repository.CartRepository
import com.sorryisme.fmarket.repository.UserRepository
import com.sorryisme.fmarket.testUtils.DomainFixture
import spock.lang.Specification

class CartServiceTest extends Specification {
    UserRepository userRepository = Mock()
    CartRepository cartRepository = Mock()
    CartDetailRepository cartDetailRepository = Mock()
    CartService cartService = new CartService(cartRepository, cartDetailRepository, userRepository)

    def "없는 유저일 경우 에러 발생"() {
        given:
        userRepository.existsById(_ as Long) >> false

        when:
        cartService.addCart(createCartRequestDto(), 1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.USER_NOT_FOUND
    }

    def "이미 장바구니가 있으면 새로 만들지 않고 그 장바구니에 담는다"() {
        given:
        userRepository.existsById(_ as Long) >> true
        cartRepository.findByUserId(1L) >> Optional.of(DomainFixture.createCart(5L, 1L))
        cartDetailRepository.save(_ as CartDetail) >> { CartDetail d -> d }

        when:
        CartResponseDto result = cartService.addCart(createCartRequestDto(), 1L)

        then:
        0 * cartRepository.save(_)
        result.getCartId() == 5L
        result.getProductOptionId() == 1000L
        result.getQuantity() == 20
    }

    def "처음으로 장바구니에 담으려고 할 때 장바구니가 없다면 장바구니를 생성 후 제품 옵션을 담는다"() {
        given:
        userRepository.existsById(_ as Long) >> true
        cartRepository.findByUserId(1L) >> Optional.empty()
        cartRepository.save(_ as Cart) >> DomainFixture.createCart(7L, 1L)
        cartDetailRepository.save(_ as CartDetail) >> { CartDetail d -> d }

        when:
        CartResponseDto result = cartService.addCart(createCartRequestDto(), 1L)

        then:
        result.getCartId() == 7L
        result.getProductOptionId() == 1000L
        result.getQuantity() == 20
    }

    def "삭제 성공 시 id가 리턴된다"() {
        given:
        CartDetail cartDetail = DomainFixture.createCartDetail(1L)
        cartDetailRepository.findById(1L) >> Optional.of(cartDetail)

        when:
        Long result = cartService.deleteCartDetail(1L)

        then:
        1 * cartDetailRepository.delete(cartDetail)
        result == 1L
    }

    def "삭제 대상 장바구니 상세가 없으면 예외가 발생한다"() {
        given:
        cartDetailRepository.findById(1L) >> Optional.empty()

        when:
        cartService.deleteCartDetail(1L)

        then:
        def e = thrown(BusinessException)
        e.errorCode == ErrorCode.CART_NOT_FOUND
    }

    private static CartRequestDto createCartRequestDto() {
        return CartRequestDto.builder()
                .productOptionId(1000L)
                .quantity(20)
                .build()
    }
}
