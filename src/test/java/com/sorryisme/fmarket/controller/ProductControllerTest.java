package com.sorryisme.fmarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sorryisme.fmarket.common.SessionManager;
import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto;
import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import com.sorryisme.fmarket.dto.response.ProductListResponseDto;
import com.sorryisme.fmarket.dto.response.ProductResponseDto;
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto;
import com.sorryisme.fmarket.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** ProductController 의 라우팅·바인딩·서비스 위임을 본다. */
@WebMvcTest(controllers = ProductController.class)
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProductService productService;
  @MockitoBean private SessionManager sessionManager;

  @Test
  @DisplayName("대분류 카테고리 목록을 조회한다")
  void findsMajorCategoryList() throws Exception {
    when(productService.findMajorCategoryList())
        .thenReturn(
            List.of(
                MajorCategoryResponse.builder()
                    .majorCategoryId(1L)
                    .majorCategoryName("식품")
                    .build()));

    MvcResult result = mockMvc.perform(get("/api/v1/products/category")).andReturn();

    verify(productService).findMajorCategoryList();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("상품 검색은 검색 조건에 페이지 정보를 채워 서비스에 넘긴다")
  void fillsPageableIntoSearchCondition() throws Exception {
    when(productService.findAllProductList(any(ProductSearchDto.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(ProductListResponseDto.builder().id(1L).productName("사과").build()),
                PageRequest.of(1, 5),
                6));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/products/search")
                    .param("page", "1")
                    .param("size", "5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"사과\",\"majorCategory\":1}"))
            .andReturn();

    ArgumentCaptor<ProductSearchDto> captor = ArgumentCaptor.forClass(ProductSearchDto.class);
    verify(productService).findAllProductList(captor.capture());
    assertThat(captor.getValue().getQuery()).isEqualTo("사과");
    assertThat(captor.getValue().getMajorCategory()).isEqualTo(1);
    assertThat(captor.getValue().getPageable().getPageNumber()).isEqualTo(1);
    assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(5);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("상품 검색을 본문 없이 호출하면 조건 없는 전체 목록으로 조회한다")
  void searchesWithoutBody() throws Exception {
    when(productService.findAllProductList(any(ProductSearchDto.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    MvcResult result = mockMvc.perform(post("/api/v1/products/search")).andReturn();

    ArgumentCaptor<ProductSearchDto> captor = ArgumentCaptor.forClass(ProductSearchDto.class);
    verify(productService).findAllProductList(captor.capture());
    assertThat(captor.getValue().getQuery()).isNull();
    assertThat(captor.getValue().getMajorCategory()).isNull();
    assertThat(captor.getValue().getSubcategory()).isNull();
    assertThat(captor.getValue().getPageable()).isNotNull();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("상품 단건 조회는 경로 변수 id 로 조회한다")
  void findsProductByPathVariable() throws Exception {
    when(productService.findProductById(3L))
        .thenReturn(ProductResponseDto.builder().id(3L).description("설명").build());

    MvcResult result = mockMvc.perform(get("/api/v1/products/3")).andReturn();

    verify(productService).findProductById(3L);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("리뷰 작성은 경로 변수 productId 와 로그인 유저 ID 를 서비스에 넘기고 201 로 응답한다")
  void createsReviewWithPathVariableAndLoginUserId() throws Exception {
    when(sessionManager.getUserId()).thenReturn(42L);
    when(productService.createReview(any(ProductReviewRequestDto.class), eq(3L), eq(42L)))
        .thenReturn(ProductReviewResponseDto.builder().reviewId(9L).productId(3L).build());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/3/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\":5,\"reviewText\":\"좋아요\"}"))
            .andReturn();

    ArgumentCaptor<ProductReviewRequestDto> captor =
        ArgumentCaptor.forClass(ProductReviewRequestDto.class);
    verify(productService).createReview(captor.capture(), eq(3L), eq(42L));
    assertThat(captor.getValue().getRating()).isEqualTo(5);
    assertThat(captor.getValue().getReviewText()).isEqualTo("좋아요");
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
  }
}
