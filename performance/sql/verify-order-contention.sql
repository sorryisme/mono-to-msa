-- order-contention 사후 검증. master 의 커밋 결과 기준. 출력: check_name<TAB>PASS|FAIL|INFO<TAB>detail
-- 입력 변수:
--   @option_id, @initial_stock,
--   @k6_orders_created, @k6_orders_created_qty, @k6_transport_errors
-- 불변식:
--   성공 주문 수량 합 <= 초기 재고 / 최종 재고 >= 0 / 초기 - 최종 = 성공 수량 합 (취소 없음) /
--   롤백된 요청의 주문이 남지 않음 (DB 주문 수 = k6 201 수, 응답 유실분만 상한 여유)

SET @sold_qty = (
  SELECT IFNULL(SUM(od.`quantity`), 0) FROM `order_detail` od WHERE od.`product_option_id` = @option_id);
SET @db_orders = (
  SELECT COUNT(DISTINCT od.`order_id`) FROM `order_detail` od WHERE od.`product_option_id` = @option_id);
SET @final_stock = (SELECT `quantity` FROM `inventory` WHERE `product_option_id` = @option_id);

SELECT 'sold_qty_le_initial_stock' AS check_name,
       IF(@sold_qty <= @initial_stock, 'PASS', 'FAIL'),
       CONCAT('sold=', @sold_qty, ' initial=', @initial_stock)
UNION ALL
SELECT 'final_stock_non_negative',
       IF(@final_stock >= 0, 'PASS', 'FAIL'),
       CONCAT('final=', @final_stock)
UNION ALL
SELECT 'stock_delta_equals_sold_qty',
       IF(@initial_stock - @final_stock = @sold_qty, 'PASS', 'FAIL'),
       CONCAT('initial=', @initial_stock, ' final=', @final_stock, ' sold=', @sold_qty)
UNION ALL
SELECT 'db_orders_match_k6_created',
       IF(@db_orders >= @k6_orders_created AND @db_orders <= @k6_orders_created + @k6_transport_errors, 'PASS', 'FAIL'),
       CONCAT('db=', @db_orders, ' k6_created=', @k6_orders_created, ' k6_transport_errors=', @k6_transport_errors)
UNION ALL
SELECT 'sold_qty_matches_k6_qty',
       IF(@k6_transport_errors = 0, IF(@sold_qty = @k6_orders_created_qty, 'PASS', 'FAIL'), 'INFO'),
       CONCAT('sold=', @sold_qty, ' k6_qty=', @k6_orders_created_qty, IF(@k6_transport_errors = 0, '', ' (응답 유실 있음, 판정 제외)'));
