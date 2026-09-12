package com.sorryisme.fmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto;
import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.ProductListResponseDto;
import com.sorryisme.fmarket.dto.response.ProductResponseDto;
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto;
import com.sorryisme.fmarket.entity.MajorCategory;
import com.sorryisme.fmarket.entity.ProductReview;
import com.sorryisme.fmarket.entity.Subcategory;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.MajorCategoryRepository;
import com.sorryisme.fmarket.repository.ProductOptionRepository;
import com.sorryisme.fmarket.repository.ProductRepository;
import com.sorryisme.fmarket.repository.ProductReviewRepository;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class ProductServiceTest {

  private final ProductRepository productRepository = mock(ProductRepository.class);
  private final MajorCategoryRepository majorCategoryRepository =
      mock(MajorCategoryRepository.class);
  private final ProductOptionRepository productOptionRepository =
      mock(ProductOptionRepository.class);
  private final ProductReviewRepository productReviewRepository =
      mock(ProductReviewRepository.class);
  private final ProductService productService =
      new ProductService(
          majorCategoryRepository,
          productRepository,
          productOptionRepository,
          productReviewRepository);

  @Test
  @DisplayName("카테고리 조회 시 대분류와 중분류가 모두 응답 DTO 로 변환된다")
  void mapsCategoriesToResponse() {
    MajorCategory major =
        MajorCategory.builder().id(1L).categoryName("대분류").description("대분류 설명").build();
    major.addSubcategory(Subcategory.builder().id(1L).categoryName("중분류1").build());
    major.addSubcategory(Subcategory.builder().id(2L).categoryName("중분류2").build());
    when(majorCategoryRepository.findAllWithSubcategories()).thenReturn(List.of(major));

    List<MajorCategoryResponse> majorCategoryList = productService.findMajorCategoryList();

    assertThat(majorCategoryList).hasSize(1);
    assertThat(majorCategoryList.get(0).getMajorCategoryName()).isEqualTo("대분류");
    assertThat(majorCategoryList.get(0).getSubcategories()).hasSize(2);
    assertThat(majorCategoryList.get(0).getSubcategories().get(0).getMajorCategoryId())
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("모든 상품 리스트와 총 상품 개수를 조회한다")
  void findsAllProductsWithTotalCount() {
    when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(DomainFixture.createProduct())));

    Page<ProductListResponseDto> result =
        productService.findAllProductList(createProductSearchDto());

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getProductName()).isEqualTo("제품명1");
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("ID로 상품 조회 시 존재하지 않거나 삭제됐으면 예외가 발생한다")
  void throwsWhenProductMissing() {
    when(productRepository.findByIdAndStatusNot(1L, ProductStatus.DELETED))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.findProductById(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  @DisplayName("ID로 상품 조회 시 상품이 존재하면 옵션·리뷰를 합쳐 반환한다")
  void combinesOptionsAndReviews() {
    when(productRepository.findByIdAndStatusNot(1L, ProductStatus.DELETED))
        .thenReturn(Optional.of(DomainFixture.createProduct()));
    when(productOptionRepository.findAllByProductIdAndStatusNot(1L, ProductStatus.DELETED))
        .thenReturn(
            List.of(
                DomainFixture.createProductOption(101L, 1L, "옵션 1", "4500"),
                DomainFixture.createProductOption(102L, 1L, "옵션 2", "6500")));
    when(productReviewRepository.findAllByProductId(1L))
        .thenReturn(
            List.of(
                DomainFixture.createProductReview(1001L),
                DomainFixture.createProductReview(1002L)));

    ProductResponseDto result = productService.findProductById(1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getDescription()).isEqualTo("상품설명");
    assertThat(result.getOptions()).hasSize(2);
    assertThat(result.getReviews()).hasSize(2);
  }

  @Test
  @DisplayName("리뷰 생성 시 상품이 존재하지 않거나 삭제됐으면 예외가 발생한다")
  void rejectsReviewForMissingProduct() {
    when(productRepository.existsByIdAndStatusNot(1L, ProductStatus.DELETED)).thenReturn(false);

    assertThatThrownBy(() -> productService.createReview(createProductReviewRequestDto(), 1L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    verify(productReviewRepository, never()).save(any(ProductReview.class));
  }

  @Test
  @DisplayName("리뷰 생성 시 성공적으로 저장된다")
  void savesReview() {
    when(productRepository.existsByIdAndStatusNot(1L, ProductStatus.DELETED)).thenReturn(true);
    when(productReviewRepository.save(any(ProductReview.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ProductReviewResponseDto result =
        productService.createReview(createProductReviewRequestDto(), 1L, 1L);

    assertThat(result.getProductId()).isEqualTo(1L);
    assertThat(result.getUserId()).isEqualTo(1L);
    assertThat(result.getReviewText()).isEqualTo("리뷰 작성 DTO");
    assertThat(result.getRating()).isEqualTo(5);
  }

  private static ProductSearchDto createProductSearchDto() {
    return ProductSearchDto.builder().pageable(Pageable.ofSize(10)).query("").build();
  }

  private static ProductReviewRequestDto createProductReviewRequestDto() {
    return ProductReviewRequestDto.builder()
        .productId(1L)
        .reviewText("리뷰 작성 DTO")
        .rating(5)
        .build();
  }
}
