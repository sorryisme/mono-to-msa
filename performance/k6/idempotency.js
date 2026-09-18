// 멱등성 경합. 두 시나리오를 함께 돌린다.
//
// (a) duplicate — 동일 사용자·동일 본문·동일 키를 M 개 VU 가 동시에 보낸다(요청 재전송 모델). 그룹 K 개.
//     기대: 그룹당 201 정확히 1건, 나머지 409 DUPLICATE_REQUEST. 주문·재고 변경도 그룹당 한 번.
// (b) failurePath — 옵션 2개(선행 옵션 재고 충분, 후순위 옵션 재고 0)를 한 키로 주문 → 후순위에서 OUT_OF_STOCK.
//     이때 선행 옵션의 재고 차감·주문·멱등키가 함께 롤백되는지(원자성) DB 로 확인한다. 이어서 같은 키로 재시도해
//     "재시도 허용(OUT_OF_STOCK 재현)" 인지 "영구 거절(DUPLICATE_REQUEST)" 인지 관측한다.
//     IdempotencyAspect 는 saveAndFlush 후 proceed() 하고 트랜잭션 advice 와의 순서를 명시하지 않아, 계약은 정적으로
//     확정할 수 없다. LT_RETRY_CONTRACT (RETRY_ALLOWED | PERMANENT_REJECT) 가 주어지면 그 계약을 threshold 로 강제하고,
//     없으면 관측값만 요약에 남긴다. 첫 실측 후 performance/README.md 에 계약을 적고 스크립트 기본값으로 고정한다.
//
// 키 형식: <8hex>-0000-4000-8000-<1|2><11자리>. 8hex 는 실행마다 다르고(LT_KEY_PREFIX), 13번째 자리 1 은 duplicate,
//   2 는 failurePath. 사후 검증 SQL 이 prefix 로 이 실행의 키만 센다.
// 부하 모델: per-vu-iterations, VU 당 1회. 세션은 setup() 에서 확보한다. 사용자: duplicate 그룹 g → 사용자 g,
//   failurePath v → 사용자 K+v. 두 시나리오는 동시에 시작한다(사용자·키가 겹치지 않는다).
// 판정: 허용 거절은 시나리오별로 DUPLICATE_REQUEST / OUT_OF_STOCK(+재시도 시 DUPLICATE_REQUEST) 뿐.

import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { BASE_URL, intEnv, strEnv, requireEnv, userLoginId, assertUsersAvailable } from './lib/config.js';
import { loginUsers, useSession } from './lib/auth.js';
import { params, checkMain, classify, expect2xxOr409, envelopeCode } from './lib/http.js';
import { baseThresholds, TREND_STATS } from './lib/thresholds.js';
import { handleSummary } from './lib/summary.js';

export { handleSummary };

const GROUPS = intEnv('LT_DUP_GROUPS', 10);
const VUS_PER_GROUP = intEnv('LT_DUP_VUS_PER_GROUP', 5);
const FAIL_VUS = intEnv('LT_FAIL_VUS', 5);
const OK_OPTION_ID = parseInt(requireEnv('LT_IDEM_OPTION_OK_ID'), 10);
const EMPTY_OPTION_ID = parseInt(requireEnv('LT_IDEM_OPTION_EMPTY_ID'), 10);
const KEY_PREFIX = requireEnv('LT_KEY_PREFIX');
const RETRY_CONTRACT = strEnv('LT_RETRY_CONTRACT', '');

if (KEY_PREFIX.length !== 24) throw new Error(`LT_KEY_PREFIX 는 24자(8hex-0000-4000-8000-)여야 합니다: ${KEY_PREFIX}`);
if (!(OK_OPTION_ID < EMPTY_OPTION_ID)) {
  // createOrder 는 옵션 ID 오름차순으로 차감한다. 선행 옵션이 먼저 차감된 뒤 후순위에서 실패해야 롤백을 검증할 수 있다.
  throw new Error('LT_IDEM_OPTION_OK_ID 는 LT_IDEM_OPTION_EMPTY_ID 보다 작아야 합니다.');
}

export const dupSuccess = new Counter('idem_dup_success');
export const dupRejected = new Counter('idem_dup_rejected');
export const failFirstOutOfStock = new Counter('idem_fail_first_out_of_stock');
export const retryOutOfStock = new Counter('idem_retry_out_of_stock');
export const retryDuplicate = new Counter('idem_retry_duplicate');

function key(kind, n) {
  return `${KEY_PREFIX}${kind}${String(n).padStart(11, '0')}`;
}

const contractThresholds = {};
if (RETRY_CONTRACT === 'RETRY_ALLOWED') contractThresholds.idem_retry_out_of_stock = [`count==${FAIL_VUS}`];
else if (RETRY_CONTRACT === 'PERMANENT_REJECT') contractThresholds.idem_retry_duplicate = [`count==${FAIL_VUS}`];
else if (RETRY_CONTRACT !== '') throw new Error(`LT_RETRY_CONTRACT 값이 올바르지 않습니다: ${RETRY_CONTRACT}`);

export const options = {
  scenarios: {
    duplicate: {
      executor: 'per-vu-iterations',
      vus: GROUPS * VUS_PER_GROUP,
      iterations: 1,
      maxDuration: '2m',
      exec: 'duplicate',
    },
    failurePath: {
      executor: 'per-vu-iterations',
      vus: FAIL_VUS,
      iterations: 1,
      maxDuration: '2m',
      exec: 'failurePath',
    },
  },
  thresholds: baseThresholds(
    Object.assign(
      {
        idem_dup_success: [`count==${GROUPS}`],
        idem_dup_rejected: [`count==${GROUPS * (VUS_PER_GROUP - 1)}`],
        idem_fail_first_out_of_stock: [`count==${FAIL_VUS}`],
      },
      contractThresholds,
    ),
  ),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  assertUsersAvailable(GROUPS + FAIL_VUS);
  const loginIds = [];
  for (let i = 1; i <= GROUPS + FAIL_VUS; i++) loginIds.push(userLoginId(i));
  return { sessions: loginUsers(loginIds) };
}

function createOrder(items, idempotencyKey) {
  return http.post(
    `${BASE_URL}/api/v1/orders/create`,
    JSON.stringify({ orderItems: items }),
    params('main', 'POST /api/v1/orders/create', {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      responseCallback: expect2xxOr409,
    }),
  );
}

export function duplicate(data) {
  const group = Math.floor(exec.scenario.iterationInTest / VUS_PER_GROUP) + 1;
  useSession(data.sessions[group - 1].sid);

  const res = createOrder([{ productOptionId: OK_OPTION_ID, quantity: 1 }], key('1', group));
  const kind = classify(res, { DUPLICATE_REQUEST: 409 });
  if (kind === 'success' && res.status === 201) dupSuccess.add(1);
  else if (kind === 'expected_reject') dupRejected.add(1);

  checkMain(res, {
    'dup: 201 OK or 409 DUPLICATE_REQUEST': (r) =>
      (r.status === 201 && envelopeCode(r) === 'OK') || (r.status === 409 && envelopeCode(r) === 'DUPLICATE_REQUEST'),
  });
}

export function failurePath(data) {
  const n = exec.scenario.iterationInTest + 1;
  useSession(data.sessions[GROUPS + n - 1].sid);
  const idempotencyKey = key('2', n);
  const items = [
    { productOptionId: OK_OPTION_ID, quantity: 1 },
    { productOptionId: EMPTY_OPTION_ID, quantity: 1 },
  ];

  const first = createOrder(items, idempotencyKey);
  const firstKind = classify(first, { OUT_OF_STOCK: 409 });
  if (firstKind === 'expected_reject') failFirstOutOfStock.add(1);
  checkMain(first, {
    'fail: first request 409 OUT_OF_STOCK': (r) => r.status === 409 && envelopeCode(r) === 'OUT_OF_STOCK',
  });

  const retry = createOrder(items, idempotencyKey);
  const retryKind = classify(retry, { OUT_OF_STOCK: 409, DUPLICATE_REQUEST: 409 });
  const code = envelopeCode(retry);
  if (retryKind === 'expected_reject' && code === 'OUT_OF_STOCK') retryOutOfStock.add(1);
  else if (retryKind === 'expected_reject' && code === 'DUPLICATE_REQUEST') retryDuplicate.add(1);
  checkMain(retry, {
    // 성공(201)은 어느 계약에서도 허용되지 않는다 — 후순위 옵션 재고가 0 이므로 성공했다면 재고 검사가 뚫린 것이다.
    'fail: retry is 409 (OUT_OF_STOCK or DUPLICATE_REQUEST)': (r) =>
      r.status === 409 && (code === 'OUT_OF_STOCK' || code === 'DUPLICATE_REQUEST'),
  });
}
