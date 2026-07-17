import { backendOrigin } from "../config";

// 공통 API 유틸리티 함수
async function apiCall<T>(endpoint: string, init?: RequestInit): Promise<T> {
	// 항상 동일 오리진으로 프록시(nginx) 경유
	const url = endpoint.startsWith('http')
		? endpoint
		: (endpoint.startsWith('/api') ? endpoint : `/api${endpoint}`);

	// CSRF: 서버(Spring Security)가 CSRF 를 켜면 XSRF-TOKEN 쿠키를 내려준다(HttpOnly=false).
	// 더블 서브밋 패턴상 쓰기 요청은 그 값을 X-XSRF-TOKEN 헤더로 되돌려줘야 한다.
	// 서버 CSRF 가 꺼져 있으면 쿠키가 없어 헤더도 안 붙으므로 무해(no-op)하다.
	const method = (init?.method || "GET").toUpperCase();
	const csrfHeader: Record<string, string> = {};
	if (method !== "GET" && method !== "HEAD" && typeof document !== "undefined") {
		const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
		if (m) csrfHeader["X-XSRF-TOKEN"] = decodeURIComponent(m[1]);
	}

	const res = await fetch(url, {
		credentials: "include",
		...init,
		headers: { "Content-Type": "application/json", ...csrfHeader, ...(init?.headers || {}) },
	});

	if (!res.ok) {
		const text = await res.text().catch(() => "");
		const errorMessage = `${res.status} ${res.statusText} ${text}`;

		// 로그인 상태로 보이던 사용자가 401 을 받으면(서버 세션 만료) 전역 이벤트로 알려
		// AuthContext 가 클라이언트 로그인 상태를 정리하도록 한다.
		// 비로그인 사용자의 정상적인 401(흔적 없음)은 무시한다.
		if (res.status === 401 && typeof window !== 'undefined' && localStorage.getItem('user')) {
			localStorage.removeItem('user');
			window.dispatchEvent(new CustomEvent('auth:unauthorized'));
		}

		const error = new Error(errorMessage) as Error & { response: { status: number; data: { message: string } } };
		error.response = { status: res.status, data: { message: text } };
		throw error;
	}

	const ct = res.headers.get("content-type") || "";
	return ct.includes("application/json") ? (res.json() as Promise<T>) : (null as T);
}

// HTTP 메서드를 지원하는 API 객체
export const api = {
	get: <T>(endpoint: string) => apiCall<T>(endpoint, { method: 'GET' }),
	post: <T>(endpoint: string, data?: unknown) => apiCall<T>(endpoint, {
		method: 'POST',
		body: data ? JSON.stringify(data) : undefined
	}),
	put: <T>(endpoint: string, data?: unknown) => apiCall<T>(endpoint, {
		method: 'PUT',
		body: data ? JSON.stringify(data) : undefined
	}),
	patch: <T>(endpoint: string, data?: unknown) => apiCall<T>(endpoint, {
		method: 'PATCH',
		body: data ? JSON.stringify(data) : undefined
	}),
	delete: <T>(endpoint: string) => apiCall<T>(endpoint, { method: 'DELETE' })
};

// 기존 호환성을 위한 함수 export (deprecated)
export async function apiFunction<T>(input: string, init?: RequestInit): Promise<T> {
	return apiCall<T>(input, init);
}

// API 모듈들 export
export * from './auth';
export * from './anime';
export * from './membership';
export * from './user';
export * from './search';
export * from './reviews';
export * from './comments';
export * from './episodeComments';
export * from './player';
export * from './skip';
export * from './admin';
