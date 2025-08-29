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
};

export default function WeeklySchedule({ onAnimeClick }: WeeklyScheduleProps) {
  const [selectedDay, setSelectedDay] = useState(4); // 기본값: 금요일 (인덱스 4)
  
  const days = ["월", "화", "수", "목", "금", "토", "일"];
  
  // 임시 데이터 (실제로는 API에서 가져올 데이터)
  const scheduleData: Record<number, AnimeItem[]> = {
    0: [], // 월요일
    1: [], // 화요일  
    2: [], // 수요일
    3: [], // 목요일
    4: [ // 금요일 (4번 이미지에 있는 작품들)
      {
        aniId: 1,
        title: "타코피의 원죄",
        posterUrl: "https://placehold.co/200x280/ff69b4/ffffff?text=타코피의+원죄",
        rating: 4.7,
        badge: "UP"
      },
      {
        aniId: 2,
        title: "하나Doll",
        posterUrl: "https://placehold.co/200x280/4a5568/ffffff?text=하나Doll",
        rating: 4.6,
        badge: "UP"
      },
      {
        aniId: 3,
        title: "단다단 2기",
        posterUrl: "https://placehold.co/200x280/38a169/ffffff?text=단다단+2기",
        rating: 4.5,
        badge: "UP"
      },
      {
        aniId: 4,
        title: "철야의 노래 시즌 2",
        posterUrl: "https://placehold.co/200x280/805ad5/ffffff?text=철야의+노래+시즌2",
        rating: 4.8
      },
      {
        aniId: 5,
        title: "사일런트 위치 침묵의 마녀의 비밀",
        posterUrl: "https://placehold.co/200x280/e53e3e/ffffff?text=사일런트+위치",
        rating: 4.7
      },
      {
        aniId: 6,
        title: "수속성의 마법사",
        posterUrl: "https://placehold.co/200x280/ed8936/ffffff?text=수속성의+마법사",
        rating: 4.6
      }
    ],
    5: [], // 토요일
    6: []  // 일요일
  };

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
