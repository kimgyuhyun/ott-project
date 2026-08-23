import { expect, test } from "@playwright/test";
import { login } from "./support";

/**
 * 구독 결제 진입. 돈이 걸린 경로라 실패 비용이 가장 크다.
 *
 * 어디까지 보고 어디서 멈추는가
 * - 결제창(PortOne)을 여는 지점까지만 본다. 그 너머는 외부 도메인의 팝업이고, 카드 입력과
 *   본인 인증이 끼어 자동화하면 거의 확실히 불안정해진다. 깨지는 E2E 는 사람이 빨간불을
 *   무시하게 만들어서 없는 것보다 나쁘다.
 * - 결제 자체의 정확성(이중 지급, 멱등, 차액 정산)은 이미 Testcontainers 테스트가 실제 커밋과
 *   실제 스레드 2개로 검증한다: PaymentConfirmIdempotencyTest, RefundIdempotencyTest 등.
 *   여기서 그걸 다시 확인하는 것은 중복이고, E2E 로는 오히려 더 못 본다.
 * - 그래서 이 스펙이 답하는 질문은 하나다: "로그인한 사용자가 플랜을 보고 결제로 들어갈 수 있는가."
 *   플랜 가격이 화면에 뜬다는 것은 플랜 조회 API 와 렌더링이 살아있다는 뜻이고,
 *   CTA 가 로그인으로 튕기지 않는다는 것은 세션이 결제 경로까지 유지된다는 뜻이다.
 *
 * 2026-08-23 실측(비로그인): /membership 은 익명으로도 열리고 두 플랜이 렌더링되며
 * 버튼은 "멤버십 시작하기" 하나다. 로그인 이후 동작은 테스트 계정이 있는 스택에서 확인해야 한다.
 */
test.describe("구독 결제", () => {
  test("로그인한 사용자에게 플랜이 보이고 결제로 진입할 수 있다", async ({
    page,
  }) => {
    await login(page);

    await page.goto("/membership");

    // 플랜 조회가 실패하면 이름도 가격도 렌더링되지 않는다. 가격까지 보는 이유는
    // 껍데기만 그려지고 값이 비어 있는 경우를 통과시키지 않기 위해서다.
    await expect(page.getByText("베이직").first()).toBeVisible();
    await expect(page.getByText("프리미엄").first()).toBeVisible();
    await expect(page.getByText(/월\s*[\d,]+원/).first()).toBeVisible();

    await page.getByRole("button", { name: /멤버십 시작하기/ }).click();

    // 결제 진입의 최소 조건은 "로그인으로 돌려보내지지 않는 것"이다. 세션이 결제 경로에서
    // 끊기면 여기서 /login 으로 튄다 - 쿠키 속성이나 프록시 설정이 어긋났을 때 나오는 증상이다.
    await expect(page).not.toHaveURL(/\/login/);
  });
});
