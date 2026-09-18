// 종료 요약. 사람이 읽을 텍스트(stdout)와, 사후 검증 스크립트가 읽을 key=value 파일(/results/k6-facts.txt)을 만든다.
// jslib 같은 외부 모듈을 내려받지 않도록 직접 만든다 (CI 네트워크 의존을 줄인다).

import { randomBytes } from 'k6/crypto';

const RESULTS_DIR = __ENV.K6_RESULTS_DIR || '/results';

function fmt(n) {
  if (n === undefined || n === null) return '-';
  if (Number.isInteger(n)) return String(n);
  return n.toFixed(2);
}

function pad(s, width) {
  s = String(s);
  return s.length >= width ? s : s + ' '.repeat(width - s.length);
}

function thresholdLines(data) {
  const lines = [];
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (!metric.thresholds) continue;
    for (const [expr, result] of Object.entries(metric.thresholds)) {
      lines.push(`  ${result.ok ? 'PASS' : 'FAIL'}  ${name}: ${expr}`);
    }
  }
  return lines;
}

export function textSummary(data) {
  const out = [];
  out.push('');
  out.push('==== k6 summary ====');
  const m = data.metrics;
  const rows = [];
  for (const name of Object.keys(m).sort()) {
    const v = m[name].values;
    const type = m[name].type;
    if (type === 'counter') rows.push([name, `count=${fmt(v.count)} rate=${fmt(v.rate)}/s`]);
    else if (type === 'rate') rows.push([name, `rate=${fmt(v.rate * 100)}% (${fmt(v.passes)}/${fmt(v.passes + v.fails)})`]);
    else if (type === 'trend')
      rows.push([name, `avg=${fmt(v.avg)} med=${fmt(v.med)} p90=${fmt(v['p(90)'])} p95=${fmt(v['p(95)'])} p99=${fmt(v['p(99)'])} max=${fmt(v.max)}`]);
    else if (type === 'gauge') rows.push([name, `value=${fmt(v.value)} max=${fmt(v.max)}`]);
  }
  const width = Math.max(...rows.map((r) => r[0].length)) + 2;
  for (const [k, v] of rows) out.push(`  ${pad(k, width)}${v}`);
  out.push('');
  out.push('thresholds:');
  const tl = thresholdLines(data);
  out.push(...(tl.length ? tl : ['  (none)']));
  out.push('');
  return out.join('\n');
}

/** 카운터 전부와 threshold 실패 수를 key=value 로 남긴다. 사후 검증 SQL 이 k6 관측값과 DB 를 대조할 때 쓴다. */
export function facts(data) {
  const lines = [];
  let failed = 0;
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (metric.type === 'counter') lines.push(`k6_${name}=${metric.values.count}`);
    if (metric.thresholds) {
      for (const result of Object.values(metric.thresholds)) if (!result.ok) failed += 1;
    }
  }
  const reqs = data.metrics.http_reqs;
  if (reqs) lines.push(`k6_http_reqs_rate=${fmt(reqs.values.rate)}`);
  const dur = data.metrics['http_req_duration{phase:main}'] || data.metrics.http_req_duration;
  if (dur) lines.push(`k6_main_p95_ms=${fmt(dur.values['p(95)'])}`);
  lines.push(`k6_thresholds_failed=${failed}`);
  return lines.sort().join('\n') + '\n';
}

export function handleSummary(data) {
  const out = { stdout: textSummary(data) };
  out[`${RESULTS_DIR}/summary.json`] = JSON.stringify(data, null, 2);
  out[`${RESULTS_DIR}/k6-facts.txt`] = facts(data);
  return out;
}

/** RFC 4122 v4. jslib 없이 k6/crypto 로 만든다. */
export function uuidv4() {
  const b = new Uint8Array(randomBytes(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
