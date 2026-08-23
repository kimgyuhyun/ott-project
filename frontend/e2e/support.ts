import { expect, type Page } from "@playwright/test";

/**
 * 스펙 공용 도우미.
 *
 * 계정은 환경변수로 받는다. 소스에 두지 않는 이유는 loadtest/seed-users.sql 과 같다 -
 * 실제로 로그인이 되는 자격증명이라 커밋되면 그대로 유출이다.
 */
export const E2E_EMAIL = process.env.E2E_EMAIL ?? "e2e@e2e.local";
export const E2E_PASSWORD = process.env.E2E_PASSWORD;

export function requireCredentials(): string {
  if (!E2E_PASSWORD) {
    throw new Error(
      "E2E_PASSWORD 가 필요하다. e2e/seed-e2e-user.sql 로 계정을 심고 같은 값을 넘겨라.\n" +
        "  예: E2E_PASSWORD=... npm run e2e",
    );
  }
  return E2E_PASSWORD;
}

/**
 * 이메일/비밀번호로 로그인한다.
 *
 * 로그인 폼은 처음부터 보이지 않는다. 로그인 페이지는 소셜 버튼이 있는 모달을 먼저 띄우고,
 * "이메일로 시작"을 눌러야 EmailAuthForm 이 나타난다(app/login/page.tsx 의 showEmailForm).
 * 그래서 곧바로 #email 을 찾으면 실패한다.
 */
export async function login(page: Page): Promise<void> {
  const password = requireCredentials();

  await page.goto("/login");
  await page.getByText("이메일로 시작").click();

  await page.locator("#email").fill(E2E_EMAIL);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();

  // 로그인 성공은 "로그인 페이지를 벗어났다"로 판정한다. 착지 지점은 라우팅 정책에 따라
  // 바뀔 수 있지만 /login 에 머물지 않는다는 것은 어느 정책에서도 참이다.
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
}
