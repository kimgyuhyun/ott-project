// 결제창 성공 콜백 뒤 "결제가 실제로 확정됐는가"를 판정한다(#192).
//
// 왜 응답 코드로 나누지 않는가
// - 서버의 확정 API 는 PG 재검증이 통과하지 않으면 400 을 낸다. 그런데 그 400 에는 금액·주문번호 불일치처럼
//   다시는 확정되지 않을 건과, PG 조회 장애나 결제 직후라 아직 paid 가 아닌 것처럼 웹훅·대사(10분 주기)가
//   곧 확정할 건이 섞여 있다. 코드만 보고 실패로 보여주면 곧 확정될 결제를 실패로 안내하고,
//   예전처럼 전부 성공으로 보여주면 확정되지 않은 결제에 "결제 완료"를 안내한다.
// - 그래서 판단 기준을 서버의 결제 상태 하나로 둔다. 모바일 리다이렉트 경로(membership/success)도 같은 원칙이다.

export type ConfirmOutcome = "SUCCEEDED" | "FAILED" | "PENDING";

/**
 * @param confirm 확정 API 호출. 2xx 면 서버가 확정을 끝낸 것이다(이미 확정된 결제도 2xx).
 * @param readStatus 서버의 결제 상태 조회(PENDING/SUCCEEDED/FAILED/CANCELED)
 * @returns SUCCEEDED 일 때만 멤버십이 시작된 것이다. PENDING 은 아직 모른다는 뜻이지 실패가 아니다.
 */
export async function resolveConfirmOutcome(
  confirm: () => Promise<unknown>,
  readStatus: () => Promise<string>,
  { attempts = 5, intervalMs = 2000 } = {},
): Promise<ConfirmOutcome> {
  try {
    await confirm();
    return "SUCCEEDED";
  } catch {
    // 응답 코드로 판정하지 않는다(위 주석). 웹훅이 먼저 확정했을 수도 있으니 상태를 몇 번 확인한다.
  }
  for (let i = 0; i < attempts; i++) {
    try {
      const status = await readStatus();
      if (status === "SUCCEEDED") return "SUCCEEDED";
      if (status === "FAILED" || status === "CANCELED") return "FAILED";
    } catch {
      // 상태 조회 실패는 판정 근거가 아니다. 다음 시도로 넘어간다.
    }
    if (i < attempts - 1) {
      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }
  }
  return "PENDING";
}
