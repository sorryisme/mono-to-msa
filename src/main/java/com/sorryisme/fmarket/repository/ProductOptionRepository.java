package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.ProductOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

  List<ProductOption> findAllByProductId(Long productId);
}
