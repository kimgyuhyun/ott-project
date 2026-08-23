import { expect, test } from "@playwright/test";

/**
 * 비로그인 접근이 실제로 막히는가.
 *
 * 왜 이 시나리오인가
 * - 인가가 뚫리면 유료 콘텐츠가 그대로 새고, 그건 매출로 직결된다.
 * - AdminAuthorizationTest 계열의 @WebMvcTest 가 인가 규칙 자체는 이미 검증한다. 여기서 다른 것은
 *   그 규칙이 nginx 와 프론트 라우팅을 지나서도 그대로 살아있는지다. 슬라이스는 필터체인만 보고
 *   프록시 설정이나 클라이언트 가드는 보지 않는다.
 *
 * 이 스펙만 계정이 필요 없다
 * - 로그인을 하지 않는 것이 검증 대상이라서다. 그래서 세 스펙 중 유일하게 어떤 스택에도
 *   안전하게 쏠 수 있다(읽기 전용, 계정 생성 없음).
 */
test.describe("비로그인 접근 차단", () => {
  test("보호된 페이지는 로그인으로 돌려보낸다", async ({ page }) => {
    await page.goto("/profiles");

    // 클라이언트 가드가 router.replace 로 돌려보낸다(app/profiles/page.tsx).
    // 서버 리다이렉트가 아니므로 URL 이 바뀔 때까지 기다려야 한다.
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });
  });

  test("인증이 필요한 API 는 익명 요청을 거부한다", async ({ request }) => {
    // 프론트를 거치지 않고 같은 진입점(nginx)으로 직접 친다. UI 가드가 없어도
    // 서버가 스스로 거부하는지를 보려는 것이다 - 가드는 우회될 수 있고 서버는 아니다.
    const response = await request.get("/api/users/me");

    expect(response.status()).toBe(401);
  });

  test("존재하지 않는 API 경로가 200 을 돌려주지 않는다", async ({
    request,
  }) => {
    // 라우팅이 잘못 열려 모든 경로가 프론트로 흘러가면(catch-all) 없는 API 가 200 을 준다.
    // 그 상태에서는 위 401 검사도 의미를 잃으므로 함께 본다.
    const response = await request.get("/api/this-endpoint-does-not-exist");

    expect(response.status()).not.toBe(200);
  });
});
