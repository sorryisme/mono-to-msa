-- idempotency 사후 검증. master 기준. 출력: check_name<TAB>PASS|FAIL|INFO<TAB>detail
-- 입력 변수:
--   @run_id, @key_prefix (24자), @groups, @fail_vus, @ok_option_id, @ok_initial_stock,
--   @retry_contract ('' | 'RETRY_ALLOWED' | 'PERMANENT_REJECT')
-- 사용자 번호: duplicate 그룹 g → 사용자 g (1..@groups), failurePath v → 사용자 @groups+v
-- 불변식:
--   (a) 그룹당 주문 정확히 1건, 키 저장 @groups 건, ok 옵션 재고 = 초기 - @groups
--   (b) failurePath 사용자의 주문 0건 (선행 옵션 차감·주문·상세가 함께 롤백됨). 키 잔존 여부는 계약에 따라 판정.

CREATE TEMPORARY TABLE lt_users AS
SELECT `id`, CAST(SUBSTRING_INDEX(`login_id`, '-', -1) AS UNSIGNED) AS n
FROM `user`
WHERE `login_id` LIKE CONCAT('lt-', @run_id, '-%') AND `login_id` <> CONCAT('lt-', @run_id, '-seed');

SET @dup_keys = (SELECT COUNT(*) FROM `idempotency_keys` WHERE `idempotency_key` LIKE CONCAT(@key_prefix, '1%'));
SET @fail_keys = (SELECT COUNT(*) FROM `idempotency_keys` WHERE `idempotency_key` LIKE CONCAT(@key_prefix, '2%'));
SET @dup_orders = (
  SELECT COUNT(*) FROM `order` o WHERE o.`user_id` IN (SELECT `id` FROM lt_users WHERE n BETWEEN 1 AND @groups));
SET @dup_users_not_exactly_one = (
  SELECT COUNT(*) FROM (
    SELECT u.`id`, COUNT(o.`id`) AS c
    FROM lt_users u LEFT JOIN `order` o ON o.`user_id` = u.`id`
    WHERE u.n BETWEEN 1 AND @groups
    GROUP BY u.`id` HAVING c <> 1) t);
SET @fail_orders = (
  SELECT COUNT(*) FROM `order` o
  WHERE o.`user_id` IN (SELECT `id` FROM lt_users WHERE n BETWEEN @groups + 1 AND @groups + @fail_vus));
SET @ok_stock = (SELECT `quantity` FROM `inventory` WHERE `product_option_id` = @ok_option_id);

SELECT 'dup_keys_stored_equals_groups' AS check_name,
       IF(@dup_keys = @groups, 'PASS', 'FAIL'),
       CONCAT('keys=', @dup_keys, ' groups=', @groups)
UNION ALL
SELECT 'dup_orders_equals_groups',
       IF(@dup_orders = @groups, 'PASS', 'FAIL'),
       CONCAT('orders=', @dup_orders, ' groups=', @groups)
UNION ALL
SELECT 'dup_each_user_exactly_one_order',
       IF(@dup_users_not_exactly_one = 0, 'PASS', 'FAIL'),
       CONCAT('users_not_exactly_one=', @dup_users_not_exactly_one)
UNION ALL
SELECT 'ok_stock_equals_initial_minus_groups',
       IF(@ok_stock = @ok_initial_stock - @groups, 'PASS', 'FAIL'),
       CONCAT('stock=', @ok_stock, ' expected=', @ok_initial_stock - @groups, ' (failurePath 차감분은 롤백되어야 함)')
UNION ALL
SELECT 'fail_users_have_no_orders',
       IF(@fail_orders = 0, 'PASS', 'FAIL'),
       CONCAT('orders=', @fail_orders)
UNION ALL
SELECT 'fail_keys_per_retry_contract',
       CASE @retry_contract
         WHEN 'RETRY_ALLOWED' THEN IF(@fail_keys = 0, 'PASS', 'FAIL')
         WHEN 'PERMANENT_REJECT' THEN IF(@fail_keys = @fail_vus, 'PASS', 'FAIL')
         ELSE 'INFO'
       END,
       CONCAT('keys_remaining=', @fail_keys, ' fail_vus=', @fail_vus, ' contract=', IF(@retry_contract = '', '(관측만)', @retry_contract),
              IF(@fail_keys = 0, ' → 키가 주문과 함께 롤백됨(RETRY_ALLOWED)', IF(@fail_keys = @fail_vus, ' → 키만 커밋됨(PERMANENT_REJECT)', ' → 일부만 남음')));
