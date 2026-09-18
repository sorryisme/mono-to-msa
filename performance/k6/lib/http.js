// 응답 분류와 판정 규칙.
//
// 기본 http_req_failed 는 4xx/5xx 를 전부 실패로 센다. 시나리오가 의도한 업무 거절(409 OUT_OF_STOCK 등)은
// 실패가 아니므로, 요청마다 responseCallback 으로 "허용 상태"를 좁히고 본문 code 로 다시 한 번 분류한다.
// 허용 code 는 시나리오가 명시적으로 넘긴 것만 인정한다 — 전역으로 409 를 허용하면 잘못된 업무 오류까지 숨는다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { JSON_HEADERS } from './config.js';

// 5xx. 어떤 시나리오에서도 0 이어야 한다.
export const serverErrors = new Counter('server_errors');
// status 0: 타임아웃·연결 실패. 응답이 유실된 요청은 DB 에 커밋됐을 수도 있으므로 사후 검증에서 따로 대조한다.
export const transportErrors = new Counter('transport_errors');
// 허용 목록에 없는 4xx 또는 허용 상태인데 본문 code 가 다른 경우.
export const unexpectedRejects = new Counter('unexpected_rejects');

export const expect2xx = http.expectedStatuses({ min: 200, max: 299 });
export const expect2xxOr409 = http.expectedStatuses({ min: 200, max: 299 }, 409);

/** 공통 요청 파라미터. phase 태그로 setup/login 요청을 본 측정(main) threshold 에서 분리한다. */
export function params(phase, name, extra = {}) {
  return Object.assign(
    {
      headers: JSON_HEADERS,
      tags: { phase, name },
      responseCallback: expect2xx,
      timeout: '30s',
    },
    extra,
  );
}

export function envelope(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

export function envelopeCode(res) {
  const body = envelope(res);
  return body && typeof body.code === 'string' ? body.code : null;
}

/**
 * 응답을 success / expected_reject / unexpected 로 분류하고 카운터를 올린다.
 * allowedRejects: { OUT_OF_STOCK: 409 } 처럼 "code -> 기대 HTTP 상태" 로 넘긴다.
 */
export function classify(res, allowedRejects = {}) {
  if (res.status === 0) {
    transportErrors.add(1);
    return 'transport_error';
  }
  if (res.status >= 500) {
    serverErrors.add(1);
    return 'server_error';
  }
  const code = envelopeCode(res);
  if (res.status >= 200 && res.status < 300) {
    if (code === 'OK') return 'success';
    unexpectedRejects.add(1);
    return 'unexpected';
  }
  if (code && allowedRejects[code] === res.status) return 'expected_reject';
  unexpectedRejects.add(1);
  return 'unexpected';
}

/** 본 측정 구간의 기능 check. 태그를 고정해 checks{phase:main} threshold 가 이것만 보게 한다. */
export function checkMain(res, checks) {
  return check(res, checks, { phase: 'main' });
}

export function checkSetup(res, checks) {
  return check(res, checks, { phase: 'setup' });
}

export function isOk(res, status = 200) {
  return res.status === status && envelopeCode(res) === 'OK';
}
