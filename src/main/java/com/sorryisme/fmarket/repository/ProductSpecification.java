package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.dto.request.ProductSearchDto;
import com.sorryisme.fmarket.entity.Product;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** 상품 목록 검색 조건. 값이 없는 조건은 건너뛴다(기존 동적 SQL 과 동일). */
public final class ProductSpecification {

  private ProductSpecification() {}

  public static Specification<Product> search(ProductSearchDto searchDto) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (searchDto.getQuery() != null && !searchDto.getQuery().isEmpty()) {
        predicates.add(cb.like(root.get("productName"), "%" + searchDto.getQuery() + "%"));
      }
      if (searchDto.getMajorCategory() != null) {
        predicates.add(cb.equal(root.get("majorCategory"), searchDto.getMajorCategory()));
      }
      if (searchDto.getSubcategory() != null) {
        predicates.add(cb.equal(root.get("subcategory"), searchDto.getSubcategory()));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
