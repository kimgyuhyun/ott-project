import HomeClient, { Anime } from "./HomeClient";

export const revalidate = 120;

type ListResponse = {
  items?: Anime[];
  content?: Anime[];
};

async function getBaseOrigin() {
  return process.env.BACKEND_ORIGIN || process.env.NEXT_PUBLIC_BACKEND_ORIGIN || "http://localhost:8090";
}

async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const base = await getBaseOrigin();
    const response = await fetch(`${base}${path}`, {
      method: "GET",
      next: { revalidate },
    });

    if (!response.ok) return null;
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) return null;
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

function normalizeAnimeList(input: unknown): Anime[] {
  const list = ((input as ListResponse)?.items || (input as ListResponse)?.content || []) as Anime[];
  if (!Array.isArray(list)) return [];
  return list.filter((anime) => (anime.title && anime.title.trim()) || (anime.titleEn && anime.titleEn.trim()) || (anime.titleJp && anime.titleJp.trim()));
}

export default async function Home() {
  const days = ["월", "화", "수", "목", "금", "토", "일"];

  const [animeListData, recommendedData, popularData, ...weeklyResponses] = await Promise.all([
    fetchJson<ListResponse>("/api/anime?status=ONGOING&size=50&sort=id"),
    fetchJson<Anime[]>("/api/anime/recommended?size=20"),
    fetchJson<ListResponse>("/api/anime?isPopular=true&size=20&sort=id"),
    ...days.map((day) => fetchJson<Anime[]>(`/api/anime/weekly/${encodeURIComponent(day)}?limit=20`)),
  ]);

  const initialWeeklyAnime: Record<string, Anime[]> = {};
  days.forEach((day, index) => {
    const dayList = weeklyResponses[index];
    initialWeeklyAnime[day] = Array.isArray(dayList) ? dayList.filter((anime) => anime?.isNew === true) : [];
  });

  const initialAnimeList = normalizeAnimeList(animeListData);
  const initialRecommendedAnime = Array.isArray(recommendedData)
    ? recommendedData.filter((anime) => (anime.title && anime.title.trim()) || (anime.titleEn && anime.titleEn.trim()) || (anime.titleJp && anime.titleJp.trim()))
    : [];
  const initialPopularAnime = normalizeAnimeList(popularData);

  return (
    <HomeClient
      initialAnimeList={initialAnimeList}
      initialRecommendedAnime={initialRecommendedAnime}
      initialPopularAnime={initialPopularAnime}
      initialWeeklyAnime={initialWeeklyAnime}
    />
  );
}
