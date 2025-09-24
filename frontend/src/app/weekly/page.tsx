"use client";
import { useState, useEffect, useRef, useCallback } from "react";
import Header from "@/components/layout/Header";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { api } from "@/lib/api/index";
import { Anime } from "@/types/common";
import Image from "next/image";
import styles from "./weekly.module.css";

/**
 * 요일별 신작 페이지
 * 7열 컬럼형 레이아웃으로 모든 요일을 동시에 표시
 */
export default function WeeklyPage() {
  type ExtendedAnime = Anime & { isNew?: boolean; aniId?: number | string; badges?: string[]; titleEn?: string; titleJp?: string };
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<Anime | null>(null);
  const [weeklyAnimes, setWeeklyAnimes] = useState<Record<string, Anime[]>>({
    '월': [],
    '화': [],
    '수': [],
    '목': [],
    '금': [],
    '토': [],
    '일': []
  });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeColumn, setActiveColumn] = useState<string>('');
  
  const columnRefs = useRef<Record<string, HTMLDivElement | null>>({});

  // 다크모드 적용
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'dark');
  }, []);

  const days = [
    { id: '월' as const, fullLabel: '월요일' },
    { id: '화' as const, fullLabel: '화요일' },
    { id: '수' as const, fullLabel: '수요일' },
    { id: '목' as const, fullLabel: '목요일' },
    { id: '금' as const, fullLabel: '금요일' },
    { id: '토' as const, fullLabel: '토요일' },
    { id: '일' as const, fullLabel: '일요일' }
  ];

  // 현재 요일 가져오기
  const getCurrentDay = () => {
    const today = new Date().getDay();
    const dayMap = ['일', '월', '화', '수', '목', '금', '토'];
    return dayMap[today];
  };

  const currentDay = getCurrentDay();

  // 요일별 애니메이션 데이터 로드
  useEffect(() => {
    const loadWeeklyAnime = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        // 모든 요일의 애니메이션 데이터를 병렬로 로드
        const dayPromises = days.map(async (day) => {
          try {
            const data = await api.get(`/api/anime/weekly/${day.id}?limit=20`);
            const allAnime = Array.isArray(data) ? (data as ExtendedAnime[]) : [];
            // 신작만 필터링
            const newAnime = allAnime.filter((anime: ExtendedAnime) => anime.isNew === true);
            console.log(`${day.fullLabel} 전체 애니메이션:`, allAnime.length, '개, 신작만:', newAnime.length, '개');
            return { day: day.id, data: newAnime };
          } catch (err) {
            console.error(`${day.fullLabel} 애니메이션 로드 실패:`, err);
            return { day: day.id, data: [] };
          }
        });
        
        const results = await Promise.all(dayPromises);
        const animeData: Record<string, ExtendedAnime[]> = {
          '월': [],
          '화': [],
          '수': [],
          '목': [],
          '금': [],
          '토': [],
          '일': []
        };
        
        results.forEach(({ day, data }) => {
          animeData[day] = data;
        });
        
        setWeeklyAnimes(animeData);
      } catch (err) {
        console.error('요일별 애니메이션 데이터 로드 실패:', err);
        setError('요일별 애니메이션 데이터를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    loadWeeklyAnime();
  }, []);

  // 현재 요일로 자동 스크롤
  useEffect(() => {
    if (!isLoading && Object.keys(weeklyAnimes).length > 0) {
      const currentDayColumn = columnRefs.current[currentDay];
      
      if (currentDayColumn) {
        setTimeout(() => {
          currentDayColumn.scrollIntoView({ 
            behavior: 'smooth', 
            block: 'start',
            inline: 'center'
          });
          setActiveColumn(currentDay);
        }, 100);
      }
    }
  }, [isLoading, weeklyAnimes, currentDay]);

  // 컬럼 가시성 감지 (IntersectionObserver)
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const dayId = entry.target.getAttribute('data-day');
            if (dayId) {
              setActiveColumn(dayId);
            }
          }
        });
      },
      {
        root: null,
        rootMargin: '-20% 0px -20% 0px',
        threshold: 0.5
      }
    );

    Object.values(columnRefs.current).forEach((ref) => {
      if (ref) {
        observer.observe(ref);
      }
    });

    return () => {
      observer.disconnect();
    };
  }, [weeklyAnimes]);


  // 애니 카드 클릭 시 모달 열기
  const handleAnimeClick = (anime: Anime) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  // 키보드 접근성
  const handleKeyDown = (event: React.KeyboardEvent, anime: Anime) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      handleAnimeClick(anime);
    }
  };

  // 배지 색상 매핑
  const getBadgeClass = (badge: string) => {
    switch (badge) {
      case 'UP':
        return styles.weeklyBadgeUp;
      case 'ONLY':
        return styles.weeklyBadgeOnly;
      case '선독점':
        return styles.weeklyBadgeExclusive;
      default:
        return styles.weeklyBadgeUp;
    }
  };

  if (isLoading) {
    return (
      <div className={styles.weeklyPageContainer}>
        <Header />
        <div className={styles.weeklyLoadingContainer}>
          <div className={styles.weeklyLoadingText}>로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.weeklyPageContainer}>
        <Header />
        <div className={styles.weeklyErrorContainer}>
          <div className={styles.weeklyErrorText}>{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.weeklyPageContainer}>
      <Header />
      
      <main className={styles.weeklyMain}>
        <div className={styles.weeklyContent}>
          {/* 페이지 제목 */}
          <h1 className={styles.weeklyPageTitle}>요일별 신작</h1>

          {/* 서비스 업데이트 안내 박스 */}
          {false && ( // 공지 필요 시 true로 바꿔서 노출
            <div className={styles.noticeBox}>
              <div className={styles.noticeContent}>
                <div className={styles.noticeIcon}>🔔</div>
                <div className={styles.noticeText}>
                  <p>
                    8월 12일 서비스 예정이었던 <span className={styles.noticeHighlight}>《가치아쿠타》 3화</span>는 
                    판권사 사정으로 인해 4화와 함께 <span className={styles.noticeHighlight}>8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    8월 21일 업데이트 예정이었던 <span className={styles.noticeHighlight}>《앤 셜리 (Anne Shirley)》 19화</span>는 
                    현지 휴방으로 인해 <span className={styles.noticeHighlight}>8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    <span className={styles.noticeHighlight}>《가라오케 가자!》 5화</span>는 
                    현지 휴방으로 인해 <span className={styles.noticeHighlight}>9월 중 서비스 예정</span>입니다.
                  </p>
                </div>
              </div>
            </div>
          )}


          {/* 7열 컬럼 컨테이너 */}
          <div className={styles.weeklyColumnsContainer}>
            {days.map((day) => {
              const dayAnimes = weeklyAnimes[day.id] || [];
              return (
                <div
                  key={day.id}
                  ref={(el) => { columnRefs.current[day.id] = el; }}
                  data-day={day.id}
                  className={`${styles.weeklyColumn} ${day.id === currentDay ? styles.weeklyColumnToday : ''}`}
                  aria-labelledby={`column-header-${day.id}`}
                >
                  {/* 컬럼 헤더 */}
                  <div className={`${styles.weeklyColumnHeader} ${day.id === currentDay ? styles.weeklyColumnHeaderToday : ''}`}>
                    <h2 
                      id={`column-header-${day.id}`}
                      className={`${styles.weeklyColumnTitle} ${day.id === currentDay ? styles.weeklyColumnTitleToday : ''}`}
                    >
                      {day.fullLabel}
                    </h2>
                  </div>

                  {/* 컬럼 콘텐츠 */}
                  <div className={styles.weeklyColumnContent}>
                    {dayAnimes.length > 0 ? (
                      <div className={styles.weeklyAnimeGrid}>
                        {dayAnimes.map((anime: ExtendedAnime, index: number) => {
                          const itemId = (anime as any).id ?? (anime as any).aniId ?? index;
                          const key = `${itemId}-${(anime as any).title ?? 'item'}`;
                          const badge = (anime as any).badges?.[0];
                          return (
                            <div
                              key={key}
                              className={styles.weeklyAnimeCard}
                              onClick={() => handleAnimeClick(anime)}
                              onKeyDown={(e) => handleKeyDown(e, anime)}
                              tabIndex={0}
                              role="button"
                              aria-label={`${anime.title || anime.titleEn || anime.titleJp || '애니메이션'} 상세보기`}
                            >
                              <Image
                                className={styles.weeklyAnimePoster}
                                src={anime.posterUrl || "https://placehold.co/200x280/4a5568/ffffff?text=No+Image"}
                                alt={anime.title || anime.titleEn || anime.titleJp || '애니메이션 포스터'}
                                width={200}
                                height={280}
                                loading="lazy"
                              />
                              <div className={styles.weeklyAnimeTitle}>
                                {anime.title || anime.titleEn || anime.titleJp || '제목 없음'}
                              </div>
                              {badge && (
                                <div className={`${styles.weeklyAnimeBadge} ${getBadgeClass(badge)}`}>
                                  {badge}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <div className={styles.weeklyEmptyState}>
                        <div className={styles.weeklyEmptyStateIcon}>📺</div>
                        <div className={styles.weeklyEmptyStateText}>
                          {day.fullLabel} 신작이 없습니다
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </main>

      {/* 애니메이션 상세 모달 */}
      {isModalOpen && selectedAnime && (
        <AnimeDetailModal
          anime={selectedAnime}
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </div>
  );
}
