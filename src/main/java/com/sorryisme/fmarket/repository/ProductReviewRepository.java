package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.ProductReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

  List<ProductReview> findAllByProductId(Long productId);
}
