import { expect, test } from "@playwright/test";
import { login } from "./support";

/**
 * 로그인 → 목록 → 상세 → 재생. 서비스의 존재 이유에 해당하는 경로다.
 *
 * 왜 이 시나리오인가
 * - 이 한 줄에 프론트 라우팅, 세션 쿠키, 목록 조회, 상세 조회, 재생권한 판정, HLS 서명 URL 발급이
 *   전부 걸려 있다. 하나라도 끊기면 서비스가 죽은 것이므로 E2E 를 쓸 값이 나온다.
 * - 단위와 슬라이스는 이 경로를 조각으로만 본다. 조각이 전부 통과해도 조각 사이의 계약
 *   (프론트가 부르는 경로와 컨트롤러 매핑, 쿠키 속성, nginx 프록시)이 어긋나면 재생이 안 된다.
 *
 * 선택자에 대한 메모
 * - 포스터는 img[alt] 로 잡는다. CSS 모듈 클래스는 빌드마다 해시가 바뀌어
 *   (page_animeGridPoster__Cbwqp 같은 형태) 선택자로 쓸 수 없다. alt 는 애니 제목이라 안정적이다.
 * - 상세는 별도 라우트가 아니라 홈 위의 모달이다(HomeClient 의 AnimeDetailModal).
 *   role=dialog 가 아니어서 getByRole("dialog") 로는 못 잡는다 - 제목 노출로 판정한다.
 * - 재생 버튼 문구는 시청 이력에 따라 "1화 재생하기"/"N화 재생하기"로 달라져 정규식으로 받는다.
 *
 * 2026-08-23 실측(비로그인 구간): 위 세 선택자는 실제 화면에서 확인했다. 로그인 이후 구간
 * (video 엘리먼트 등장)은 테스트 계정이 있는 스택에서 처음 돌 때 확인해야 한다.
 */
test.describe("재생 경로", () => {
  test("로그인한 사용자가 목록에서 상세를 열고 재생을 시작한다", async ({
    page,
  }) => {
    await login(page);

    await page.goto("/");

    const poster = page.locator("img[alt]").first();
    await poster.waitFor({ state: "visible" });
    const title = await poster.getAttribute("alt");
    expect(
      title,
      "포스터에 alt 가 없으면 목록이 비어 있다는 뜻이다",
    ).toBeTruthy();

    await poster.click();

    // 모달이 열렸다는 것은 상세 조회가 성공했다는 뜻이다(getAnimeDetail 실패 시 열리지 않는다).
    await expect(
      page.getByText(title!, { exact: false }).first(),
    ).toBeVisible();

    await page
      .getByText(/재생하기/)
      .first()
      .click();

    // 재생 진입은 쿼리스트링으로 판정한다. 두 id 가 모두 채워졌다는 것은 상세 응답에서
    // 다음 화를 골라내는 로직까지 돌았다는 뜻이다.
    await expect(page).toHaveURL(/\/player\?episodeId=\d+&animeId=\d+/);

    // video 엘리먼트는 재생권한 판정과 HLS 서명 URL 발급이 끝나야 붙는다.
    // 비로그인으로 같은 경로를 타면 /player 까지는 가지만 여기서 멈춘다(실측: video 0개).
    // 그래서 이 단언이 "로그인 상태로 재생까지 갔다"를 실제로 구분한다.
    await expect(page.locator("video")).toBeAttached({ timeout: 20_000 });
  });
});
