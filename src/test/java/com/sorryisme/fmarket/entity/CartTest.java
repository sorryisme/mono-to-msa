package com.sorryisme.fmarket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 장바구니 연관관계 편의 메서드와 수량 경계. */
class CartTest {

  @Test
  @DisplayName("항목을 담으면 양쪽 참조가 맞춰지고, 빼면 둘 다 끊긴다")
  void keepsBothSidesInSync() {
    Cart cart = Cart.of(1L);
    CartDetail detail = DomainFixture.createCartDetail(10L);

    cart.addCartDetail(detail);
    assertThat(cart.getCartDetails()).containsExactly(detail);
    assertThat(detail.getCart()).isSameAs(cart);

    cart.removeCartDetail(detail);
    assertThat(cart.getCartDetails()).isEmpty();
    assertThat(detail.getCart()).isNull();
  }

  @Test
  @DisplayName("빌더로 넘긴 항목도 연관관계 편의 메서드를 거쳐 담긴다")
  void builderAssignsCartToGivenDetails() {
    CartDetail detail = DomainFixture.createCartDetail(10L);

    Cart cart = Cart.builder().userId(1L).cartDetails(List.of(detail)).build();

    assertThat(cart.getCartDetails()).containsExactly(detail);
    assertThat(detail.getCart()).isSameAs(cart);
  }

  @Test
  @DisplayName("밖으로 내준 항목 목록은 수정할 수 없다")
  void exposesUnmodifiableDetails() {
    Cart cart = Cart.of(1L);

    assertThatThrownBy(() -> cart.getCartDetails().add(DomainFixture.createCartDetail(1L)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("수량은 최소값 1 까지 바꿀 수 있다")
  void changesQuantityAtLowerBound() {
    CartDetail detail = DomainFixture.createCartDetail(10L);

    detail.changeQuantity(1);

    assertThat(detail.getQuantity()).isEqualTo(1);
  }

  @Test
  @DisplayName("수량을 1 미만으로 바꾸면 거절하고 기존 수량을 유지한다")
  void rejectsQuantityBelowOne() {
    CartDetail detail = DomainFixture.createCartDetail(10L);

    assertThatThrownBy(() -> detail.changeQuantity(0)).isInstanceOf(IllegalArgumentException.class);
    assertThat(detail.getQuantity()).isEqualTo(5);
  }
}
