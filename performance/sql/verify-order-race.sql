-- order-race 사후 검증. master 기준. 출력: check_name<TAB>PASS|FAIL|INFO<TAB>detail
-- 입력 변수: @run_id, @race_option_id, @race_initial_stock, @rounds
-- setup 이 사용자 1 로 @rounds 개의 주문(수량 1)을 만들었다.
-- 불변식: 상태 전이 성공은 주문당 하나 (PENDING 잔존 없음, CANCELLED 또는 COMPLETED),
--         최종 재고 = 초기 - COMPLETED 수 (취소된 주문의 재고는 정확히 한 번 복구).

SET @race_user_id = (SELECT `id` FROM `user` WHERE `login_id` = CONCAT('lt-', @run_id, '-1'));

SET @orders = (SELECT COUNT(*) FROM `order` WHERE `user_id` = @race_user_id);
SET @pending = (SELECT COUNT(*) FROM `order` WHERE `user_id` = @race_user_id AND `status` = 'PENDING');
SET @cancelled = (SELECT COUNT(*) FROM `order` WHERE `user_id` = @race_user_id AND `status` = 'CANCELLED');
SET @completed = (SELECT COUNT(*) FROM `order` WHERE `user_id` = @race_user_id AND `status` = 'COMPLETED');
SET @final_stock = (SELECT `quantity` FROM `inventory` WHERE `product_option_id` = @race_option_id);

SELECT 'orders_equals_rounds' AS check_name,
       IF(@orders = @rounds, 'PASS', 'FAIL'),
       CONCAT('orders=', @orders, ' rounds=', @rounds)
UNION ALL
SELECT 'no_pending_left',
       IF(@pending = 0, 'PASS', 'FAIL'),
       CONCAT('pending=', @pending)
UNION ALL
SELECT 'cancelled_plus_completed_equals_rounds',
       IF(@cancelled + @completed = @rounds, 'PASS', 'FAIL'),
       CONCAT('cancelled=', @cancelled, ' completed=', @completed)
UNION ALL
SELECT 'stock_equals_initial_minus_completed',
       IF(@final_stock = @race_initial_stock - @completed, 'PASS', 'FAIL'),
       CONCAT('final=', @final_stock, ' initial=', @race_initial_stock, ' completed=', @completed);
