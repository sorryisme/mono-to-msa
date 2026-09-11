package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.Order;
import com.sorryisme.fmarket.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

  Page<Order> findByUserId(Long userId, Pageable pageable);

  /** 주문일이 from 이상 to 미만인 사용자의 주문. 날짜 단위 조회는 to 를 다음날 00:00 로 넘긴다. */
  @Query(
      "select o from Order o "
          + "where o.userId = :userId and o.orderDate >= :from and o.orderDate < :to")
  Page<Order> findByUserIdAndOrderDateIn(
      @Param("userId") Long userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

  /** 주문 상세까지 한 번에 가져온다(상세 응답용). */
  @EntityGraph(attributePaths = "orderDetails")
  Optional<Order> findWithDetailsById(Long id);

  /**
   * 현재 상태가 {@code from} 일 때만 {@code to} 로 바꾸는 조건부 상태 전이.
   *
   * <p>비관적 락으로 주문 행을 잡고 상태를 확인·변경하는 대신 한 UPDATE 로 처리한다. 사용자 취소, 결제 실패 처리, (추후) 결제 만료 배치가 동시에 들어와도
   * 상태 전이에 성공한 요청은 하나뿐이므로 재고 복구 같은 후속 작업이 두 번 실행되지 않는다.
   *
   * @return 1 이면 전이 성공, 0 이면 이미 다른 상태로 바뀌었거나 주문이 없음
   */
  @Modifying
  @Query("update Order o set o.status = :to where o.id = :id and o.status = :from")
  int updateStatusIfCurrent(
      @Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
