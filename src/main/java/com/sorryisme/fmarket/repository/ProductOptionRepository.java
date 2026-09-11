package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.ProductOption;
import com.sorryisme.fmarket.enums.ProductStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

  /** 상품 상세용. 삭제된 옵션은 제외하고 판매중지 옵션은 남긴다. */
  List<ProductOption> findAllByProductIdAndStatusNot(Long productId, ProductStatus status);

  /** 주문용. 요청한 id 중 주어진 상태(보통 ON_SALE)인 옵션만 돌려준다. 빠진 id 가 있으면 호출부에서 주문을 거절한다. */
  List<ProductOption> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);
}
