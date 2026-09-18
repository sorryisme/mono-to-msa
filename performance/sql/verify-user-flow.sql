-- user-flow 사후 검증. master 에서 실행한다. 출력: check_name<TAB>PASS|FAIL|INFO<TAB>detail
-- 입력 변수(스크립트가 앞에 붙인다):
--   @run_id, @flow_product_id, @big_stock,
--   @k6_orders_created, @k6_orders_created_qty, @k6_transport_errors
-- 응답이 유실된 요청(transport error)은 DB 에 커밋됐을 수 있으므로 상한 여유로만 허용하고 별도 INFO 로 남긴다.

CREATE TEMPORARY TABLE lt_users AS
SELECT `id` FROM `user`
WHERE `login_id` LIKE CONCAT('lt-', @run_id, '-%') AND `login_id` <> CONCAT('lt-', @run_id, '-seed');

CREATE TEMPORARY TABLE lt_flow_options AS
SELECT `id` FROM `product_option` WHERE `product_id` = @flow_product_id;

SET @db_orders = (
  SELECT COUNT(DISTINCT o.`id`) FROM `order` o
  JOIN `order_detail` od ON od.`order_id` = o.`id`
  WHERE o.`user_id` IN (SELECT `id` FROM lt_users) AND od.`product_option_id` IN (SELECT `id` FROM lt_flow_options));
SET @db_order_qty = (
  SELECT IFNULL(SUM(od.`quantity`), 0) FROM `order` o
  JOIN `order_detail` od ON od.`order_id` = o.`id`
  WHERE o.`user_id` IN (SELECT `id` FROM lt_users) AND od.`product_option_id` IN (SELECT `id` FROM lt_flow_options));
SET @stock_delta = (
  SELECT SUM(@big_stock - i.`quantity`) FROM `inventory` i WHERE i.`product_option_id` IN (SELECT `id` FROM lt_flow_options));
SET @non_pending = (
  SELECT COUNT(*) FROM `order` o WHERE o.`user_id` IN (SELECT `id` FROM lt_users) AND o.`status` <> 'PENDING');

SELECT 'db_orders_match_k6_created' AS check_name,
       IF(@db_orders >= @k6_orders_created AND @db_orders <= @k6_orders_created + @k6_transport_errors, 'PASS', 'FAIL'),
       CONCAT('db=', @db_orders, ' k6_created=', @k6_orders_created, ' k6_transport_errors=', @k6_transport_errors)
UNION ALL
SELECT 'stock_delta_equals_ordered_qty',
       IF(@stock_delta = @db_order_qty, 'PASS', 'FAIL'),
       CONCAT('stock_delta=', @stock_delta, ' ordered_qty=', @db_order_qty, ' k6_qty=', @k6_orders_created_qty)
UNION ALL
SELECT 'all_orders_pending',
       IF(@non_pending = 0, 'PASS', 'FAIL'),
       CONCAT('non_pending=', @non_pending)
UNION ALL
SELECT 'lost_responses',
       IF(@k6_transport_errors = 0, 'PASS', 'INFO'),
       CONCAT('transport_errors=', @k6_transport_errors, ' (0 이 아니면 db_orders 는 상한 여유로 판정됨)');
