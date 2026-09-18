// 시드 사용자 1명을 회원가입 API 로 만든다. fixture SQL 이 이 계정의 BCrypt 해시·salt 를 복사해
// 부하용 사용자 N 명을 만들므로, BCrypt 비용은 여기서 한 번만 든다.

import http from 'k6/http';
import exec from 'k6/execution';
import { BASE_URL, RUN_ID, USER_PASSWORD, userLoginId } from './lib/config.js';
import { params, isOk } from './lib/http.js';

export const options = { vus: 1, iterations: 1 };

export default function () {
  const loginId = userLoginId('seed');
  const res = http.post(
    `${BASE_URL}/api/v1/user/signup`,
    JSON.stringify({
      loginId,
      password: USER_PASSWORD,
      name: loginId,
      email: `${loginId}@example.com`,
      phoneNumber: '01000000000',
    }),
    params('setup', 'POST /api/v1/user/signup'),
  );
  if (!isOk(res, 201)) {
    exec.test.abort(`seed signup failed: status=${res.status} body=${res.body} run=${RUN_ID}`);
  }
  console.log(`seed user created: ${loginId} id=${res.json('data.id')}`);
}

export function handleSummary() {
  return {};
}
