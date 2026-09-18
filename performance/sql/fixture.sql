-- 부하 테스트 fixture. scripts/load-test.sh 가 아래 세션 변수를 앞에 붙여 master 에서 실행한다.
--   @run_id            실행 ID (영숫자)
--   @user_count        만들 사용자 수
--   @contention_stock  재고 경합용 옵션의 초기 재고
--   @big_stock         "충분한 재고" 옵션의 초기 재고
-- 선행 조건: 시드 사용자 lt-<run_id>-seed 가 회원가입 API 로 만들어져 있어야 한다 (performance/k6/seed.js).
-- 마지막 SELECT 가 name<TAB>value 행을 출력하고, 스크립트가 이를 LT_* 환경변수로 k6 와 검증 SQL 에 넘긴다.
--
-- sql_mode 에 ANSI_QUOTES 가 있어 문자열은 작은따옴표, 식별자는 백틱으로 쓴다.

SET SESSION cte_max_recursion_depth = 100000;

-- ---------- 사용자 ----------
-- 시드 계정의 BCrypt 해시·salt 를 그대로 복사한다. 해시에 salt 가 포함돼 있으므로 같은 비밀번호로 전원 로그인된다.
-- 회원가입 API 는 name+phone 중복을 거절하므로 둘 다 사용자 번호로 고유하게 만든다.
INSERT INTO `user` (`login_id`, `password`, `salt`, `name`, `email`, `phone_number`, `role`)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < @user_count
)
SELECT CONCAT('lt-', @run_id, '-', seq.n),
       s.`password`,
       s.`salt`,
       CONCAT('lt-', @run_id, '-', seq.n),
       CONCAT('lt-', @run_id, '-', seq.n, '@example.com'),
       CONCAT('019', LPAD(seq.n, 8, '0')),
       'USER'
FROM seq
CROSS JOIN (SELECT `password`, `salt` FROM `user` WHERE `login_id` = CONCAT('lt-', @run_id, '-seed')) s;

-- ---------- 일반 흐름용: 상품 1 + 옵션 20 (재고 충분) ----------
INSERT INTO `product` (`product_name`, `description`, `thumbnail`, `major_category`, `subcategory`, `catalog`)
VALUES (CONCAT('lt-', @run_id, '-flow'), '부하 테스트 일반 흐름용 상품', 'https://example.com/lt-flow.jpg', 1, 101, 'loadtest');
SET @flow_product_id = LAST_INSERT_ID();

INSERT INTO `product_option` (`product_id`, `option_name`, `origin_price`, `sale_price`)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT @flow_product_id, CONCAT('flow-', n), 1000.00, 900.00 FROM seq;

INSERT INTO `inventory` (`product_option_id`, `quantity`)
SELECT `id`, @big_stock FROM `product_option` WHERE `product_id` = @flow_product_id;

-- ---------- 재고 경합용: 상품 1 + 옵션 1 (한정 재고) ----------
INSERT INTO `product` (`product_name`, `description`, `thumbnail`, `major_category`, `subcategory`, `catalog`)
VALUES (CONCAT('lt-', @run_id, '-contention'), '부하 테스트 재고 경합용 상품', 'https://example.com/lt-contention.jpg', 1, 101, 'loadtest');
SET @contention_product_id = LAST_INSERT_ID();
INSERT INTO `product_option` (`product_id`, `option_name`, `origin_price`, `sale_price`)
VALUES (@contention_product_id, 'contention', 1000.00, 900.00);
SET @contention_option_id = LAST_INSERT_ID();
INSERT INTO `inventory` (`product_option_id`, `quantity`) VALUES (@contention_option_id, @contention_stock);

-- ---------- 멱등성용: 상품 1 + 옵션 2 (선행 옵션 재고 충분, 후순위 옵션 재고 0) ----------
-- createOrder 가 옵션 ID 오름차순으로 차감하므로 ok 옵션을 먼저 만들어 ID 가 작게 한다.
INSERT INTO `product` (`product_name`, `description`, `thumbnail`, `major_category`, `subcategory`, `catalog`)
VALUES (CONCAT('lt-', @run_id, '-idem'), '부하 테스트 멱등성용 상품', 'https://example.com/lt-idem.jpg', 1, 101, 'loadtest');
SET @idem_product_id = LAST_INSERT_ID();
INSERT INTO `product_option` (`product_id`, `option_name`, `origin_price`, `sale_price`)
VALUES (@idem_product_id, 'idem-ok', 1000.00, 900.00);
SET @idem_option_ok_id = LAST_INSERT_ID();
INSERT INTO `inventory` (`product_option_id`, `quantity`) VALUES (@idem_option_ok_id, @big_stock);
INSERT INTO `product_option` (`product_id`, `option_name`, `origin_price`, `sale_price`)
VALUES (@idem_product_id, 'idem-empty', 1000.00, 900.00);
SET @idem_option_empty_id = LAST_INSERT_ID();
INSERT INTO `inventory` (`product_option_id`, `quantity`) VALUES (@idem_option_empty_id, 0);

-- ---------- 취소·확정용: 상품 1 + 옵션 1 (재고 충분) ----------
INSERT INTO `product` (`product_name`, `description`, `thumbnail`, `major_category`, `subcategory`, `catalog`)
VALUES (CONCAT('lt-', @run_id, '-race'), '부하 테스트 취소·확정용 상품', 'https://example.com/lt-race.jpg', 1, 101, 'loadtest');
SET @race_product_id = LAST_INSERT_ID();
INSERT INTO `product_option` (`product_id`, `option_name`, `origin_price`, `sale_price`)
VALUES (@race_product_id, 'race', 1000.00, 900.00);
SET @race_option_id = LAST_INSERT_ID();
INSERT INTO `inventory` (`product_option_id`, `quantity`) VALUES (@race_option_id, @big_stock);

-- ---------- 결과 ----------
SELECT 'USERS' AS name, CAST(@user_count AS CHAR) AS value
UNION ALL SELECT 'FLOW_PRODUCT_ID', CAST(@flow_product_id AS CHAR)
UNION ALL SELECT 'FLOW_OPTION_IDS', (SELECT GROUP_CONCAT(`id` ORDER BY `id`) FROM `product_option` WHERE `product_id` = @flow_product_id)
UNION ALL SELECT 'CONTENTION_OPTION_ID', CAST(@contention_option_id AS CHAR)
UNION ALL SELECT 'CONTENTION_STOCK', CAST(@contention_stock AS CHAR)
UNION ALL SELECT 'IDEM_OPTION_OK_ID', CAST(@idem_option_ok_id AS CHAR)
UNION ALL SELECT 'IDEM_OPTION_EMPTY_ID', CAST(@idem_option_empty_id AS CHAR)
UNION ALL SELECT 'RACE_OPTION_ID', CAST(@race_option_id AS CHAR)
UNION ALL SELECT 'BIG_STOCK', CAST(@big_stock AS CHAR);
