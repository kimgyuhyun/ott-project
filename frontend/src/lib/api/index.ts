export async function api<T>(input: string, init?: RequestInit): Promise<T> {
	try {
		const res = await fetch(input, {
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
			
			throw new Error(errorMessage);
		}
		
		const ct = res.headers.get("content-type") || "";
		return ct.includes("application/json") ? (res.json() as Promise<T>) : (null as T);
	} catch (error) {
		// 네트워크 에러나 기타 에러도 안전하게 처리
		console.log('API 호출 중 오류 발생:', error);
		throw error;
	}
}
