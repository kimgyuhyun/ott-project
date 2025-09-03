"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import AnimeCard from "@/components/home/AnimeCard";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { getWeeklyAnime } from "@/lib/api/anime";

/**
 * 요일별 신작 페이지
 * 서비스 업데이트 안내, 요일별 탭, 애니 작품 그리드 포함
 */
export default function WeeklyPage() {
  const [activeDay, setActiveDay] = useState<'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'>('friday');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  const [weeklyAnimes, setWeeklyAnimes] = useState<Record<string, any[]>>({
    monday: [],
    tuesday: [],
    wednesday: [],
    thursday: [],
    friday: [],
    saturday: [],
    sunday: []
  });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const days = [
    { id: 'monday' as const, label: '월요일' },
    { id: 'tuesday' as const, label: '화요일' },
    { id: 'wednesday' as const, label: '수요일' },
    { id: 'thursday' as const, label: '목요일' },
    { id: 'friday' as const, label: '금요일' },
    { id: 'saturday' as const, label: '토요일' },
    { id: 'sunday' as const, label: '일요일' }
  ];

  // 요일별 애니메이션 데이터 로드
  useEffect(() => {
    const loadWeeklyAnime = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        // 모든 요일의 애니메이션 데이터를 병렬로 로드
        const dayPromises = days.map(async (day) => {
          try {
            const data = await getWeeklyAnime(day.id);
            return { day: day.id, data: (data as any) || [] };
          } catch (err) {
            console.error(`${day.label} 애니메이션 로드 실패:`, err);
            return { day: day.id, data: [] };
          }
        });
        
        const results = await Promise.all(dayPromises);
        const animeData: Record<string, any[]> = {
          monday: [],
          tuesday: [],
          wednesday: [],
          thursday: [],
          friday: [],
          saturday: [],
          sunday: []
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

  // 현재 선택된 요일의 작품들
  const currentAnimes = weeklyAnimes[activeDay] || [];

  // 애니 카드 클릭 시 모달 열기
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <div className="text-xl text-gray-300">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-900">
        <div className="text-xl text-red-400">{error}</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-7xl mx-auto px-6 py-8">
          {/* 페이지 제목 */}
          <h1 className="text-3xl font-bold text-white mb-6">요일별 신작</h1>

          {/* 서비스 업데이트 안내 박스 */}
          <div className="bg-gray-800 rounded-lg p-6 mb-8">
            <div className="flex items-start space-x-3">
              <div className="text-yellow-400 text-xl">🔔</div>
              <div className="flex-1">
                <div className="space-y-3 text-gray-300 text-sm">
                  <p>
                    8월 12일 서비스 예정이었던 <span className="font-medium">《가치아쿠타》 3화</span>는 
                    판권사 사정으로 인해 4화와 함께 <span className="font-medium text-yellow-400">8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    8월 21일 업데이트 예정이었던 <span className="font-medium">《앤 셜리 (Anne Shirley)》 19화</span>는 
                    현지 휴방으로 인해 <span className="font-medium text-yellow-400">8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    <span className="font-medium">《가라오케 가자!》 5화</span>는 
                    현지 휴방으로 인해 <span className="font-medium text-yellow-400">9월 중 서비스 예정</span>입니다.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* 요일별 탭 */}
          <div className="flex flex-wrap gap-2 mb-8">
            {days.map((day) => (
              <button
                key={day.id}
                onClick={() => setActiveDay(day.id)}
                className={`px-4 py-2 rounded-lg font-medium transition-colors ${
                  activeDay === day.id
                    ? 'bg-purple-600 text-white'
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
              >
                {day.label}
              </button>
            ))}
          </div>

          {/* 선택된 요일의 애니메이션 그리드 */}
          {currentAnimes.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
              {currentAnimes.map((anime: any, index: number) => {
                const itemId = anime.id ?? anime.aniId ?? index;
                const key = `${itemId}-${anime.title ?? 'item'}`;
                return (
                  <AnimeCard
                    key={key}
                    aniId={Number(itemId)}
                    title={anime.title}
                    posterUrl={anime.posterUrl || "https://placehold.co/200x280/4a5568/ffffff?text=No+Image"}
                    rating={anime.rating}
                    badge={anime.badges?.[0]}
                    episode={anime.episode}
                    onClick={() => handleAnimeClick(anime)}
                  />
                );
              })}
            </div>
          ) : (
            <div className="text-center py-12">
              <div className="text-gray-400 text-lg mb-2">
                {days.find(d => d.id === activeDay)?.label} 신작이 없습니다
              </div>
              <p className="text-gray-500">다른 요일을 선택해보세요</p>
            </div>
          )}
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
