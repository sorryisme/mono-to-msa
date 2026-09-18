// 취소·확정 경합: 같은 PENDING 주문에 취소와 확정을 동시에 보낸다.
// OrderRepository.updateStatusIfCurrent 의 조건부 UPDATE 로 전이 성공이 하나뿐이고,
// 취소 성공 시에만 재고가 정확히 한 번 복구되는지 결과 불변식으로 확인한다.
//
// 데이터: setup() 에서 사용자 1 로 ROUNDS 개의 PENDING 주문(취소·확정용 옵션, 수량 1)을 만든다.
//   라운드마다 새 주문을 쓴다 — 같은 주문을 재사용하면 첫 전이 이후에는 상태 거절만 반복 측정된다.
// 부하 모델: cancel / confirm 두 scenario(shared-iterations, 각 VUS 개 VU, ROUNDS 회)가 같은 시각에 시작해
//   같은 iteration 번호의 주문을 때린다. iterationInTest 가 두 scenario 에서 각각 0..ROUNDS-1 로 나오므로
//   i 번째 취소와 i 번째 확정이 거의 동시에 같은 주문에 도달한다(엄밀한 동시 발사는 아니다).
// 판정: 허용 거절은 409 ORDER_STATUS_NOT_CHANGEABLE 뿐. 전이 성공(200) 합계 = ROUNDS, 거절 합계 = ROUNDS.
// 사후 검증(verify-order-race.sql): 주문 상태가 모두 CANCELLED 또는 COMPLETED(PENDING 없음),
//   최종 재고 = 초기 재고 - COMPLETED 주문 수 (취소분은 정확히 한 번 복구).

import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { BASE_URL, intEnv, requireEnv, userLoginId, assertUsersAvailable } from './lib/config.js';
import { login, useSession } from './lib/auth.js';
import { params, checkSetup, checkMain, classify, expect2xxOr409, envelopeCode, isOk } from './lib/http.js';
import { baseThresholds, TREND_STATS } from './lib/thresholds.js';
import { handleSummary, uuidv4 } from './lib/summary.js';

export { handleSummary };

const ROUNDS = intEnv('ROUNDS', 50);
const VUS = intEnv('VUS', 10);
const OPTION_ID = parseInt(requireEnv('LT_RACE_OPTION_ID'), 10);

export const transitions = new Counter('race_transitions');
export const rejected = new Counter('race_rejected');
export const cancelSuccess = new Counter('race_cancel_success');
export const confirmSuccess = new Counter('race_confirm_success');

export const options = {
  scenarios: {
    cancel: { executor: 'shared-iterations', vus: VUS, iterations: ROUNDS, maxDuration: '3m', exec: 'cancel' },
    confirm: { executor: 'shared-iterations', vus: VUS, iterations: ROUNDS, maxDuration: '3m', exec: 'confirm' },
  },
  thresholds: baseThresholds({
    race_transitions: [`count==${ROUNDS}`],
    race_rejected: [`count==${ROUNDS}`],
  }),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  assertUsersAvailable(1);
  const sid = login(userLoginId(1), 'setup');
  if (sid === null) throw new Error('setup: 사용자 1 로그인 실패');
  useSession(sid);

  const orderIds = [];
  for (let i = 0; i < ROUNDS; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/orders/create`,
      JSON.stringify({ orderItems: [{ productOptionId: OPTION_ID, quantity: 1 }] }),
      params('setup', 'POST /api/v1/orders/create', {
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
      }),
    );
    checkSetup(res, { 'setup: order 201': (r) => isOk(r, 201) });
    if (!isOk(res, 201)) throw new Error(`setup: ${i}번째 주문 생성 실패 status=${res.status} body=${res.body}`);
    orderIds.push(res.json('data'));
  }
  return { sid, orderIds };
}

function transition(data, action, successCounter) {
  useSession(data.sid);
  const orderId = data.orderIds[exec.scenario.iterationInTest];
  const res = http.put(
    `${BASE_URL}/api/v1/orders/${orderId}/${action}`,
    null,
    params('main', `PUT /api/v1/orders/{id}/${action}`, { responseCallback: expect2xxOr409 }),
  );
  const kind = classify(res, { ORDER_STATUS_NOT_CHANGEABLE: 409 });
  if (kind === 'success') {
    transitions.add(1);
    successCounter.add(1);
  } else if (kind === 'expected_reject') {
    rejected.add(1);
  }
  checkMain(res, {
    [`${action}: 200 OK or 409 ORDER_STATUS_NOT_CHANGEABLE`]: (r) =>
      (r.status === 200 && envelopeCode(r) === 'OK') ||
      (r.status === 409 && envelopeCode(r) === 'ORDER_STATUS_NOT_CHANGEABLE'),
  });
}

export function cancel(data) {
  transition(data, 'cancel', cancelSuccess);
}

export function confirm(data) {
  transition(data, 'confirm', confirmSuccess);
}
