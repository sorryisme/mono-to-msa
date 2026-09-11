package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.common.PageableSupport;
import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto;
import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.ProductListResponseDto;
import com.sorryisme.fmarket.dto.response.ProductResponseDto;
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto;
import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.entity.ProductReview;
import com.sorryisme.fmarket.enums.ProductStatus;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.MajorCategoryRepository;
import com.sorryisme.fmarket.repository.ProductOptionRepository;
import com.sorryisme.fmarket.repository.ProductRepository;
import com.sorryisme.fmarket.repository.ProductReviewRepository;
import com.sorryisme.fmarket.repository.ProductSpecification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final MajorCategoryRepository majorCategoryRepository;
  private final ProductRepository productRepository;
  private final ProductOptionRepository productOptionRepository;
  private final ProductReviewRepository productReviewRepository;

  @Transactional(readOnly = true)
  public List<MajorCategoryResponse> findMajorCategoryList() {
    return majorCategoryRepository.findAllWithSubcategories().stream()
        .map(MajorCategoryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<ProductListResponseDto> findAllProductList(ProductSearchDto productSearchDto) {
    return productRepository
        .findAll(
            ProductSpecification.search(productSearchDto),
            PageableSupport.withStableSort(productSearchDto.getPageable()))
        .map(ProductListResponseDto::from);
  }

  /** 삭제된 상품은 404 로 취급한다. 판매중지 상품은 상세를 보여주되 옵션도 삭제된 것만 뺀다. */
  @Transactional(readOnly = true)
  public ProductResponseDto findProductById(Long id) {
    Product product =
        productRepository
            .findByIdAndStatusNot(id, ProductStatus.DELETED)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    return ProductResponseDto.of(
        product,
        productOptionRepository.findAllByProductIdAndStatusNot(id, ProductStatus.DELETED),
        productReviewRepository.findAllByProductId(id));
  }

  @Transactional
  public ProductReviewResponseDto createReview(
      ProductReviewRequestDto reviewRequestDto, Long productId, Long userId) {

    if (!productRepository.existsByIdAndStatusNot(productId, ProductStatus.DELETED))
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

    ProductReview productReview =
        ProductReview.builder()
            .productId(productId)
            .userId(userId)
            .reviewText(reviewRequestDto.getReviewText())
            .rating(reviewRequestDto.getRating())
            .build();

    return ProductReviewResponseDto.from(productReviewRepository.save(productReview));
  }
}
