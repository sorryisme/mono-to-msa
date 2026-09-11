package com.sorryisme.fmarket.controller;

import com.sorryisme.fmarket.annotation.LoginUserId;
import com.sorryisme.fmarket.annotation.RequireLogin;
import com.sorryisme.fmarket.common.dto.ResponseDto;
import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto;
import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.ProductListResponseDto;
import com.sorryisme.fmarket.dto.response.ProductResponseDto;
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto;
import com.sorryisme.fmarket.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {

  private final ProductService productService;

  @GetMapping("/products/category")
  public List<MajorCategoryResponse> findMajorCategoryList() {
    return productService.findMajorCategoryList();
  }

  @PostMapping("/products/search")
  public ResponseDto<Page<ProductListResponseDto>> findAllProductList(
      @RequestBody(required = false) ProductSearchDto productSearchDto,
      @PageableDefault Pageable pageable) {
    Page<ProductListResponseDto> searchResult =
        productService.findAllProductList(ProductSearchDto.from(productSearchDto, pageable));
    return ResponseDto.success(searchResult);
  }

  @GetMapping("/products/{id}")
  public ResponseDto<ProductResponseDto> findProductById(@PathVariable Long id) {
    ProductResponseDto productResponseDto = productService.findProductById(id);
    return ResponseDto.success(productResponseDto);
  }

  @PostMapping("/{productId}/reviews")
  @RequireLogin
  public ResponseDto<ProductReviewResponseDto> createReview(
      @RequestBody ProductReviewRequestDto requestDto,
      @PathVariable Long productId,
      @LoginUserId Long userId) {
    ProductReviewResponseDto productReview =
        productService.createReview(requestDto, productId, userId);
    return ResponseDto.success(productReview);
  }
}
