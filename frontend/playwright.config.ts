import { defineConfig, devices } from "@playwright/test";

/**
 * E2E 설정
 *
 * 어디에 쏘는가
 * - E2E_BASE_URL 로 정한다. 기본값은 개발 스택의 nginx(http://localhost)다.
 * - 프로덕션에 쏘지 않는다. 로그인이 필요한 시나리오는 테스트 계정을 DB 에 심어야 하는데,
 *   그 계정은 실제로 로그인이 되므로 운영 DB 에 두면 그대로 공격 표면이 된다.
 *   같은 이유로 loadtest 도 CD 에서 빼 두었다(deploy-rolling.ps1 의 스모크 주석 참고).
 *   배포 후 프로덕션 확인은 배포 스크립트의 읽기 전용 스모크가 담당한다 - 역할이 다르다.
 *
 * 왜 개수가 적은가
 * - E2E 는 느리고 잘 깨진다. 수가 늘면 가짜 실패가 반복되고, 그러면 사람이 빨간불을
 *   무시하기 시작한다. 그 상태의 테스트는 없는 것보다 나쁘다. 그래서 "깨지면 서비스가
 *   죽은 것"인 경로만 남긴다. 나머지는 단위와 슬라이스가 이미 덮고 있다.
 *
 * 재시도
 * - CI 에서만 1회 재시도한다. 로컬에서 재시도를 켜면 진짜로 불안정한 테스트가 초록으로
 *   보여서 고칠 기회를 놓친다.
 */
export default defineConfig({
  testDir: "./e2e",
  // 로그인·재생처럼 단계가 많은 시나리오가 있어 기본 30초로는 부족하다.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // 같은 테스트 계정을 여러 스펙이 동시에 쓰면 세션이 서로를 밀어낸다(UserSessionRegistry).
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }]]
    : [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost",
    // 실패한 것만 남긴다. 전부 남기면 용량만 먹고 아무도 안 본다.
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
    // 개발 스택은 자체 서명 인증서일 수 있다.
    ignoreHTTPSErrors: true,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
