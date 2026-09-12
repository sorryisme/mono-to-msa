package com.sorryisme.fmarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.entity.MajorCategory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MajorCategoryRepositoryTest {

  @Autowired private MajorCategoryRepository majorCategoryRepository;

  @Test
  @DisplayName("대분류 조회 시 중분류가 fetch join 으로 함께 조회된다")
  void fetchesSubcategoriesWithJoin() {
    List<MajorCategory> majorCategories = majorCategoryRepository.findAllWithSubcategories();

    assertThat(majorCategories).hasSize(8);
    assertThat(majorCategories.get(0).getId()).isEqualTo(1L);
    assertThat(majorCategories.get(0).getCategoryName()).isEqualTo("브랜드 패션");
    assertThat(majorCategories.get(0).getSubcategories()).hasSize(10);
    assertThat(majorCategories.get(0).getSubcategories().get(0).getMajorCategory().getId())
        .isEqualTo(1L);
  }
}
