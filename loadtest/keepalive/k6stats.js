// [측정 전용] k6 --summary-export 에서 측정 구간 지표만 뽑는다. 사용: node k6stats.js <k6-summary.json>...
const fs = require('fs');
for (const f of process.argv.slice(2)) {
  const m = JSON.parse(fs.readFileSync(f, 'utf8')).metrics;
  const d = m['http_req_duration{phase:measure}'], e = m['http_req_failed{phase:measure}'];
  const r = m['http_reqs'];
  console.log(`${f}: reqs=${r.count} med=${d.med.toFixed(2)} p95=${d['p(95)'].toFixed(2)} p99=${d['p(99)'].toFixed(2)} max=${d.max.toFixed(1)} ms  fail=${(e.value * 100).toFixed(3)}%`);
}
