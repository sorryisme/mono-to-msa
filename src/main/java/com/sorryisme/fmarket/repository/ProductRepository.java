package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.Product;
import com.sorryisme.fmarket.enums.ProductStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  /** 삭제되지 않은 상품 단건. 판매중지 상품도 상세는 볼 수 있다. */
  Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);

  boolean existsByIdAndStatusNot(Long id, ProductStatus status);
}
