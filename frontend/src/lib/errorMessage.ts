// 캐치된 unknown 에러에서 사용자에게 보여줄 메시지를 안전하게 추출한다.
// - axios 스타일(err.response.data.message)과 표준 Error(err.message)를 모두 처리
// - unknown 을 좁혀 읽으므로 `as any` 없이 타입 안전하다.
export function getErrorMessage(err: unknown): string | undefined {
  if (err && typeof err === "object") {
    const resp = (err as { response?: { data?: { message?: unknown } } }).response;
    if (resp?.data?.message != null) return String(resp.data.message);
    const message = (err as { message?: unknown }).message;
    if (message != null) return String(message);
  }
  return undefined;
}

// 캐치된 unknown 에러에서 HTTP 상태 코드를 안전하게 추출한다(예: 401 판별).
// - err.status(커스텀) 또는 err.response.status(axios 스타일) 모두 처리
export function getErrorStatus(err: unknown): number | undefined {
  if (err && typeof err === "object") {
    const direct = (err as { status?: unknown }).status;
    if (typeof direct === "number") return direct;
    const respStatus = (err as { response?: { status?: unknown } }).response?.status;
    if (typeof respStatus === "number") return respStatus;
  }
  return undefined;
}
