// 공통 threshold. 합격/불합격은 반드시 k6 종료 코드로 전달돼야 하므로 check 결과도 threshold 로 묶는다.
//
// 적용 범위:
//   checks{phase:main}          본 측정 구간의 기능 check 만 (setup/login 태그는 제외)
//   http_req_failed{phase:main} 본 측정 구간의 HTTP 실패율. responseCallback 이 허용한 409 는 실패로 세지 않는다.
//   server_errors / transport_errors / unexpected_rejects  전 구간, 항상 0
//   login_failures              setup/login 단계의 로그인 실패. 일부 VU 만 실패해도 실행 전체를 실패로 만든다
//
// 시나리오별 지연·처리량 threshold 는 extra 로 덧붙인다.

export function baseThresholds(extra = {}) {
  return Object.assign(
    {
      'checks{phase:main}': ['rate==1'],
      'http_req_failed{phase:main}': ['rate<0.01'],
      server_errors: ['count==0'],
      transport_errors: ['count==0'],
      unexpected_rejects: ['count==0'],
      login_failures: ['count==0'],
    },
    extra,
  );
}

export const TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];
