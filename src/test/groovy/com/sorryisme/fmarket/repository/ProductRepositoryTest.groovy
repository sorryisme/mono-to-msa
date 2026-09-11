package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.dto.request.ProductSearchDto
import com.sorryisme.fmarket.entity.Product
import com.sorryisme.fmarket.entity.ProductOption
import com.sorryisme.fmarket.enums.ProductStatus
import com.sorryisme.fmarket.testUtils.DomainFixture
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

/** 실제 MySQL 위에서 상품 검색·상태 필터를 검증한다. data.sql 의 product 1~3, product_option 1~4 를 전제로 한다. */
@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest extends Specification {

    @Autowired
    ProductRepository productRepository
    @Autowired
    ProductOptionRepository productOptionRepository
    @Autowired
    ProductReviewRepository productReviewRepository
    @Autowired
    UserRepository userRepository
    @Autowired
    EntityManager em

    def setup() {
        productRepository.save(product("스펙 검색용 티셔츠", 7, 701, ProductStatus.ON_SALE))
        productRepository.save(product("스펙 검색용 바지", 7, 702, ProductStatus.ON_SALE))
        productRepository.save(product("다른 카테고리", 8, 801, ProductStatus.ON_SALE))
        productRepository.save(product("스펙 검색용 판매중지", 7, 701, ProductStatus.SUSPENDED))
        productRepository.save(product("스펙 검색용 삭제됨", 7, 701, ProductStatus.DELETED))
        em.flush()
    }

    def "검색 조건 조합에 따라 상품이 필터링된다"() {
        when:
        Page<Product> page = productRepository.findAll(
                ProductSpecification.search(new ProductSearchDto(query, major, sub, Pageable.ofSize(50))),
                Pageable.ofSize(50))

        then:
        page.getContent()*.getProductName().containsAll(expectedNames)
        page.getContent()*.getProductName().findAll { it.startsWith("스펙 검색용") || it == "다른 카테고리" }.size() == expectedNames.size()

        where:
        query    | major | sub  | expectedNames
        "스펙 검색용" | null  | null | ["스펙 검색용 티셔츠", "스펙 검색용 바지"]
        null     | 7     | null | ["스펙 검색용 티셔츠", "스펙 검색용 바지"]
        null     | 7     | 702  | ["스펙 검색용 바지"]
        "티셔츠"    | 7     | 701  | ["스펙 검색용 티셔츠"]
        "없는이름"   | null  | null | []
    }

    def "목록 검색은 판매중지·삭제 상품을 제외한다"() {
        when:
        Page<Product> page = productRepository.findAll(
                ProductSpecification.search(new ProductSearchDto("스펙 검색용", null, null, Pageable.ofSize(50))),
                Pageable.ofSize(50))

        then:
        page.getContent()*.getStatus().every { it == ProductStatus.ON_SALE }
        !page.getContent()*.getProductName().contains("스펙 검색용 판매중지")
        !page.getContent()*.getProductName().contains("스펙 검색용 삭제됨")
    }

    def "빈 검색 조건이면 전체 상품이 페이징되어 조회된다"() {
        when:
        Page<Product> page = productRepository.findAll(
                ProductSpecification.search(new ProductSearchDto("", null, null, Pageable.ofSize(2))),
                Pageable.ofSize(2))

        then:
        page.getContent().size() == 2
        page.getTotalElements() >= 3
    }

    def "단건 조회는 삭제된 상품만 제외하고 판매중지 상품은 돌려준다"() {
        given:
        Long suspendedId = productRepository.findAll().find { it.getProductName() == "스펙 검색용 판매중지" }.getId()
        Long deletedId = productRepository.findAll().find { it.getProductName() == "스펙 검색용 삭제됨" }.getId()

        expect:
        productRepository.findByIdAndStatusNot(suspendedId, ProductStatus.DELETED).isPresent()
        productRepository.findByIdAndStatusNot(deletedId, ProductStatus.DELETED).isEmpty()
        productRepository.existsByIdAndStatusNot(suspendedId, ProductStatus.DELETED)
        !productRepository.existsByIdAndStatusNot(deletedId, ProductStatus.DELETED)
    }

    def "상품 id 로 옵션과 리뷰를 각각 조회한다"() {
        given:
        def user = userRepository.save(DomainFixture.createUser())
        productReviewRepository.save(DomainFixture.createProductReview(user.getId()))
        em.flush()

        expect:
        productOptionRepository.findAllByProductIdAndStatusNot(1L, ProductStatus.DELETED).size() >= 1
        productReviewRepository.findAllByProductId(1L).size() >= 1
    }

    def "상품 상세용 옵션 조회는 삭제된 옵션만 제외한다"() {
        given:
        ProductOption suspended = productOptionRepository.save(option(1L, "판매중지 옵션", ProductStatus.SUSPENDED))
        ProductOption deleted = productOptionRepository.save(option(1L, "삭제된 옵션", ProductStatus.DELETED))
        em.flush()

        when:
        List<Long> ids = productOptionRepository.findAllByProductIdAndStatusNot(1L, ProductStatus.DELETED)*.getId()

        then:
        ids.contains(suspended.getId())
        !ids.contains(deleted.getId())
    }

    def "주문용 옵션 조회는 판매중 옵션만 돌려준다"() {
        given:
        ProductOption suspended = productOptionRepository.save(option(1L, "판매중지 옵션", ProductStatus.SUSPENDED))
        em.flush()

        when:
        List<ProductOption> found = productOptionRepository.findAllByIdInAndStatus([1L, 2L, suspended.getId()], ProductStatus.ON_SALE)

        then:
        found*.getId().toSet() == [1L, 2L].toSet()
    }

    private static Product product(String name, int major, int sub, ProductStatus status) {
        return Product.builder().productName(name).description("d").majorCategory(major).subcategory(sub).status(status).build()
    }

    private static ProductOption option(Long productId, String name, ProductStatus status) {
        return ProductOption.builder()
                .productId(productId)
                .optionName(name)
                .originPrice(new BigDecimal("1000.00"))
                .salePrice(new BigDecimal("900.00"))
                .status(status)
                .build()
    }
}
