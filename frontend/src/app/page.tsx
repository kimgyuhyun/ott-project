"use client";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

type AnimeListDto = { aniId: number; title: string; posterUrl: string; rating: number | null };
type Paged<T> = { items: T[]; total: number; page: number; size: number };

export default function Home() {
	const { data, isLoading, error } = useQuery({
		queryKey: ["search", { query: "", page: 0, size: 20 }],
		queryFn: () => api<Paged<AnimeListDto>>("/api/search?query=&page=0&size=20&sort=id"),
	});

	if (isLoading) return <p>로딩...</p>;
	if (error) {
		const msg = (error as Error).message || "오류";
		const is401 = msg.startsWith("401");
		return (
			<main style={{ padding: 16 }}>
				<h1>작품 목록</h1>
				<p>{is401 ? "로그인이 필요합니다." : "목록을 불러오는 중 오류가 발생했습니다."}</p>
				{is401 && (
					<a href="/login" style={{ color: "#06f" }}>로그인 페이지로 이동</a>
				)}
			</main>
		);
	}

	return (
		<main style={{ padding: 16 }}>
			<h1>작품 목록</h1>
			<ul style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, 160px)", gap: 12 }}>
				{data?.items.map((it) => (
					<li key={it.aniId} style={{ listStyle: "none" }}>
						<img src={it.posterUrl} alt={it.title} width={160} height={220} />
						<div style={{ marginTop: 6 }}>{it.title}</div>
						{it.rating != null && <div style={{ color: "#888" }}>{it.rating.toFixed(1)}</div>}
					</li>
				))}
			</ul>
		</main>
	);
}
