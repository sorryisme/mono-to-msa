package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.Order;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
   * 주문 행에 비관적 락(SELECT ... FOR UPDATE)을 걸고 주문 상세까지 한 번에 가져온다. 상태 전이 전 동시 변경을 막고, 취소 시 상세를 지연 로딩하느라
   * SELECT 가 한 번 더 나가지 않게 한다. MySQL 은 조인된 order_detail 행도 함께 잠근다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o left join fetch o.orderDetails where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
