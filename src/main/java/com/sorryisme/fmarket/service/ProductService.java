package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.common.PageableSupport;
import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto;
import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.ProductListResponseDto;
import com.sorryisme.fmarket.dto.response.ProductResponseDto;
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto;
import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.entity.ProductReview;
import com.sorryisme.fmarket.exception.NotFoundDataException;
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

  @Transactional(readOnly = true)
  public ProductResponseDto findProductById(Long id) {
    Product product =
        productRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 제품입니다."));

    return ProductResponseDto.of(
        product,
        productOptionRepository.findAllByProductId(id),
        productReviewRepository.findAllByProductId(id));
  }

  @Transactional
  public ProductReviewResponseDto createReview(
      ProductReviewRequestDto reviewRequestDto, Long productId, Long userId) {

    if (!productRepository.existsById(productId)) throw new NotFoundDataException("찾을 수 없는 제품입니다.");

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
