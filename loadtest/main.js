import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Rate } from 'k6/metrics';

// ── 설정 ────────────────────────────────────────────────────────────────
// BASE:     부하를 걸 대상. 기본은 nginx 를 통한 실제 진입점.
// BACKENDS: 백엔드를 직접 때릴 때 쓴다(쉼표 구분). 지정하면 BASE 를 덮어쓰고
//           VU 를 인스턴스별로 나눠 로드밸런싱을 흉내낸다. nginx 의 limit_req 를
//           피해야 목표 부하가 만들어지기 때문이다(README 의 "레이트 리밋" 참고).
// TEST: smoke  = 스크립트가 도는지만 확인(부하 아님)
//       slo    = 목표치(동시 시청자 500명) 통과 여부 판정
//       knee   = 포화점 탐색(계단식 램프업)
//       stress = 한계 RPS 탐색(open-loop). knee 로는 한계를 못 재기 때문이다 — RESULTS.md 참고
const BACKENDS = (__ENV.BACKENDS || '').split(',').filter(Boolean);
const BASE = BACKENDS.length
  ? BACKENDS[(__VU - 1 + BACKENDS.length) % BACKENDS.length]
  : (__ENV.BASE || 'https://laputa.kozow.com');
// OriginValidationFilter 통과용. 미허용 오리진이면 쓰기 요청이 403.
// 백엔드 직결일 때도 공개 도메인을 그대로 보내야 한다(BASE 를 쓰면 127.0.0.1 이라 403).
const ORIGIN = __ENV.ORIGIN || (BACKENDS.length ? 'https://laputa.kozow.com' : BASE);
const MODE = __ENV.TEST || 'slo';
// 비밀번호는 소스에 두지 않는다. seed-users.sql 에 심은 값을 실행할 때 넘긴다.
const PASSWORD = __ENV.LT_PASSWORD;
if (!PASSWORD) {
  throw new Error('LT_PASSWORD 가 필요합니다. 예: k6 run -e LT_PASSWORD=... loadtest/main.js');
}
const ACCOUNTS = Number(__ENV.LT_ACCOUNTS || 500); // seed-users.sql 로 심은 계정 수

// 시청자 1명 = 5초에 1회 진행률 저장 (frontend/src/app/player/page.tsx:158-166)
// → VU 1명당 0.2 RPS. 500 VU = 진행률 저장 100 RPS = 동시 시청자 500명.
const TICK_SEC = 5;

const loginFailed = new Rate('login_failed');

// 스모크: 각 요청이 실제로 200 을 받는지만 확인한다. 임계값 판정 대상이 아니다.
const smokeStages = [{ duration: '20s', target: 2 }];

const sloStages = [
  { duration: '2m', target: 500 },  // 램프업
  { duration: '5m', target: 500 },  // 목표 부하 유지 ← 판정 구간
  { duration: '30s', target: 0 },
];

// 포화점 탐색: 처리량이 정체되고 p95 가 꺾이는 지점을 찾는다.
const kneeStages = [
  { duration: '1m', target: 100 },
  { duration: '2m', target: 100 },
  { duration: '1m', target: 250 },
  { duration: '2m', target: 250 },
  { duration: '1m', target: 500 },
  { duration: '2m', target: 500 },
  { duration: '1m', target: 1000 },
  { duration: '2m', target: 1000 },
  { duration: '1m', target: 1500 },
  { duration: '2m', target: 1500 },
  { duration: '30s', target: 0 },
];

// 한계 RPS 탐색: 사람 수가 아니라 초당 요청수를 직접 지정한다(open-loop).
// closed-loop(ramping-vus)은 서버가 느려지면 VU 가 응답을 기다리느라 부하도 같이 줄어들어
// 포화를 감춘다. 여기서는 서버가 느려져도 발생률이 유지되므로 큐가 쌓이는 게 그대로 보인다.
// rate 단위는 이터레이션/초. 이터레이션 1회가 평균 약 1.4 요청이라 http RPS 는 그 1.4배다.
const stressStages = [
  // 워밍업. 낮은 발생률로 시작해 JIT 와 커넥션 풀을 데운 뒤 계단을 올린다.
  { duration: '1m', target: 100 },
  { duration: '30s', target: 500 },
  { duration: '2m', target: 500 },
  { duration: '30s', target: 1000 },
  { duration: '2m', target: 1000 },
  { duration: '30s', target: 2000 },
  { duration: '2m', target: 2000 },
  { duration: '30s', target: 4000 },
  { duration: '2m', target: 4000 },
];

const viewerScenario = {
  executor: 'ramping-vus',
  startVUs: 0,
  stages: MODE === 'knee' ? kneeStages : MODE === 'smoke' ? smokeStages : sloStages,
  gracefulRampDown: '30s',
};

// preAllocatedVUs 는 시작 시점에 미리 만들어 두는 일꾼 수, maxVUs 는 서버가 느려졌을 때까지
// 감안한 상한이다. 응답 7ms 기준으로는 일꾼 하나가 초당 약 140 이터레이션을 내므로 4000/s 에
// 30명이면 되지만, 지연이 100ms 로 늘면 400명이 필요해진다.
// 상한에 닿아 dropped_iterations 가 찍히면 두 가지 중 하나다: 서버가 느려져 일꾼이 모자랐거나
// (= 포화 신호), 생성기가 한계였거나(= 측정 무효). 구분은 k6 CPU 로 한다.
const stressScenario = {
  executor: 'ramping-arrival-rate',
  startRate: 20,
  timeUnit: '1s',
  preAllocatedVUs: 50,
  maxVUs: 600,
  stages: stressStages,
};

// 기존 판정 임계값. slo/knee 결과와 비교 가능해야 하므로 건드리지 않는다.
const baseThresholds = {
  'login_failed': ['rate<0.01'],
  'http_req_failed': ['rate<0.001'],                       // 에러율 0.1% 미만
  'http_req_duration{name:progress_save}': ['p(95)<500'],  // 백그라운드 저장이라 관대
  'http_req_duration{name:anime_list}': ['p(95)<300'],     // 화면이 멈춰 보이는 구간
  'http_req_duration{name:anime_detail}': ['p(95)<300'],
  'http_req_duration{name:stream_url}': ['p(95)<500'],     // 재생 버튼 후 대기
  'http_req_duration{name:login}': ['p(95)<1000'],
};

// stress 는 깨질 때까지 미는 게 목적이라 임계값이 판정이 아니라 '중단 조건'이다.
// 서버가 무너진 뒤의 구간은 정보가 없고 운영 DB 에 부하만 주므로 즉시 멈춘다.
// delayAbortEval 은 램프 초반의 표본 부족으로 오탐 중단되는 것을 막는다.
// 중단 판정은 트래픽의 대부분이자 SLO 대상인 진행률 저장으로 본다. 전체 http_req_duration 으로
// 걸면 측정 대상이 아닌 요청까지 섞여 엉뚱하게 중단된다(로그인 때문에 실제로 겪었다).
const stressThresholds = {
  'http_req_failed': [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '1m' }],
  'http_req_duration{name:progress_save}': [
    { threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '1m' },
  ],
};

export const options = {
  scenarios: {
    viewers: MODE === 'stress' ? stressScenario : viewerScenario,
  },
  // 응답 본문을 버려서 부하 생성기 쪽 CPU/메모리 낭비를 줄인다(setup 은 개별로 예외 처리).
  discardResponseBodies: true,
  thresholds: MODE === 'stress' ? stressThresholds : baseThresholds,
};

// stress 는 실패가 나는 것이 정상이라 요청마다 로그를 찍으면 초당 수천 줄이 되어
// 생성기가 먼저 죽는다. 실패 건수는 http_req_failed 로 이미 집계되므로 로그는 끈다.
const LOG_ERRORS = MODE !== 'stress';

const jsonHeaders = { 'Content-Type': 'application/json', Origin: ORIGIN };
const getHeaders = { Origin: ORIGIN };

// ── setup: 실제 존재하는 애니/에피소드 ID 를 API 로 수집 ────────────────
// ID 를 하드코딩하면 DB 가 바뀔 때마다 스크립트가 죽는다. 매 실행마다 조회해서 쓴다.
export function setup() {
  const listRes = http.get(`${BASE}/api/anime?page=0&size=50`, {
    headers: getHeaders,
    responseType: 'text',
  });
  if (listRes.status !== 200) {
    fail(`애니 목록 조회 실패: status=${listRes.status}`);
  }
  const aniIds = listRes.json('items').map((a) => a.aniId);

  // 무료 회차(1~3화)만 모은다. 4화 이상은 멤버십이 없으면 stream-url 이 403 이라
  // 부하 테스트가 아니라 권한 거부 테스트가 되어버린다(PlaybackAuthService.canStream).
  const freeEpisodeIds = [];
  for (const aniId of aniIds.slice(0, 20)) {
    const detail = http.get(`${BASE}/api/anime/${aniId}`, {
      headers: getHeaders,
      responseType: 'text',
    });
    if (detail.status !== 200) {
      console.error(`setup 상세 조회 실패: aniId=${aniId} status=${detail.status}`);
      continue;
    }
    const episodes = detail.json('episodes') || [];
    for (const ep of episodes) {
      if (ep.episodeNumber <= 3 && ep.isActive && ep.isReleased) {
        freeEpisodeIds.push(ep.id);
      }
    }
  }

  if (freeEpisodeIds.length === 0) {
    fail('재생 가능한 무료 에피소드를 찾지 못했습니다.');
  }
  // stress 에서는 로그인을 부하 구간에서 완전히 빼고 여기서 미리 끝낸다.
  // 이유: open-loop 은 발생률을 맞추려고 VU 를 동적으로 늘리는데, VU 가 새로 생길 때마다
  // 로그인(BCrypt)을 하면 "느려짐 → VU 증설 → 로그인 폭주 → 더 느려짐" 되먹임이 생긴다.
  // 실측으로 이 되먹임이 두 번 측정을 날렸다(10초에 로그인 278건, VU 8 → 600). 인증은
  // 측정 대상이 아니라 준비물이므로 여기서 세션만 받아 두고 VU 는 그것을 빌려 쓴다.
  const sessions = [];
  if (MODE === 'stress') {
    const poolSize = Math.min(ACCOUNTS, Number(__ENV.LT_SESSIONS || 200));
    for (let i = 1; i <= poolSize; i++) {
      const email = `loadtest${String(i).padStart(4, '0')}@loadtest.local`;
      const res = http.post(
        `${BASE}/api/auth/login`,
        JSON.stringify({ email, password: PASSWORD }),
        { headers: jsonHeaders, responseType: 'text' }
      );
      if (res.status !== 200 || !res.cookies['JSESSIONID']) {
        fail(`setup 로그인 실패: ${email} status=${res.status} — seed-users.sql 을 실행했는지 확인`);
      }
      sessions.push(res.cookies['JSESSIONID'][0].value);
    }
    console.log(`setup 세션 ${sessions.length}개 확보(부하 구간에서는 로그인하지 않는다)`);
  }

  console.log(`setup 완료: 애니 ${aniIds.length}개, 무료 에피소드 ${freeEpisodeIds.length}개`);
  return { aniIds, freeEpisodeIds, sessions };
}

// ── VU 상태 ─────────────────────────────────────────────────────────────
// 세션 인증(Spring Session + Redis)이라 VU 당 1회만 로그인하고 세션을 재사용한다.
// 주의: k6 의 기본(VU) 쿠키 자는 매 이터레이션마다 초기화된다. 그래서 전용 CookieJar 를
// VU 당 1개 만들어 두고 모든 요청에 jar 로 넘긴다. 이 자는 이터레이션 경계를 넘어 유지되고,
// 서버가 세션 ID 를 회전시켜도 응답의 Set-Cookie 를 자동으로 따라간다.
let vuJar = null;
let myEpisodeId = null;
let positionSec = 0;

function login() {
  const idx = ((__VU - 1) % ACCOUNTS) + 1;
  const email = `loadtest${String(idx).padStart(4, '0')}@loadtest.local`;

  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: jsonHeaders, jar: vuJar, tags: { name: 'login' } }
  );

  // 401 이 나오면 계정이 없거나 비번이 틀린 것이고, 그대로 두면 실패가 누적되어
  // LoginAttemptService 가 계정을 잠근다(429) → 이후 측정이 통째로 오염된다.
  const ok = res.status === 200;
  loginFailed.add(!ok);
  if (!ok) {
    console.error(`로그인 실패: ${email} status=${res.status} — seed-users.sql 을 실행했는지 확인`);
    return false;
  }

  // 로그인 성공 시 세션 고정 방어로 세션 ID 가 회전한다(EmailAuthController 의 changeSessionId).
  // 응답의 Set-Cookie 는 vuJar 가 자동으로 흡수하므로 따로 꺼내 보관하지 않는다.
  if (!res.cookies['JSESSIONID']) {
    console.error(`로그인 응답에 JSESSIONID 가 없습니다: ${email}`);
    return false;
  }
  // 백엔드 직결(BACKENDS)일 때는 쿠키가 Domain=laputa.kozow.com; Secure 로 발급돼
  // 자가 도메인 불일치로 버린다 → 이후 요청이 전부 401. BASE 기준으로 직접 심어준다.
  if (BACKENDS.length) {
    vuJar.set(BASE, 'JSESSIONID', res.cookies['JSESSIONID'][0].value);
  }
  return true;
}

export default function (data) {
  if (vuJar === null) {
    vuJar = new http.CookieJar();
    if (MODE === 'stress') {
      // setup 에서 받아 둔 세션을 빌려 쓴다. 계정 수보다 VU 가 많으면 세션을 공유한다.
      vuJar.set(BASE, 'JSESSIONID', data.sessions[__VU % data.sessions.length]);
    } else if (!login()) {
      vuJar = null; // 다음 이터레이션에서 재시도
      sleep(TICK_SEC);
      return;
    }
    // VU 마다 다른 에피소드를 보게 해서 같은 row 에만 UPDATE 가 몰리는 것을 막는다.
    myEpisodeId = data.freeEpisodeIds[__VU % data.freeEpisodeIds.length];
    positionSec = 0;
  }


  // ── 시청 중 진행률 저장 (매 tick, 전체 요청의 약 72%) ──
  positionSec = (positionSec + TICK_SEC) % 1400;
  const progressRes = http.post(
    `${BASE}/api/episodes/${myEpisodeId}/progress`,
    JSON.stringify({ positionSec, durationSec: 1440 }),
    { headers: jsonHeaders, jar: vuJar, tags: { name: 'progress_save' } }
  );
  if (progressRes.status !== 200 && LOG_ERRORS) {
    console.error(`진행률 저장 실패: episodeId=${myEpisodeId} status=${progressRes.status}`);
  }

  // 탐색은 전체의 20%(목록 14% + 상세 14%가 아니라, 둘을 합쳐 28% 중 절반씩).
  // 스모크에서는 확률을 무시하고 모든 엔드포인트를 반드시 한 번씩 태운다.
  const roll = Math.random();
  const smoke = MODE === 'smoke';

  // ── 목록 조회 ──
  if (smoke || roll < 0.14) {
    const page = Math.floor(Math.random() * 5);
    const res = http.get(`${BASE}/api/anime?page=${page}&size=20`, {
      headers: getHeaders,
      jar: vuJar,
      tags: { name: 'anime_list' },
    });
    if (res.status !== 200 && LOG_ERRORS) console.error(`목록 조회 실패: status=${res.status}`);
  }

  // ── 상세 조회 ──
  if (smoke || (roll >= 0.14 && roll < 0.28)) {
    const aniId = data.aniIds[Math.floor(Math.random() * data.aniIds.length)];
    const res = http.get(`${BASE}/api/anime/${aniId}`, {
      headers: getHeaders,
      jar: vuJar,
      tags: { name: 'anime_detail' },
    });
    if (res.status !== 200 && LOG_ERRORS) console.error(`상세 조회 실패: aniId=${aniId} status=${res.status}`);
  }

  // ── 재생 시작 (약 8%) ──
  if (smoke || Math.random() < 0.11) {
    const res = http.get(`${BASE}/api/episodes/${myEpisodeId}/stream-url`, {
      headers: getHeaders,
      jar: vuJar,
      tags: { name: 'stream_url' },
    });
    if (!check(res, { 'stream-url 200': (r) => r.status === 200 }) && LOG_ERRORS) {
      console.error(`스트림 URL 실패: episodeId=${myEpisodeId} status=${res.status}`);
    }
  }

  // open-loop 에서는 발생률을 executor 가 통제하므로 이터레이션 안에서 자면 안 된다.
  // 5초를 자면 일꾼 하나가 초당 0.2 이터레이션밖에 못 내서 목표 RPS 를 만들 수 없다.
  if (MODE !== 'stress') {
    sleep(TICK_SEC);
  }
}
