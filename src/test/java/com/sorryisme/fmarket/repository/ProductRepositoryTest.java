package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.entity.User;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 실제 MySQL 위에서 상품 검색·상태 필터를 검증한다. data.sql 의 product 1~3, product_option 1~4 를 전제로 한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

  @Autowired private ProductRepository productRepository;
  @Autowired private ProductOptionRepository productOptionRepository;
  @Autowired private ProductReviewRepository productReviewRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @BeforeEach
  void setUp() {
    productRepository.save(product("스펙 검색용 티셔츠", 7, 701, ProductStatus.ON_SALE));
    productRepository.save(product("스펙 검색용 바지", 7, 702, ProductStatus.ON_SALE));
    productRepository.save(product("다른 카테고리", 8, 801, ProductStatus.ON_SALE));
    productRepository.save(product("스펙 검색용 판매중지", 7, 701, ProductStatus.SUSPENDED));
    productRepository.save(product("스펙 검색용 삭제됨", 7, 701, ProductStatus.DELETED));
    em.flush();
  }

  static Stream<Arguments> searchConditions() {
    return Stream.of(
        Arguments.of("스펙 검색용", null, null, List.of("스펙 검색용 티셔츠", "스펙 검색용 바지")),
        Arguments.of(null, 7, null, List.of("스펙 검색용 티셔츠", "스펙 검색용 바지")),
        Arguments.of(null, 7, 702, List.of("스펙 검색용 바지")),
        Arguments.of("티셔츠", 7, 701, List.of("스펙 검색용 티셔츠")),
        Arguments.of("없는이름", null, null, List.of()));
  }

  @ParameterizedTest(name = "query={0}, major={1}, sub={2}")
  @MethodSource("searchConditions")
  @DisplayName("검색 조건 조합에 따라 상품이 필터링된다")
  void filtersBySearchConditions(
      String query, Integer major, Integer sub, List<String> expectedNames) {
    Page<Product> page =
        productRepository.findAll(
            ProductSpecification.search(
                new ProductSearchDto(query, major, sub, Pageable.ofSize(50))),
            Pageable.ofSize(50));

    List<String> names = page.getContent().stream().map(Product::getProductName).toList();
    assertThat(names).containsAll(expectedNames);
    assertThat(names.stream().filter(ProductRepositoryTest::isFixtureName).toList())
        .hasSameSizeAs(expectedNames);
  }

  @Test
  @DisplayName("목록 검색은 판매중지·삭제 상품을 제외한다")
  void excludesSuspendedAndDeletedFromList() {
    Page<Product> page =
        productRepository.findAll(
            ProductSpecification.search(
                new ProductSearchDto("스펙 검색용", null, null, Pageable.ofSize(50))),
            Pageable.ofSize(50));

    assertThat(page.getContent())
        .extracting(Product::getStatus)
        .containsOnly(ProductStatus.ON_SALE);
    assertThat(page.getContent())
        .extracting(Product::getProductName)
        .doesNotContain("스펙 검색용 판매중지", "스펙 검색용 삭제됨");
  }

  @Test
  @DisplayName("빈 검색 조건이면 전체 상품이 페이징되어 조회된다")
  void pagesAllProductsWhenConditionEmpty() {
    Page<Product> page =
        productRepository.findAll(
            ProductSpecification.search(new ProductSearchDto("", null, null, Pageable.ofSize(2))),
            Pageable.ofSize(2));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("단건 조회는 삭제된 상품만 제외하고 판매중지 상품은 돌려준다")
  void singleLookupExcludesOnlyDeleted() {
    Long suspendedId = findIdByName("스펙 검색용 판매중지");
    Long deletedId = findIdByName("스펙 검색용 삭제됨");

    assertThat(productRepository.findByIdAndStatusNot(suspendedId, ProductStatus.DELETED))
        .isPresent();
    assertThat(productRepository.findByIdAndStatusNot(deletedId, ProductStatus.DELETED)).isEmpty();
    assertThat(productRepository.existsByIdAndStatusNot(suspendedId, ProductStatus.DELETED))
        .isTrue();
    assertThat(productRepository.existsByIdAndStatusNot(deletedId, ProductStatus.DELETED))
        .isFalse();
  }

  @Test
  @DisplayName("상품 id 로 옵션과 리뷰를 각각 조회한다")
  void findsOptionsAndReviewsByProductId() {
    User user = userRepository.save(DomainFixture.createUser());
    productReviewRepository.save(DomainFixture.createProductReview(user.getId()));
    em.flush();

    assertThat(productOptionRepository.findAllByProductIdAndStatusNot(1L, ProductStatus.DELETED))
        .isNotEmpty();
    assertThat(productReviewRepository.findAllByProductId(1L)).isNotEmpty();
  }

  @Test
  @DisplayName("상품 상세용 옵션 조회는 삭제된 옵션만 제외한다")
  void optionLookupExcludesOnlyDeleted() {
    ProductOption suspended =
        productOptionRepository.save(option(1L, "판매중지 옵션", ProductStatus.SUSPENDED));
    ProductOption deleted =
        productOptionRepository.save(option(1L, "삭제된 옵션", ProductStatus.DELETED));
    em.flush();

    List<Long> ids =
        productOptionRepository.findAllByProductIdAndStatusNot(1L, ProductStatus.DELETED).stream()
            .map(ProductOption::getId)
            .toList();

    assertThat(ids).contains(suspended.getId()).doesNotContain(deleted.getId());
  }

  @Test
  @DisplayName("주문용 옵션 조회는 판매중 옵션만 돌려준다")
  void orderLookupReturnsOnSaleOnly() {
    ProductOption suspended =
        productOptionRepository.save(option(1L, "판매중지 옵션", ProductStatus.SUSPENDED));
    em.flush();

    List<ProductOption> found =
        productOptionRepository.findAllByIdInAndStatus(
            List.of(1L, 2L, suspended.getId()), ProductStatus.ON_SALE);

    assertThat(found).extracting(ProductOption::getId).containsExactlyInAnyOrder(1L, 2L);
  }

  private static boolean isFixtureName(String name) {
    return name.startsWith("스펙 검색용") || name.equals("다른 카테고리");
  }

  private Long findIdByName(String productName) {
    return productRepository.findAll().stream()
        .filter(product -> product.getProductName().equals(productName))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private static Product product(String name, int major, int sub, ProductStatus status) {
    return Product.builder()
        .productName(name)
        .description("d")
        .majorCategory(major)
        .subcategory(sub)
        .status(status)
        .build();
  }

  private static ProductOption option(Long productId, String name, ProductStatus status) {
    return ProductOption.builder()
        .productId(productId)
        .optionName(name)
        .originPrice(new BigDecimal("1000.00"))
        .salePrice(new BigDecimal("900.00"))
        .status(status)
        .build();
  }
}
