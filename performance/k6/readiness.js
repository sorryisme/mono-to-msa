// 애플리케이션 readiness 폴링. scripts/load-test.sh 가 fixture 를 넣기 전에 실행한다.
//
// /actuator/health/readiness 는 readinessState + DataSource(source/replica) health 를 묶어 본다.
// DB 한쪽이 죽어 있으면 Hikari connection-timeout 때문에 DOWN 판정까지 수십 초가 걸릴 수 있으므로
// 요청 timeout 과 전체 데드라인을 따로 둔다. 데드라인을 넘기면 abort 로 0 이 아닌 종료 코드를 만든다.

import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { MGMT_URL, intEnv } from './lib/config.js';

export const options = {
  vus: 1,
  iterations: 1,
  // 여기서는 기능 판정이 없다. 요약을 조용히 하려고 summary 모드를 줄인다.
  summaryTrendStats: ['avg', 'max'],
};

const anyStatus = http.expectedStatuses({ min: 100, max: 599 });

export default function () {
  const deadline = Date.now() + intEnv('READINESS_TIMEOUT_SEC', 180) * 1000;
  let last = '';
  while (Date.now() < deadline) {
    const res = http.get(`${MGMT_URL}/actuator/health/readiness`, {
      timeout: '10s',
      responseCallback: anyStatus,
      tags: { phase: 'setup', name: 'GET /actuator/health/readiness' },
    });
    if (res.status === 200) {
      let status = null;
      try {
        status = res.json('status');
      } catch (e) {
        status = null;
      }
      if (status === 'UP') {
        console.log(`readiness UP: ${res.body}`);
        return;
      }
    }
    last = `status=${res.status} body=${res.body}`;
    sleep(2);
  }
  exec.test.abort(`readiness timeout. last=${last}`);
}

export function handleSummary() {
  // readiness 는 측정이 아니므로 요약을 남기지 않는다.
  return {};
}
