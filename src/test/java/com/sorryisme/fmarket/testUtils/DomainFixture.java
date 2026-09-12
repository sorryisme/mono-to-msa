package com.sorryisme.fmarket.testUtils;

import com.sorryisme.fmarket.entity.Cart;
import com.sorryisme.fmarket.entity.CartDetail;
import com.sorryisme.fmarket.entity.Inventory;
import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.entity.OrderDetail;
import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.entity.ProductReview;
import com.sorryisme.fmarket.entity.User;
import com.sorryisme.fmarket.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.List;

/** 테스트용 엔티티 픽스처. id 가 필요한 단위 테스트는 id 를 인자로 받는 오버로드를 쓴다. */
public class DomainFixture {

  public static User createUser() {
    return createUser(null);
  }

  public static User createUser(Long id) {
    return User.builder()
        .id(id)
        .loginId("testUser")
        .password("xptmxmqlalfqjsgh!")
        .salt("testSalt")
        .name("테스트유저")
        .email("test@example.com")
        .phoneNumber("01012345678")
        .build();
  }

  public static Cart createCart(Long id, Long userId) {
    return Cart.builder().id(id).userId(userId).build();
  }

  public static CartDetail createCartDetail(Long id) {
    return CartDetail.builder().id(id).productOptionId(1L).quantity(5).build();
  }

  public static ProductReview createProductReview(Long userId) {
    return ProductReview.builder()
        .userId(userId)
        .productId(1L)
        .reviewText("좋은 제품입니다.")
        .rating(5)
        .build();
  }

  public static Product createProduct() {
    return Product.builder()
        .id(1L)
        .productName("제품명1")
        .description("상품설명")
        .thumbnail("썸네일 주소")
        .catalog("카탈로그")
        .build();
  }

  public static ProductOption createProductOption(
      Long id, Long productId, String optionName, String salePrice) {
    return ProductOption.builder()
        .id(id)
        .productId(productId)
        .optionName(optionName)
        .originPrice(new BigDecimal(salePrice))
        .salePrice(new BigDecimal(salePrice))
        .build();
  }

  public static Inventory createInventory(Long productOptionId, Integer quantity) {
    return Inventory.builder().productOptionId(productOptionId).quantity(quantity).build();
  }

  public static List<Inventory> createInventories() {
    return List.of(createInventory(1L, 1), createInventory(2L, 2));
  }

  public static Order createOrder(Long id, OrderStatus status) {
    return Order.builder()
        .id(id)
        .userId(1L)
        .status(status)
        .totalAmount(new BigDecimal(10000))
        .orderDetails(List.of(createOrderDetail(1L, 1L, 5)))
        .build();
  }

  public static OrderDetail createOrderDetail(Long id, Long productOptionId, int quantity) {
    return OrderDetail.builder()
        .id(id)
        .productOptionId(productOptionId)
        .price(new BigDecimal(100))
        .quantity(quantity)
        .build();
  }
}
