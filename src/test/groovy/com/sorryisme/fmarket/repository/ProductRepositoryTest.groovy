package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.dto.request.ProductSearchDto
import com.sorryisme.fmarket.entity.Product
import com.sorryisme.fmarket.testUtils.DomainFixture
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

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
        productRepository.save(Product.builder().productName("스펙 검색용 티셔츠").description("d").majorCategory(7).subcategory(701).build())
        productRepository.save(Product.builder().productName("스펙 검색용 바지").description("d").majorCategory(7).subcategory(702).build())
        productRepository.save(Product.builder().productName("다른 카테고리").description("d").majorCategory(8).subcategory(801).build())
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

    def "빈 검색 조건이면 전체 상품이 페이징되어 조회된다"() {
        when:
        Page<Product> page = productRepository.findAll(
                ProductSpecification.search(new ProductSearchDto("", null, null, Pageable.ofSize(2))),
                Pageable.ofSize(2))

        then:
        page.getContent().size() == 2
        page.getTotalElements() >= 3
    }

    def "상품 id 로 옵션과 리뷰를 각각 조회한다"() {
        given:
        def user = userRepository.save(DomainFixture.createUser())
        productReviewRepository.save(DomainFixture.createProductReview(user.getId()))
        em.flush()

        expect:
        productOptionRepository.findAllByProductId(1L).size() >= 1
        productReviewRepository.findAllByProductId(1L).size() >= 1
    }

    def "id 목록으로 옵션을 조회한다"() {
        expect:
        productOptionRepository.findAllById([1L, 2L]).size() == 2
    }
}
