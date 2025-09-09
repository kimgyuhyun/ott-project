"use client";
import { useState, useRef, useEffect } from "react";
import AnimeCard from "./AnimeCard";
import styles from "./WeeklySchedule.module.css";

type AnimeItem = {
  aniId: number;
  title: string;
  posterUrl: string;
  rating?: number | null;
  badge?: string;
  episode?: string;
};

/**
 * 요일별 애니메이션 스케줄 컴포넌트
 * 요일별 탭과 해당 요일의 애니메이션 목록 표시
 */
type WeeklyScheduleProps = {
  onAnimeClick?: (anime: AnimeItem) => void;
  animeData?: any[] | Record<string, any[]>; // DB에서 가져온 애니메이션 데이터 (배열 또는 요일별 객체)
};

export default function WeeklySchedule({ onAnimeClick, animeData = [] }: WeeklyScheduleProps) {
  // 현재 요일 기반으로 기본 탭 설정
  const getCurrentDayIndex = () => {
    const today = new Date();
    const dayOfWeek = today.getDay(); // 0=일요일, 1=월요일, ..., 6=토요일
    // 한국 요일 순서로 변환: 월(0), 화(1), 수(2), 목(3), 금(4), 토(5), 일(6)
    return dayOfWeek === 0 ? 6 : dayOfWeek - 1;
  };
  
  const [selectedDay, setSelectedDay] = useState(getCurrentDayIndex());
  const [isScrollable, setIsScrollable] = useState(false);
  const carouselRef = useRef<HTMLDivElement | null>(null);
  
  const days = ["월", "화", "수", "목", "금", "토", "일"];
  
  // DB 데이터를 요일별로 그룹화
  const scheduleData: Record<number, AnimeItem[]> = {
    0: [], 1: [], 2: [], 3: [], 4: [], 5: [], 6: []
  };
  
  // animeData가 객체인 경우 (요일별로 이미 분류된 데이터)
  if (animeData && typeof animeData === 'object' && !Array.isArray(animeData)) {
    Object.keys(animeData).forEach(day => {
      const dayIndex = days.indexOf(day);
      if (dayIndex !== -1 && Array.isArray(animeData[day])) {
        animeData[day].forEach((anime: any) => {
          if (anime.title || anime.titleEn || anime.titleJp) {
            scheduleData[dayIndex].push({
              aniId: anime.aniId || anime.id,
              title: anime.title || anime.titleEn || anime.titleJp || '제목 없음',
              posterUrl: anime.posterUrl || '/placeholder-anime.jpg',
              rating: anime.rating,
              badge: anime.isNew ? 'NEW' : undefined
            });
          }
        });
      }
    });
  } else if (Array.isArray(animeData)) {
    // 기존 방식: 배열인 경우 broadcastDay 필드 기준으로 분류
    animeData.forEach((anime: any) => {
      const broadcastDay = anime.broadcastDay;
      if (broadcastDay && (anime.title || anime.titleEn || anime.titleJp)) {
        const dayIndex = days.indexOf(broadcastDay);
        if (dayIndex !== -1) {
          scheduleData[dayIndex].push({
            aniId: anime.aniId || anime.id,
            title: anime.title || anime.titleEn || anime.titleJp || '제목 없음',
            posterUrl: anime.posterUrl || '/placeholder-anime.jpg',
            rating: anime.rating,
            badge: anime.isNew ? 'NEW' : undefined
          });
        }
      }
    });
  }

  const currentDayAnimes = scheduleData[selectedDay] || [];

  // 캐러셀 스크롤 함수
  const scrollByCard = (direction: number) => {
    const container = carouselRef.current;
    if (!container) return;
    const firstItem = container.querySelector(`.${styles.carouselItem}`) as HTMLElement | null;
    const gapPx = 16; // CSS gap 1rem 가정
    const scrollAmount = firstItem ? (firstItem.getBoundingClientRect().width + gapPx) : Math.max(240, container.clientWidth * 0.8);
    container.scrollBy({ left: direction * scrollAmount, behavior: 'smooth' });
  };

  // 스크롤 가능 여부 계산
  useEffect(() => {
    const updateScrollability = () => {
      if (carouselRef.current) {
        setIsScrollable(carouselRef.current.scrollWidth > carouselRef.current.clientWidth + 4);
      }
    };

    updateScrollability();
    window.addEventListener('resize', updateScrollability);
    return () => window.removeEventListener('resize', updateScrollability);
  }, [currentDayAnimes]);

  return (
    <div className={styles.weeklyContainer}>
      <div className={styles.weeklyInnerContainer}>
        {/* 섹션 헤더 */}
        <div className={styles.weeklyHeader}>
          <div>
            <h2 className={styles.weeklyTitle}>요일별 신작</h2>
          </div>
          <button className={styles.weeklyNoticeButton}>
            업로드 공지
          </button>
        </div>

        {/* 요일 탭 */}
        <div className={styles.weeklyTabs}>
          {days.map((day, index) => (
            <button
              key={index}
              className={`${styles.weeklyTab} ${selectedDay === index ? styles.active : ''}`}
              onClick={() => setSelectedDay(index)}
            >
              {day}
            </button>
          ))}
        </div>

        {/* 애니메이션 캐러셀 */}
        {currentDayAnimes.length > 0 ? (
          <div className={styles.carouselWrapper}>
            {isScrollable && (
              <button
                className={`${styles.carouselButton} ${styles.carouselButtonLeft}`}
                aria-label="왼쪽으로"
                onClick={() => scrollByCard(-1)}
              >
                ‹
              </button>
            )}
            <div className={styles.carouselViewport}>
              <div className={styles.carouselTrack} ref={carouselRef}>
                {currentDayAnimes.map((anime) => (
                  <div
                    key={anime.aniId}
                    className={`${styles.animeGridItem} ${styles.carouselItem}`}
                    onClick={() => onAnimeClick?.(anime)}
                  >
                    <img
                      className={styles.animeGridPoster}
                      src={anime.posterUrl || '/placeholder-anime.jpg'}
                      alt={anime.title || '애니메이션 포스터'}
                    />
                    <div className={styles.animeGridTitle}>
                      {anime.title || '제목 없음'}
                    </div>
                    {anime.badge && (
                      <div className={styles.animeGridBadge}>
                        {anime.badge}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
            {isScrollable && (
              <button
                className={`${styles.carouselButton} ${styles.carouselButtonRight}`}
                aria-label="오른쪽으로"
                onClick={() => scrollByCard(1)}
              >
                ›
              </button>
            )}
          </div>
        ) : (
          <div className={styles.weeklyEmpty}>
            <p>해당 요일에 방영되는 애니메이션이 없습니다.</p>
          </div>
        )}
      </div>
    </div>
  );
}
