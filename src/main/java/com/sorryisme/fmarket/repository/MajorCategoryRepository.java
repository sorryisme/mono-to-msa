package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.MajorCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MajorCategoryRepository extends JpaRepository<MajorCategory, Long> {

  /** 중분류가 하나 이상 있는 대분류만, 중분류까지 한 번에 가져온다(inner join fetch). */
  @Query("select distinct m from MajorCategory m join fetch m.subcategories order by m.id")
  List<MajorCategory> findAllWithSubcategories();
}
