// 세션 기반 로그인. 서버는 HttpSession(JSESSIONID 쿠키)으로 로그인 상태를 유지한다.
//
// k6 는 기본적으로 iteration 마다 VU 의 cookie jar 를 비운다. 최초 iteration 에서만 로그인하고 이후 요청을
// 반복하려면 시나리오 options 에 noCookiesReset: true 를 두거나(user-flow), setup() 에서 확보한 세션 ID 를
// 매 iteration 마다 jar 에 다시 넣어야 한다(경합 시나리오).

import http from 'k6/http';
import { BASE_URL, USER_PASSWORD } from './config.js';
import { params, isOk } from './http.js';

const SESSION_COOKIE = 'JSESSIONID';

/**
 * 로그인하고 세션 쿠키 값을 돌려준다. 실패하면 null. phase 태그로 본 측정에서 분리된다.
 *
 * freshJar=true 면 요청마다 빈 cookie jar 를 쓴다. 한 VU(예: setup)가 여러 계정을 잇달아 로그인할 때 jar 에 남은
 * 이전 JSESSIONID 가 실려 가면 서버가 기존 세션을 재사용해 Set-Cookie 를 내려주지 않기 때문이다.
 */
export function login(loginId, phase = 'login', freshJar = false) {
  const extra = freshJar ? { jar: new http.CookieJar() } : {};
  const res = http.post(
    `${BASE_URL}/api/v1/user/login`,
    JSON.stringify({ loginId, password: USER_PASSWORD }),
    params(phase, 'POST /api/v1/user/login', extra),
  );
  if (!isOk(res)) {
    console.error(`login failed: loginId=${loginId} status=${res.status} body=${res.body}`);
    return null;
  }
  const cookies = res.cookies[SESSION_COOKIE];
  if (!cookies || cookies.length === 0) {
    console.error(`login response has no ${SESSION_COOKIE}: loginId=${loginId}`);
    return null;
  }
  return cookies[0].value;
}

/** setup() 에서 여러 사용자를 순서대로 로그인해 세션 목록을 만든다. 하나라도 실패하면 테스트를 시작하지 않는다. */
export function loginUsers(loginIds) {
  const sessions = [];
  for (const loginId of loginIds) {
    const sid = login(loginId, 'setup', true);
    if (sid === null) throw new Error(`setup: ${loginId} 로그인 실패. fixture 와 replica 반영을 확인하세요.`);
    sessions.push({ loginId, sid });
  }
  return sessions;
}

/** setup() 이 넘긴 세션 ID 를 현재 VU 의 cookie jar 에 넣는다. cookie reset 이 켜져 있어도 iteration 마다 호출하면 된다. */
export function useSession(sid) {
  http.cookieJar().set(BASE_URL, SESSION_COOKIE, sid, { path: '/' });
}
