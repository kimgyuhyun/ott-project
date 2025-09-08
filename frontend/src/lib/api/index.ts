import { backendOrigin } from "../config";

// 공통 API 유틸리티 함수
async function apiCall<T>(endpoint: string, init?: RequestInit): Promise<T> {
	try {
		// 항상 동일 오리진으로 프록시(nginx) 경유
		const url = endpoint.startsWith('http')
			? endpoint
			: (endpoint.startsWith('/api') ? endpoint : `/api${endpoint}`);
		
		const res = await fetch(url, {
			credentials: "include",
			headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
			...init,
		});
		
		if (!res.ok) {
			const text = await res.text().catch(() => "");
			const errorMessage = `${res.status} ${res.statusText} ${text}`;
			
			// 401 에러는 로그인하지 않은 상태이므로 정상적인 상황
			if (res.status === 401) {
				console.log('사용자가 로그인하지 않았습니다.');
			} else {
				console.log('API 호출 실패:', errorMessage);
			}
			
			const error = new Error(errorMessage) as any;
			error.response = { status: res.status, data: { message: text } };
			throw error;
		}
		
		const ct = res.headers.get("content-type") || "";
		return ct.includes("application/json") ? (res.json() as Promise<T>) : (null as T);
	} catch (error) {
		// 네트워크 에러나 기타 에러도 안전하게 처리
		console.log('API 호출 중 오류 발생:', error);
		throw error;
	}
}

// HTTP 메서드를 지원하는 API 객체
export const api = {
	get: <T>(endpoint: string) => apiCall<T>(endpoint, { method: 'GET' }),
	post: <T>(endpoint: string, data?: any) => apiCall<T>(endpoint, { 
		method: 'POST', 
		body: data ? JSON.stringify(data) : undefined 
	}),
	put: <T>(endpoint: string, data?: any) => apiCall<T>(endpoint, { 
		method: 'PUT', 
		body: data ? JSON.stringify(data) : undefined 
	}),
	patch: <T>(endpoint: string, data?: any) => apiCall<T>(endpoint, { 
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
