package com.sorryisme.fmarket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 상품·상품 옵션의 판매 상태 전이. 삭제는 되돌릴 수 없다는 규칙이 핵심이다. */
class ProductStatusTransitionTest {

  @Nested
  @DisplayName("Product")
  class ProductTransition {

    @Test
    @DisplayName("상태를 주지 않으면 판매중으로 만들어진다")
    void defaultsToOnSale() {
      Product product = DomainFixture.createProduct();

      assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
      assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("판매중지 후 재개하면 다시 판매중이 된다")
    void suspendsAndResumes() {
      Product product = DomainFixture.createProduct();

      product.suspend();
      assertThat(product.getStatus()).isEqualTo(ProductStatus.SUSPENDED);

      product.resume();
      assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("삭제된 상품은 판매중지·재개할 수 없고 상태가 그대로 남는다")
    void rejectsTransitionAfterDelete() {
      Product product = DomainFixture.createProduct();
      product.delete();

      assertThat(product.isDeleted()).isTrue();
      assertThatThrownBy(product::suspend).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(product::resume).isInstanceOf(IllegalStateException.class);
      assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
    }
  }

  @Nested
  @DisplayName("ProductOption")
  class ProductOptionTransition {

    @Test
    @DisplayName("상태를 주지 않으면 판매중으로 만들어진다")
    void defaultsToOnSale() {
      ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션", "1000");

      assertThat(option.getStatus()).isEqualTo(ProductStatus.ON_SALE);
      assertThat(option.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("판매중지 후 재개하면 다시 판매중이 된다")
    void suspendsAndResumes() {
      ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션", "1000");

      option.suspend();
      assertThat(option.getStatus()).isEqualTo(ProductStatus.SUSPENDED);

      option.resume();
      assertThat(option.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("삭제된 옵션은 판매중지·재개할 수 없고 상태가 그대로 남는다")
    void rejectsTransitionAfterDelete() {
      ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션", "1000");
      option.delete();

      assertThat(option.isDeleted()).isTrue();
      assertThatThrownBy(option::suspend).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(option::resume).isInstanceOf(IllegalStateException.class);
      assertThat(option.getStatus()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    @DisplayName("가격 변경은 정가와 판매가를 함께 바꾼다")
    void changesBothPrices() {
      ProductOption option = DomainFixture.createProductOption(1L, 1L, "옵션", "1000");

      option.changePrice(new BigDecimal("2000"), new BigDecimal("1800"));

      assertThat(option.getOriginPrice()).isEqualByComparingTo("2000");
      assertThat(option.getSalePrice()).isEqualByComparingTo("1800");
    }
  }
}
