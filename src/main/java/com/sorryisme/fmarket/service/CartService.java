package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.dto.request.CartRequestDto;
import com.sorryisme.fmarket.dto.response.CartResponseDto;
import com.sorryisme.fmarket.entity.Cart;
import com.sorryisme.fmarket.entity.CartDetail;
import com.sorryisme.fmarket.exception.NotFoundDataException;
import com.sorryisme.fmarket.repository.CartDetailRepository;
import com.sorryisme.fmarket.repository.CartRepository;
import com.sorryisme.fmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

  private final CartRepository cartRepository;
  private final CartDetailRepository cartDetailRepository;
  private final UserRepository userRepository;

  @Transactional
  public CartResponseDto addCart(CartRequestDto cartRequestDto, Long userId) {

    if (!userRepository.existsById(userId)) throw new NotFoundDataException("찾을 수 없는 유저입니다");

    // 카트가 없을 경우 카트를 최초 생성
    Cart cart =
        cartRepository.findByUserId(userId).orElseGet(() -> cartRepository.save(Cart.of(userId)));

    CartDetail cartDetail =
        CartDetail.builder()
            .productOptionId(cartRequestDto.getProductOptionId())
            .quantity(cartRequestDto.getQuantity())
            .build();
    cart.addCartDetail(cartDetail);

    return CartResponseDto.from(cartDetailRepository.save(cartDetail));
  }

  @Transactional
  public Long deleteCartDetail(Long id) {
    CartDetail cartDetail =
        cartDetailRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 장바구니입니다"));
    cartDetailRepository.delete(cartDetail);
    return id;
  }
}
