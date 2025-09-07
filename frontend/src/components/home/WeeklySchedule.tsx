"use client";
import { useState } from "react";
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
  animeData?: any[]; // DB에서 가져온 애니메이션 데이터
};

export default function WeeklySchedule({ onAnimeClick, animeData = [] }: WeeklyScheduleProps) {
  const [selectedDay, setSelectedDay] = useState(4); // 기본값: 금요일 (인덱스 4)
  
  const days = ["월", "화", "수", "목", "금", "토", "일"];
  
  // DB 데이터를 요일별로 그룹화
  const scheduleData: Record<number, AnimeItem[]> = {
    0: [], 1: [], 2: [], 3: [], 4: [], 5: [], 6: []
  };
  
  // animeData를 요일별로 분류 (broadcastDay 필드 기준)
  animeData.forEach((anime: any) => {
    const broadcastDay = anime.broadcastDay;
    if (broadcastDay && (anime.title || anime.titleEn || anime.titleJp)) { // 제목이 하나라도 있는 경우만 처리
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

  const currentDayAnimes = scheduleData[selectedDay] || [];

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

        {/* 애니메이션 그리드 */}
        {currentDayAnimes.length > 0 ? (
          <div className={styles.weeklyGrid}>
            {currentDayAnimes.map((anime) => (
              <AnimeCard
                key={anime.aniId}
                aniId={anime.aniId}
                title={anime.title}
                posterUrl={anime.posterUrl}
                rating={anime.rating}
                badge={anime.badge}
                episode={anime.episode}
                onClick={() => onAnimeClick?.(anime)}
              />
            ))}
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
