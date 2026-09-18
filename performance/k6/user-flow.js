// 일반 사용자 흐름: 로그인 → 상품 검색 → 상품 상세 → 장바구니 추가 → 주문 생성.
//
// 부하 모델: 닫힌 모델(constant-vus). VU 마다 전용 fixture 계정을 쓰고 최초 iteration 에서만 로그인한다
//   (noCookiesReset: true 로 세션 쿠키를 iteration 사이에 유지). iteration 당 본 측정 요청 4개 + 1초 대기.
//   로그인은 phase:login 태그라 본 측정 threshold 에 섞이지 않는다. 회원가입·BCrypt 비용은 fixture 단계에 있어 측정 밖이다.
// 데이터: 흐름용 옵션 20개(각 재고 1,000,000) 에 분산 주문 → 정상 처리량 측정이 품절 측정으로 바뀌지 않는다.
// 판정: 기능 check 100%, http_req_failed(main) < 1%, p95 < 1500ms, 주문이 1건 이상.
// 사후 검증(verify-user-flow.sql): 주문 수 = k6 가 받은 201 수(응답 유실분은 상한으로 허용), 재고 차감 합 = 주문 수량 합.

import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, intEnv, strEnv, listEnv, requireEnv, userLoginId, assertUsersAvailable } from './lib/config.js';
import { login } from './lib/auth.js';
import { params, checkMain, classify, envelopeCode } from './lib/http.js';
import { baseThresholds, TREND_STATS } from './lib/thresholds.js';
import { handleSummary, uuidv4 } from './lib/summary.js';

export { handleSummary };

const VUS = intEnv('VUS', 20);
const FLOW_PRODUCT_ID = parseInt(requireEnv('LT_FLOW_PRODUCT_ID'), 10);
const FLOW_OPTION_IDS = listEnv('LT_FLOW_OPTION_IDS');

export const ordersCreated = new Counter('orders_created');
export const ordersCreatedQty = new Counter('orders_created_qty');

export const options = {
  scenarios: {
    flow: {
      executor: 'constant-vus',
      vus: VUS,
      duration: strEnv('DURATION', '60s'),
    },
  },
  noCookiesReset: true,
  thresholds: baseThresholds({
    'http_req_duration{phase:main}': ['p(95)<1500'],
    orders_created: ['count>0'],
  }),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  assertUsersAvailable(VUS);
  if (FLOW_OPTION_IDS.length === 0) throw new Error('LT_FLOW_OPTION_IDS 가 비어 있습니다.');
}

// VU 마다 별도 JS 런타임이므로 모듈 변수는 VU 단위 상태다.
let loggedIn = false;

export default function () {
  if (!loggedIn) {
    const sid = login(userLoginId(__VU));
    if (sid === null) {
      // 예외로 iteration 만 끝내면 나머지 VU 가 threshold 를 다 통과해 실행이 "성공" 으로 끝날 수 있다.
      // 로그인 실패는 측정 조건 자체가 깨진 것이므로 실행을 중단한다 (login_failures threshold 와 이중 안전장치).
      exec.test.abort(`VU ${__VU} 로그인 실패 (${userLoginId(__VU)})`);
    }
    loggedIn = true;
  }

  const search = http.post(
    `${BASE_URL}/api/v1/products/search?page=0&size=10`,
    JSON.stringify({ query: 'lt-' }),
    params('main', 'POST /api/v1/products/search'),
  );
  classify(search);
  checkMain(search, {
    'search 200': (r) => r.status === 200,
    'search code OK': (r) => envelopeCode(r) === 'OK',
  });

  const detail = http.get(`${BASE_URL}/api/v1/products/${FLOW_PRODUCT_ID}`, params('main', 'GET /api/v1/products/{id}'));
  classify(detail);
  checkMain(detail, {
    'detail 200': (r) => r.status === 200,
    'detail has options': (r) => Array.isArray(r.json('data.options')) && r.json('data.options').length > 0,
  });

  const optionId = FLOW_OPTION_IDS[(__VU + __ITER) % FLOW_OPTION_IDS.length];
  const quantity = 1 + Math.floor(Math.random() * 3);

  const cart = http.post(
    `${BASE_URL}/api/v1/cart/add`,
    JSON.stringify({ productOptionId: optionId, quantity }),
    params('main', 'POST /api/v1/cart/add'),
  );
  classify(cart);
  checkMain(cart, {
    'cart 201': (r) => r.status === 201,
    'cart code OK': (r) => envelopeCode(r) === 'OK',
  });

  const order = http.post(
    `${BASE_URL}/api/v1/orders/create`,
    JSON.stringify({ orderItems: [{ productOptionId: optionId, quantity }] }),
    params('main', 'POST /api/v1/orders/create', { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() } }),
  );
  const kind = classify(order);
  if (kind === 'success' && order.status === 201) {
    ordersCreated.add(1);
    ordersCreatedQty.add(quantity);
  }
  checkMain(order, {
    'order 201': (r) => r.status === 201,
    'order code OK': (r) => envelopeCode(r) === 'OK',
    'order returns id': (r) => Number.isInteger(r.json('data')),
  });

  sleep(1);
}
