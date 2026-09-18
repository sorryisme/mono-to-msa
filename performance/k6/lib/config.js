// 실행 환경 값. scripts/load-test.sh 가 compose 환경변수로 넣어 준다.
// 로컬에서 k6 를 직접 돌릴 때만 기본값이 쓰인다.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const MGMT_URL = __ENV.MGMT_URL || 'http://localhost:8081';
export const RUN_ID = __ENV.RUN_ID || 'local';

// fixture 사용자 전원이 같은 비밀번호를 쓴다 (BCrypt 해시를 시드 계정에서 복사하므로).
export const USER_PASSWORD = __ENV.LT_PASSWORD || 'loadtest';

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function intEnv(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const n = parseInt(raw, 10);
  if (Number.isNaN(n)) throw new Error(`${name} 은 정수여야 합니다: ${raw}`);
  return n;
}

export function strEnv(name, fallback) {
  const raw = __ENV[name];
  return raw === undefined || raw === '' ? fallback : raw;
}

export function listEnv(name) {
  const raw = strEnv(name, '');
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '')
    .map((s) => parseInt(s, 10));
}

export function requireEnv(name) {
  const raw = strEnv(name, '');
  if (raw === '') throw new Error(`${name} 이 비어 있습니다. scripts/load-test.sh 로 실행했는지 확인하세요.`);
  return raw;
}

/** fixture 가 만든 n 번째 사용자의 login_id (1부터). */
export function userLoginId(n) {
  return `lt-${RUN_ID}-${n}`;
}

/** fixture 사용자 수보다 큰 VU 를 요구하면 시작 전에 멈춘다. 세션 없는 VU 가 401 을 실패율에 섞지 않게. */
export function assertUsersAvailable(required) {
  const available = intEnv('LT_USERS', 0);
  if (required > available) {
    throw new Error(`fixture 사용자 ${available}명으로는 ${required}명이 필요한 시나리오를 돌릴 수 없습니다. --users 를 늘리세요.`);
  }
}
