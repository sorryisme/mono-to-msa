package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.MajorCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MajorCategoryRepository extends JpaRepository<MajorCategory, Long> {

  /**
   * 중분류가 하나 이상 있는 대분류만, 중분류까지 한 번에 가져온다(inner join fetch). Hibernate 6 부터 fetch join 의 부모 중복은 자동
   * 제거되고 JPQL 의 distinct 는 SQL 로 그대로 전달되므로 붙이지 않는다(TEXT 컬럼 DISTINCT 비용 회피).
   */
  @Query("select m from MajorCategory m join fetch m.subcategories order by m.id")
  List<MajorCategory> findAllWithSubcategories();
}
