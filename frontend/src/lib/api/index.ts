import { backendOrigin } from "../config";

// 공통 API 유틸리티 함수
async function apiCall<T>(endpoint: string, init?: RequestInit): Promise<T> {
  // 항상 동일 오리진으로 프록시(nginx) 경유
  const url = endpoint.startsWith("http")
    ? endpoint
    : endpoint.startsWith("/api")
      ? endpoint
      : `/api${endpoint}`;

  const res = await fetch(url, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
    ...init,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    const errorMessage = `${res.status} ${res.statusText} ${text}`;

    // 로그인 상태로 보이던 사용자가 401 을 받으면(서버 세션 만료) 전역 이벤트로 알려
    // AuthContext 가 클라이언트 로그인 상태를 정리하도록 한다.
    // 비로그인 사용자의 정상적인 401(흔적 없음)은 무시한다.
    if (
      res.status === 401 &&
      typeof window !== "undefined" &&
      localStorage.getItem("user")
    ) {
      localStorage.removeItem("user");
      window.dispatchEvent(new CustomEvent("auth:unauthorized"));
    }

    // 에러 바디가 JSON 이면 파싱해서 싣는다. 문자열 그대로 두면 화면이 응답 전문을 그대로 보여주게 되고,
    // 에러 코드나 충돌 응답의 현재 값처럼 바디에 담아 보낸 값을 꺼내 쓸 수 없다.
    let data: { message?: string } & Record<string, unknown> = {
      message: text,
    };
    if ((res.headers.get("content-type") || "").includes("application/json")) {
      try {
        const parsed = JSON.parse(text);
        if (parsed && typeof parsed === "object") data = parsed;
      } catch {
        // 헤더는 JSON 인데 바디가 깨진 경우. 원문 문자열을 그대로 둔다.
      }
    }

    const error = new Error(errorMessage) as Error & {
      response: { status: number; data: { message?: string } };
    };
    error.response = { status: res.status, data };
    throw error;
  }

  const ct = res.headers.get("content-type") || "";
  return ct.includes("application/json")
    ? (res.json() as Promise<T>)
    : (null as T);
}

// HTTP 메서드를 지원하는 API 객체
export const api = {
  get: <T>(endpoint: string) => apiCall<T>(endpoint, { method: "GET" }),
  post: <T>(endpoint: string, data?: unknown) =>
    apiCall<T>(endpoint, {
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
    }),
  put: <T>(endpoint: string, data?: unknown) =>
    apiCall<T>(endpoint, {
      method: "PUT",
      body: data ? JSON.stringify(data) : undefined,
    }),
  patch: <T>(endpoint: string, data?: unknown) =>
    apiCall<T>(endpoint, {
      method: "PATCH",
      body: data ? JSON.stringify(data) : undefined,
    }),
  delete: <T>(endpoint: string) => apiCall<T>(endpoint, { method: "DELETE" }),
};

// 기존 호환성을 위한 함수 export (deprecated)
export async function apiFunction<T>(
  input: string,
  init?: RequestInit,
): Promise<T> {
  return apiCall<T>(input, init);
}

// API 모듈들 export
export * from "./auth";
export * from "./anime";
export * from "./membership";
export * from "./user";
export * from "./search";
export * from "./reviews";
export * from "./comments";
export * from "./episodeComments";
export * from "./player";
export * from "./skip";
export * from "./admin";
