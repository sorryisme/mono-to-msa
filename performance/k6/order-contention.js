// 재고 경합: 같은 상품 옵션(한정 재고)에 여러 사용자가 동시에 주문한다.
// OrderService.createOrder 의 옵션 ID 정렬 + InventoryRepository.decreaseQuantity 의 조건부 UPDATE 가
// 실제 MySQL 동시 요청에서도 초과 판매를 막는지 결과 불변식으로 확인한다.
//
// 부하 모델: per-vu-iterations. VU = LT 사용자 수 이내(기본 100), VU 당 ITERATIONS 회(기본 1), 수량 1~3 무작위.
//   동시 발사 방식: setup() 에서 전원 로그인해 세션을 확보하고, 본 측정에서는 주문 요청만 보낸다.
//   시작 신호를 정렬하지는 않는다(k6 가 VU 를 순차 기동하므로 수십 ms 편차가 있다). 초기 재고(기본 50)보다
//   요청 수량 합이 훨씬 크므로 대부분의 요청이 재고 잔량을 두고 경쟁한다.
// 판정: 허용 거절은 409 OUT_OF_STOCK 뿐. 그 외 4xx, 5xx, 타임아웃은 0. 기능 check 100%.
// 사후 검증(verify-order-contention.sql):
//   성공 주문 수량 합 <= 초기 재고, 최종 재고 >= 0, 초기 - 최종 = 성공 수량 합,
//   DB 주문 수 = k6 의 201 수 (응답 유실 요청 수만큼 상한 여유), 롤백된 요청의 주문 없음.

import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { BASE_URL, intEnv, requireEnv, userLoginId, assertUsersAvailable } from './lib/config.js';
import { loginUsers, useSession } from './lib/auth.js';
import { params, checkMain, classify, expect2xxOr409, envelopeCode } from './lib/http.js';
import { baseThresholds, TREND_STATS } from './lib/thresholds.js';
import { handleSummary, uuidv4 } from './lib/summary.js';

export { handleSummary };

const VUS = intEnv('VUS', 100);
const ITERATIONS = intEnv('ITERATIONS', 1);
const MAX_QTY = intEnv('LT_MAX_QTY', 3);
const OPTION_ID = parseInt(requireEnv('LT_CONTENTION_OPTION_ID'), 10);

export const ordersCreated = new Counter('orders_created');
export const ordersCreatedQty = new Counter('orders_created_qty');
export const ordersOutOfStock = new Counter('orders_out_of_stock');

export const options = {
  scenarios: {
    contention: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '3m',
    },
  },
  thresholds: baseThresholds({
    'http_req_duration{phase:main}': ['p(95)<3000'],
    // 재고가 0 보다 크고 요청이 재고보다 많으므로 성공과 품절이 모두 나와야 정상적인 경합이다.
    orders_created: ['count>0'],
    orders_out_of_stock: ['count>0'],
  }),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  assertUsersAvailable(VUS);
  const loginIds = [];
  for (let i = 1; i <= VUS; i++) loginIds.push(userLoginId(i));
  return { sessions: loginUsers(loginIds) };
}

export default function (data) {
  const session = data.sessions[(exec.vu.idInTest - 1) % data.sessions.length];
  useSession(session.sid);

  const quantity = 1 + Math.floor(Math.random() * MAX_QTY);
  const res = http.post(
    `${BASE_URL}/api/v1/orders/create`,
    JSON.stringify({ orderItems: [{ productOptionId: OPTION_ID, quantity }] }),
    params('main', 'POST /api/v1/orders/create', {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
      responseCallback: expect2xxOr409,
    }),
  );

  const kind = classify(res, { OUT_OF_STOCK: 409 });
  if (kind === 'success') {
    ordersCreated.add(1);
    ordersCreatedQty.add(quantity);
  } else if (kind === 'expected_reject') {
    ordersOutOfStock.add(1);
  }

  checkMain(res, {
    'order 201 or 409 OUT_OF_STOCK': (r) =>
      (r.status === 201 && envelopeCode(r) === 'OK') || (r.status === 409 && envelopeCode(r) === 'OUT_OF_STOCK'),
  });
}
