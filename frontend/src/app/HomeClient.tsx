"use client";

import { useState, useEffect, useRef } from "react";
import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import WeeklySchedule from "@/components/home/WeeklySchedule";
import { getAnimeDetail } from "@/lib/api/anime";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { useAuth } from "@/lib/AuthContext";
import Image from "next/image";
import { api } from "@/lib/api/index";
import styles from "./page.module.css";

type OAuthUserInfoResponse = {
  authenticated: boolean;
  username?: string;
  authorities?: string[];
  principal?: string;
  oauth2User?: boolean;
  provider?: string;
  attributes?: Record<string, unknown>;
};

export interface Anime {
  id: number;
  title: string;
  posterUrl: string;
  rating: number;
  status: string;
  type: string;
  year: number;
  genres: string[];
  studios: string[];
  tags: string[];
  synopsis: string;
  fullSynopsis: string;
  episodeCount: number;
  duration: number;
  ageRating: string;
  createdAt: string;
  updatedAt: string;
  aniId?: number;
  titleEn?: string;
  titleJp?: string;
  isNew?: boolean;
}

type HomeClientProps = {
  initialAnimeList: Anime[];
  initialRecommendedAnime: Anime[];
  initialPopularAnime: Anime[];
  initialWeeklyAnime: Record<string, Anime[]>;
};

export default function HomeClient({
  initialAnimeList,
  initialRecommendedAnime,
  initialPopularAnime,
  initialWeeklyAnime,
}: HomeClientProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<Anime | null>(null);
  const [animeList] = useState<Anime[]>(initialAnimeList);
  const [recommendedAnime] = useState<Anime[]>(initialRecommendedAnime);
  const [popularAnime] = useState<Anime[]>(initialPopularAnime);
  const [weeklyAnime] = useState<Record<string, Anime[]>>(initialWeeklyAnime);
  const [currentBannerIndex, setCurrentBannerIndex] = useState(0);

  const bannerData = [
    {
      image: "/banners/Mainbanner1.png",
      badge: "신작",
      title: "새로운 애니메이션",
      subtitle: "지금 만나보세요",
      description: "최신 인기 작품들을 확인하세요",
    },
    {
      image: "/banners/Mainbanner2.png",
      badge: "인기작",
      title: "추천 애니메이션",
      subtitle: "놓치지 마세요",
      description: "많은 사람들이 사랑하는 작품들",
    },
  ];

  const { user, isAuthenticated, login } = useAuth();
  const recommendedRef = useRef<HTMLDivElement | null>(null);
  const popularRef = useRef<HTMLDivElement | null>(null);
  const [recommendedScrollable, setRecommendedScrollable] = useState(false);
  const [popularScrollable, setPopularScrollable] = useState(false);

  const scrollByCard = (ref: React.RefObject<HTMLDivElement>, direction: number) => {
    const container = ref.current;
    if (!container) return;
    const firstItem = container.querySelector(`.${styles.carouselItem}`) as HTMLElement | null;
    const gapPx = 16;
    const scrollAmount = firstItem ? (firstItem.getBoundingClientRect().width + gapPx) : Math.max(240, container.clientWidth * 0.8);
    container.scrollBy({ left: direction * scrollAmount, behavior: "smooth" });
  };

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentBannerIndex((prev) => (prev + 1) % bannerData.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [bannerData.length]);

  useEffect(() => {
    const updateScrollability = () => {
      if (recommendedRef.current) {
        setRecommendedScrollable(recommendedRef.current.scrollWidth > recommendedRef.current.clientWidth + 4);
      }
      if (popularRef.current) {
        setPopularScrollable(popularRef.current.scrollWidth > popularRef.current.clientWidth + 4);
      }
    };

    updateScrollability();
    window.addEventListener("resize", updateScrollability);
    return () => window.removeEventListener("resize", updateScrollability);
  }, [recommendedAnime, popularAnime]);

  useEffect(() => {
    const setTheme = async () => {
      if (isAuthenticated && user) {
        try {
          const response = await fetch("/api/users/me/settings", { credentials: "include" });
          if (response.ok) {
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
              const text = await response.text();
              if (text.trim()) {
                try {
                  const settings = JSON.parse(text);
                  if (settings.theme && (settings.theme === "light" || settings.theme === "dark")) {
                    document.documentElement.setAttribute("data-theme", settings.theme);
                  } else {
                    document.documentElement.setAttribute("data-theme", "light");
                  }
                } catch {
                  document.documentElement.setAttribute("data-theme", "light");
                }
              } else {
                document.documentElement.setAttribute("data-theme", "light");
              }
            } else {
              document.documentElement.setAttribute("data-theme", "light");
            }
          } else {
            document.documentElement.setAttribute("data-theme", "light");
          }
        } catch {
          document.documentElement.setAttribute("data-theme", "light");
        }
      } else {
        document.documentElement.setAttribute("data-theme", "light");
      }
    };

    setTheme();
  }, [isAuthenticated, user]);

  useEffect(() => {
    const checkAuthStatus = async () => {
      if (isAuthenticated) return;

      try {
        try {
          const localResponse = await api.get("/api/users/me");
          if (localResponse) {
            const userData = {
              id: String((localResponse as { id?: string | number }).id || "unknown"),
              username: String((localResponse as { username?: string }).username || ""),
              email: String((localResponse as { email?: string }).email || ""),
              profileImage: typeof (localResponse as { profileImage?: string }).profileImage === "string" ? (localResponse as { profileImage?: string }).profileImage : undefined,
            };
            login(userData);
            return;
          }
        } catch {
          // fallback to oauth status
        }

        const oauthResponse = await api.get<OAuthUserInfoResponse>("/oauth2/user-info");
        if (oauthResponse.authenticated && (oauthResponse.attributes || oauthResponse.username)) {
          const userData = {
            id: String((oauthResponse as { id?: string }).id || oauthResponse.attributes?.userId || oauthResponse.attributes?.id || "unknown"),
            username: String(oauthResponse.username || oauthResponse.attributes?.userName || oauthResponse.attributes?.name || ""),
            email: String((oauthResponse as { email?: string }).email || oauthResponse.attributes?.userEmail || oauthResponse.attributes?.email || oauthResponse.username || ""),
            profileImage: typeof oauthResponse.attributes?.picture === "string" ? oauthResponse.attributes.picture : undefined,
          };
          login(userData);
        }
      } catch (error: unknown) {
        if ((error as Error & { response?: { status?: number } }).response?.status !== 401) {
          console.error("로그인 상태 확인 실패:", error);
        }
      }
    };

    checkAuthStatus();
  }, [isAuthenticated, login]);

  const recordUserActivity = async (animeId: number, activityType: string) => {
    try {
      const params = new URLSearchParams();
      params.append("animeId", animeId.toString());
      params.append("activityType", activityType);
      await api.post(`/api/anime/activity?${params.toString()}`);
    } catch {
      // no-op
    }
  };

  const handleAnimeClick = async (
    anime: Anime | { aniId?: number; id?: number; title?: string; titleEn?: string; titleJp?: string; posterUrl?: string; isNew?: boolean },
  ) => {
    const coerceToAnime = (src: any): Anime => {
      const id = Number(src?.aniId ?? src?.id ?? 0);
      return {
        id,
        title: String(src?.title ?? src?.titleEn ?? src?.titleJp ?? ""),
        posterUrl: src?.posterUrl ?? "/placeholder-anime.jpg",
        rating: typeof src?.rating === "number" ? src.rating : 0,
        status: String(src?.status ?? ""),
        type: String(src?.type ?? ""),
        year: Number(src?.year ?? new Date().getFullYear()),
        genres: Array.isArray(src?.genres) ? src.genres : [],
        studios: Array.isArray(src?.studios) ? src.studios : [],
        tags: Array.isArray(src?.tags) ? src.tags : [],
        synopsis: String(src?.synopsis ?? ""),
        fullSynopsis: String(src?.fullSynopsis ?? ""),
        episodeCount: Number(src?.episodeCount ?? 0),
        duration: Number(src?.duration ?? 0),
        ageRating: String(src?.ageRating ?? ""),
        createdAt: String(src?.createdAt ?? new Date().toISOString()),
        updatedAt: String(src?.updatedAt ?? new Date().toISOString()),
        aniId: id,
        titleEn: src?.titleEn,
        titleJp: src?.titleJp,
        isNew: src?.isNew === true,
      };
    };

    try {
      const id = anime?.aniId ?? anime?.id;
      if (id) {
        recordUserActivity(id, "view");
        const detail = await getAnimeDetail(id);
        setSelectedAnime(detail as Anime);
      } else {
        setSelectedAnime(coerceToAnime(anime));
      }
    } catch {
      setSelectedAnime(coerceToAnime(anime));
    } finally {
      setIsModalOpen(true);
    }
  };

  return (
    <div className={styles.homeContainer}>
      <Header />
      <main className={styles.homeMain}>
        <div className={styles.mainBanner}>
          <div
            className={styles.bannerBackground}
            style={{
              backgroundImage: `url('${bannerData[currentBannerIndex].image}')`,
            }}
          />
          <div className={styles.bannerDots}>
            {bannerData.map((_, index) => (
              <div
                key={index}
                className={`${styles.bannerDot} ${index === currentBannerIndex ? styles.active : ""}`}
                onClick={() => setCurrentBannerIndex(index)}
              />
            ))}
          </div>
        </div>

        <section className={styles.contentSection}>
          <div className={styles.contentContainer}>
            <WeeklySchedule onAnimeClick={(anime) => handleAnimeClick(anime)} animeData={weeklyAnime} />
          </div>

          {recommendedAnime.length > 0 && (
            <div className={styles.contentContainer}>
              <h2 className={styles.sectionTitle}>추천 애니메이션</h2>
              <div className={styles.carouselWrapper}>
                {recommendedScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonLeft}`}
                    aria-label="왼쪽으로"
                    onClick={() => scrollByCard(recommendedRef, -1)}
                  >
                    ‹
                  </button>
                )}
                <div className={styles.carouselViewport}>
                  <div className={styles.carouselTrack} ref={recommendedRef}>
                    {recommendedAnime.map((anime: Anime, idx: number) => (
                      <div
                        key={anime.aniId ?? anime.id ?? idx}
                        className={`${styles.animeGridItem} ${styles.carouselItem}`}
                        onClick={() => handleAnimeClick(anime)}
                      >
                        <Image
                          className={styles.animeGridPoster}
                          src={anime.posterUrl || "/placeholder-anime.jpg"}
                          alt={anime.title || anime.titleEn || anime.titleJp || "애니메이션 포스터"}
                          width={200}
                          height={280}
                        />
                        <div className={styles.animeGridTitle}>{anime.title || anime.titleEn || anime.titleJp || "제목 없음"}</div>
                      </div>
                    ))}
                  </div>
                </div>
                {recommendedScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonRight}`}
                    aria-label="오른쪽으로"
                    onClick={() => scrollByCard(recommendedRef, 1)}
                  >
                    ›
                  </button>
                )}
              </div>
            </div>
          )}

          {popularAnime.length > 0 && (
            <div className={styles.contentContainer}>
              <h2 className={styles.sectionTitle}>인기 애니메이션</h2>
              <div className={styles.carouselWrapper}>
                {popularScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonLeft}`}
                    aria-label="왼쪽으로"
                    onClick={() => scrollByCard(popularRef, -1)}
                  >
                    ‹
                  </button>
                )}
                <div className={styles.carouselViewport}>
                  <div className={styles.carouselTrack} ref={popularRef}>
                    {popularAnime.map((anime: Anime, idx: number) => (
                      <div
                        key={anime.aniId ?? anime.id ?? idx}
                        className={`${styles.animeGridItem} ${styles.carouselItem}`}
                        onClick={() => handleAnimeClick(anime)}
                      >
                        <Image
                          className={styles.animeGridPoster}
                          src={anime.posterUrl || "/placeholder-anime.jpg"}
                          alt={anime.title || anime.titleEn || anime.titleJp || "애니메이션 포스터"}
                          width={200}
                          height={280}
                        />
                        <div className={styles.animeGridTitle}>{anime.title || anime.titleEn || anime.titleJp || "제목 없음"}</div>
                      </div>
                    ))}
                  </div>
                </div>
                {popularScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonRight}`}
                    aria-label="오른쪽으로"
                    onClick={() => scrollByCard(popularRef, 1)}
                  >
                    ›
                  </button>
                )}
              </div>
            </div>
          )}
        </section>
      </main>

      {isModalOpen && selectedAnime && (
        <AnimeDetailModal anime={selectedAnime} isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />
      )}

      <Footer />
    </div>
  );
}
