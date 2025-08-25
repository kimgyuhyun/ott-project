// 이 파일은 더 이상 사용되지 않습니다. frontend/src/lib/api/index.ts를 사용하세요.
export async function api<T>(input: string, init?: RequestInit): Promise<T> {
	const res = await fetch(input, {
		credentials: "include",
		headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
		...init,
	});
	if (!res.ok) {
		const text = await res.text().catch(() => "");
		throw new Error(`${res.status} ${res.statusText} ${text}`);
	}
	const ct = res.headers.get("content-type") || "";
	return ct.includes("application/json") ? (res.json() as Promise<T>) : (null as T);
}
