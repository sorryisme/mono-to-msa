package com.sorryisme.fmarket.repository

import com.sorryisme.fmarket.entity.MajorCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@DataJpaTest
@ContextConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MajorCategoryRepositoryTest extends Specification {

    @Autowired
    MajorCategoryRepository majorCategoryRepository

    def "대분류 조회 시 중분류가 fetch join 으로 함께 조회된다"() {
        when:
        List<MajorCategory> majorCategories = majorCategoryRepository.findAllWithSubcategories()

        then:
        majorCategories.size() == 8
        majorCategories[0].getId() == 1L
        majorCategories[0].getCategoryName() == "브랜드 패션"
        majorCategories[0].getSubcategories().size() == 10
        majorCategories[0].getSubcategories()[0].getMajorCategory().getId() == 1L
    }
}
