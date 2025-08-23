"use client";
import { useState } from "react";
import AnimeCard from "./AnimeCard";

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
export default function WeeklySchedule() {
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
        posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=타코피의+원죄",
        rating: 4.7,
        badge: "UP"
      },
      {
        aniId: 2,
        title: "하나Doll",
        posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=하나Doll",
        rating: 4.6,
        badge: "UP"
      },
      {
        aniId: 3,
        title: "단다단 2기",
        posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=단다단+2기",
        rating: 4.5,
        badge: "UP"
      },
      {
        aniId: 4,
        title: "철야의 노래 시즌 2",
        posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=철야의+노래+시즌2",
        rating: 4.8
      },
      {
        aniId: 5,
        title: "사일런트 위치 침묵의 마녀의 비밀",
        posterUrl: "https://via.placeholder.com/200x280/e53e3e/ffffff?text=사일런트+위치",
        rating: 4.7
      },
      {
        aniId: 6,
        title: "수속성의 마법사",
        posterUrl: "https://via.placeholder.com/200x280/ed8936/ffffff?text=수속성의+마법사",
        rating: 4.6
      }
    ],
    5: [], // 토요일
    6: []  // 일요일
  };

  const currentDayAnimes = scheduleData[selectedDay] || [];

  return (
    <section className="py-12 bg-white">
      <div className="max-w-7xl mx-auto px-4">
        {/* 섹션 헤더 */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold text-gray-800 mb-2">요일별 신작</h2>
          </div>
          <button className="text-sm text-purple-600 hover:text-purple-700 transition-colors bg-purple-50 hover:bg-purple-100 px-4 py-2 rounded-lg">
            업로드 공지
          </button>
        </div>

        {/* 요일 탭 */}
        <div className="flex items-center space-x-4 mb-8">
          {days.map((day, index) => (
            <button
              key={index}
              onClick={() => setSelectedDay(index)}
              className={`w-12 h-12 rounded-full flex items-center justify-center text-sm font-medium transition-colors ${
                selectedDay === index
                  ? 'bg-purple-600 text-white'
                  : 'bg-gray-200 text-gray-600 hover:bg-gray-300'
              }`}
            >
              {day}
            </button>
          ))}
        </div>

        {/* 애니메이션 그리드 */}
        {currentDayAnimes.length > 0 ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {currentDayAnimes.map((anime) => (
              <AnimeCard
                key={anime.aniId}
                aniId={anime.aniId}
                title={anime.title}
                posterUrl={anime.posterUrl}
                rating={anime.rating}
                badge={anime.badge}
                episode={anime.episode}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500">해당 요일에 방영되는 애니메이션이 없습니다.</p>
          </div>
        )}
      </div>
    </section>
  );
}
