package com.sorryisme.fmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.dto.request.CartRequestDto;
import com.sorryisme.fmarket.dto.response.CartResponseDto;
import com.sorryisme.fmarket.entity.Cart;
import com.sorryisme.fmarket.entity.CartDetail;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.CartDetailRepository;
import com.sorryisme.fmarket.repository.CartRepository;
import com.sorryisme.fmarket.repository.UserRepository;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final CartRepository cartRepository = mock(CartRepository.class);
  private final CartDetailRepository cartDetailRepository = mock(CartDetailRepository.class);
  private final CartService cartService =
      new CartService(cartRepository, cartDetailRepository, userRepository);

  @Test
  @DisplayName("없는 유저일 경우 에러 발생")
  void throwsWhenUserMissing() {
    when(userRepository.existsById(anyLong())).thenReturn(false);

    assertThatThrownBy(() -> cartService.addCart(createCartRequestDto(), 1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("이미 장바구니가 있으면 새로 만들지 않고 그 장바구니에 담는다")
  void reusesExistingCart() {
    when(userRepository.existsById(anyLong())).thenReturn(true);
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(DomainFixture.createCart(5L, 1L)));
    when(cartDetailRepository.save(any(CartDetail.class))).thenAnswer(inv -> inv.getArgument(0));

    CartResponseDto result = cartService.addCart(createCartRequestDto(), 1L);

    verify(cartRepository, never()).save(any(Cart.class));
    assertThat(result.getCartId()).isEqualTo(5L);
    assertThat(result.getProductOptionId()).isEqualTo(1000L);
    assertThat(result.getQuantity()).isEqualTo(20);
  }

  @Test
  @DisplayName("처음으로 장바구니에 담으려고 할 때 장바구니가 없다면 장바구니를 생성 후 제품 옵션을 담는다")
  void createsCartWhenAbsent() {
    when(userRepository.existsById(anyLong())).thenReturn(true);
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
    when(cartRepository.save(any(Cart.class))).thenReturn(DomainFixture.createCart(7L, 1L));
    when(cartDetailRepository.save(any(CartDetail.class))).thenAnswer(inv -> inv.getArgument(0));

    CartResponseDto result = cartService.addCart(createCartRequestDto(), 1L);

    assertThat(result.getCartId()).isEqualTo(7L);
    assertThat(result.getProductOptionId()).isEqualTo(1000L);
    assertThat(result.getQuantity()).isEqualTo(20);
  }

  @Test
  @DisplayName("삭제 성공 시 id가 리턴된다")
  void returnsIdOnDelete() {
    CartDetail cartDetail = DomainFixture.createCartDetail(1L);
    when(cartDetailRepository.findById(1L)).thenReturn(Optional.of(cartDetail));

    Long result = cartService.deleteCartDetail(1L);

    verify(cartDetailRepository).delete(cartDetail);
    assertThat(result).isEqualTo(1L);
  }

  @Test
  @DisplayName("삭제 대상 장바구니 상세가 없으면 예외가 발생한다")
  void throwsWhenCartDetailMissing() {
    when(cartDetailRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cartService.deleteCartDetail(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CART_NOT_FOUND);
  }

  private static CartRequestDto createCartRequestDto() {
    return CartRequestDto.builder().productOptionId(1000L).quantity(20).build();
  }
}
