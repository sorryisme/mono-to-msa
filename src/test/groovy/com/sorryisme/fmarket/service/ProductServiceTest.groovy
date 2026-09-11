package com.sorryisme.fmarket.service

import com.sorryisme.fmarket.dto.request.ProductReviewRequestDto
import com.sorryisme.fmarket.dto.request.ProductSearchDto
import com.sorryisme.fmarket.dto.response.ProductListResponseDto
import com.sorryisme.fmarket.dto.response.ProductResponseDto
import com.sorryisme.fmarket.dto.response.ProductReviewResponseDto
import com.sorryisme.fmarket.entity.MajorCategory
import com.sorryisme.fmarket.entity.ProductReview
import com.sorryisme.fmarket.entity.Subcategory
import com.sorryisme.fmarket.exception.NotFoundDataException
import com.sorryisme.fmarket.repository.MajorCategoryRepository
import com.sorryisme.fmarket.repository.ProductOptionRepository
import com.sorryisme.fmarket.repository.ProductRepository
import com.sorryisme.fmarket.repository.ProductReviewRepository
import com.sorryisme.fmarket.testUtils.DomainFixture
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import spock.lang.Specification as Spec

class ProductServiceTest extends Spec {

    ProductRepository productRepository = Mock()
    MajorCategoryRepository majorCategoryRepository = Mock()
    ProductOptionRepository productOptionRepository = Mock()
    ProductReviewRepository productReviewRepository = Mock()
    ProductService productService = new ProductService(
            majorCategoryRepository, productRepository, productOptionRepository, productReviewRepository)

    def "카테고리 조회 시 대분류와 중분류가 모두 응답 DTO 로 변환된다"() {
        given:
        MajorCategory major = MajorCategory.builder().id(1L).categoryName("대분류").description("대분류 설명").build()
        major.addSubcategory(Subcategory.builder().id(1L).categoryName("중분류1").build())
        major.addSubcategory(Subcategory.builder().id(2L).categoryName("중분류2").build())
        majorCategoryRepository.findAllWithSubcategories() >> [major]

        when:
        def majorCategoryList = productService.findMajorCategoryList()

        then:
        majorCategoryList.size() == 1
        majorCategoryList[0].getMajorCategoryName() == "대분류"
        majorCategoryList[0].getSubcategories().size() == 2
        majorCategoryList[0].getSubcategories()[0].getMajorCategoryId() == 1L
    }

    def "모든 상품 리스트와 총 상품 개수를 조회한다"() {
        given:
        productRepository.findAll(_ as Specification, _ as Pageable) >> new PageImpl<>([DomainFixture.createProduct()])

        when:
        Page<ProductListResponseDto> result = productService.findAllProductList(createProductSearchDto())

        then:
        result.getContent().size() == 1
        result.getContent()[0].getProductName() == "제품명1"
        result.getTotalElements() == 1
    }

    def "ID로 상품 조회 시 존재하지 않으면 예외가 발생한다"() {
        given:
        productRepository.findById(1L) >> Optional.empty()

        when:
        productService.findProductById(1L)

        then:
        def e = thrown(NotFoundDataException.class)
        e.getMessage() == "찾을 수 없는 제품입니다."
    }

    def "ID로 상품 조회 시 상품이 존재하면 옵션·리뷰를 합쳐 반환한다"() {
        given:
        productRepository.findById(1L) >> Optional.of(DomainFixture.createProduct())
        productOptionRepository.findAllByProductId(1L) >> [
                DomainFixture.createProductOption(101L, 1L, "옵션 1", "4500"),
                DomainFixture.createProductOption(102L, 1L, "옵션 2", "6500")
        ]
        productReviewRepository.findAllByProductId(1L) >> [
                DomainFixture.createProductReview(1001L),
                DomainFixture.createProductReview(1002L)
        ]

        when:
        ProductResponseDto result = productService.findProductById(1L)

        then:
        result.getId() == 1L
        result.getDescription() == "상품설명"
        result.getOptions().size() == 2
        result.getReviews().size() == 2
    }

    def "리뷰 생성 시 상품이 존재하지 않으면 예외가 발생한다"() {
        given:
        productRepository.existsById(1L) >> false

        when:
        productService.createReview(createProductReviewRequestDto(), 1L, 1L)

        then:
        def e = thrown(NotFoundDataException.class)
        e.getMessage() == "찾을 수 없는 제품입니다."
        0 * productReviewRepository.save(_)
    }

    def "리뷰 생성 시 성공적으로 저장된다"() {
        given:
        productRepository.existsById(1L) >> true
        productReviewRepository.save(_ as ProductReview) >> { ProductReview r -> r }

        when:
        ProductReviewResponseDto result = productService.createReview(createProductReviewRequestDto(), 1L, 1L)

        then:
        result.getProductId() == 1L
        result.getUserId() == 1L
        result.getReviewText() == "리뷰 작성 DTO"
        result.getRating() == 5
    }

    private static ProductSearchDto createProductSearchDto() {
        return ProductSearchDto.builder()
                .pageable(Pageable.ofSize(10))
                .query("")
                .build()
    }

    private static ProductReviewRequestDto createProductReviewRequestDto() {
        return ProductReviewRequestDto.builder()
                .productId(1L)
                .reviewText("리뷰 작성 DTO")
                .rating(5)
                .build()
    }
}
