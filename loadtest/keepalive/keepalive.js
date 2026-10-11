import http from 'k6/http';
import { check, fail } from 'k6';

// [측정 전용] nginx→앱 연결 재사용 전후 비교. README.md 참고.
// open-loop(constant-arrival-rate)로 RATE 이터레이션/초를 고정한다. 이터레이션 1회 = 요청 1건.
// 요청 비중은 main.js 의 실측 비중(진행률 저장 ~72%)을 따른다.
const BASE = __ENV.BASE || 'http://127.0.0.1:8080';
const RATE = Number(__ENV.RATE || 30);
const DURATION = __ENV.DURATION || '3m';
const PASSWORD = __ENV.LT_PASSWORD;
if (!PASSWORD) throw new Error('LT_PASSWORD 가 필요합니다');
const SESSIONS = Number(__ENV.LT_SESSIONS || 50);

const headers = { 'Content-Type': 'application/json', Origin: BASE };

export const options = {
  scenarios: {
    // 워밍업: JIT·DB 풀을 데운다. 집계에서 빼기 위해 phase 태그를 단다.
    warmup: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: '30s',
      preAllocatedVUs: 20, maxVUs: 200, tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      startTime: '30s', preAllocatedVUs: 20, maxVUs: 200, tags: { phase: 'measure' },
    },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // 합격 기준 C2 의 오류율. 지연 기준은 기준선 대비 비교라 여기 박지 않고 README 에 적었다.
  thresholds: {
    'http_req_failed{phase:measure}': ['rate<0.001'],
    'http_req_duration{phase:measure}': ['p(95)<1000'],
  },
};

export function setup() {
  const list = http.get(`${BASE}/api/anime?page=0&size=50`, { headers, responseType: 'text' });
  if (list.status !== 200) fail(`목록 조회 실패 ${list.status}`);
  const aniIds = list.json('items').map((a) => a.aniId);
  const episodeIds = [];
  for (const aniId of aniIds) {
    const d = http.get(`${BASE}/api/anime/${aniId}`, { headers, responseType: 'text' });
    for (const ep of d.json('episodes') || []) {
      if (ep.episodeNumber <= 3 && ep.isActive && ep.isReleased) episodeIds.push(ep.id);
    }
  }
  if (episodeIds.length === 0) fail('무료 에피소드 없음');
  const sessions = [];
  for (let i = 1; i <= SESSIONS; i++) {
    const email = `loadtest${String(i).padStart(4, '0')}@loadtest.local`;
    const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }),
      // 로그인마다 새 자를 쓴다. 자를 공유하면 앞 계정의 세션 쿠키가 실려 가서 서버가 그 세션 ID 를
      // 회전시키고(세션 고정 방어), 앞에서 받아 둔 세션이 무효가 된다.
      { headers, responseType: 'text', jar: new http.CookieJar() });
    if (r.status !== 200 || !r.cookies['JSESSIONID']) fail(`로그인 실패 ${email} ${r.status}`);
    sessions.push(r.cookies['JSESSIONID'][0].value);
  }
  return { aniIds, episodeIds, sessions };
}

export default function (data) {
  const jar = http.cookieJar();
  jar.set(BASE, 'JSESSIONID', data.sessions[(__VU + __ITER) % data.sessions.length]);
  const ep = data.episodeIds[__VU % data.episodeIds.length];
  const roll = Math.random();
  let res;
  if (roll < 0.72) {
    res = http.post(`${BASE}/api/episodes/${ep}/progress`,
      JSON.stringify({ positionSec: (__ITER * 5) % 1400, durationSec: 1440 }),
      { headers, tags: { name: 'progress_save' } });
  } else if (roll < 0.82) {
    res = http.get(`${BASE}/api/anime?page=0&size=20`, { headers, tags: { name: 'anime_list' } });
  } else if (roll < 0.92) {
    const aniId = data.aniIds[Math.floor(Math.random() * data.aniIds.length)];
    res = http.get(`${BASE}/api/anime/${aniId}`, { headers, tags: { name: 'anime_detail' } });
  } else {
    res = http.get(`${BASE}/api/episodes/${ep}/stream-url`, { headers, tags: { name: 'stream_url' } });
  }
  check(res, { 'status 200': (r) => r.status === 200 });
}
