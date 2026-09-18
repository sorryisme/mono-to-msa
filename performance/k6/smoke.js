// Smoke: 기동 실패와 심각한 회귀를 잡는 짧은 읽기 부하. PR 마다 돈다.
//
// 부하 모델: 닫힌 모델(constant-vus). VU 5, 60초, iteration 당 요청 3개 + 0.5~1초 대기.
//   → iteration rate 와 RPS 는 다르다 (RPS ≈ iteration rate × 3).
// 판정: 기능 check 100%, http_req_failed < 1%, p95 < 1000ms.
//   이 값은 용량 목표가 아니라 임시 안전선이다. 기준선(같은 환경 3~5회)을 만든 뒤 조정한다.
// 데이터: data.sql 의 상품 1~3 (fixture 불필요). 로그인 없음.

import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, intEnv, strEnv, listEnv } from './lib/config.js';
import { params, checkMain, classify, envelopeCode } from './lib/http.js';
import { baseThresholds, TREND_STATS } from './lib/thresholds.js';
import { handleSummary } from './lib/summary.js';

export { handleSummary };

const PRODUCT_IDS = listEnv('LT_PRODUCT_IDS').length ? listEnv('LT_PRODUCT_IDS') : [1, 2, 3];

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: intEnv('VUS', 5),
      duration: strEnv('DURATION', '60s'),
    },
  },
  thresholds: baseThresholds({
    'http_req_duration{phase:main}': ['p(95)<1000'],
  }),
  summaryTrendStats: TREND_STATS,
};

export default function () {
  const categories = http.get(`${BASE_URL}/api/v1/products/category`, params('main', 'GET /api/v1/products/category'));
  classify(categories);
  checkMain(categories, {
    'category 200': (r) => r.status === 200,
    'category code OK': (r) => envelopeCode(r) === 'OK',
    'category has data': (r) => Array.isArray(r.json('data')) && r.json('data').length > 0,
  });

  const id = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const detail = http.get(`${BASE_URL}/api/v1/products/${id}`, params('main', 'GET /api/v1/products/{id}'));
  classify(detail);
  checkMain(detail, {
    'detail 200': (r) => r.status === 200,
    'detail code OK': (r) => envelopeCode(r) === 'OK',
    'detail id matches': (r) => r.json('data.id') === id,
  });

  const search = http.post(
    `${BASE_URL}/api/v1/products/search?page=0&size=10`,
    JSON.stringify({ query: '제품' }),
    params('main', 'POST /api/v1/products/search'),
  );
  classify(search);
  checkMain(search, {
    'search 200': (r) => r.status === 200,
    'search code OK': (r) => envelopeCode(r) === 'OK',
    'search has content': (r) => Array.isArray(r.json('data.content')),
  });

  sleep(0.5 + Math.random() * 0.5);
}
