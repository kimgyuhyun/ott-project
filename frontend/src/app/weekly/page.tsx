"use client";
import { useState } from "react";
import Header from "@/components/layout/Header";
import AnimeCard from "@/components/home/AnimeCard";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";

/**
 * 요일별 신작 페이지
 * 서비스 업데이트 안내, 요일별 탭, 애니 작품 그리드 포함
 */
export default function WeeklyPage() {
  const [activeDay, setActiveDay] = useState<'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'>('friday');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);

  const days = [
    { id: 'monday', label: '월요일' },
    { id: 'tuesday', label: '화요일' },
    { id: 'wednesday', label: '수요일' },
    { id: 'thursday', label: '목요일' },
    { id: 'friday', label: '금요일' },
    { id: 'saturday', label: '토요일' },
    { id: 'sunday', label: '일요일' }
  ];

  // 각 요일별 애니 작품 데이터 (이미지에 있는 것들)
  const weeklyAnimes = {
    monday: [
      { id: 1, title: "허니와 클로버", posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=허니와+클로버", badges: ["ONLY"], rating: 4.7 },
      { id: 2, title: "지박소년 하나코 군 2기 part 2", posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=지박소년+하나코+군+2기", badges: ["선독점"], rating: 4.8 },
      { id: 3, title: "이세계 묵시록 마이노그라~ 파멸의 문명에서 시작하는 ...", posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=이세계+묵시록", badges: [], rating: 4.6 }
    ],
    tuesday: [
      { id: 4, title: "서머 포켓츠", posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=서머+포켓츠", badges: ["선독점"], rating: 4.5 },
      { id: 5, title: "내가 연인이 될 수 있을 리 없잖아, 무리무리! (※무리가...", posterUrl: "https://via.placeholder.com/200x280/e53e3e/ffffff?text=내가+연인이+될+수+있을+리+없잖아", badges: [], rating: 4.4 },
      { id: 6, title: "루리의 보석", posterUrl: "https://via.placeholder.com/200x280/ed8936/ffffff?text=루리의+보석", badges: [], rating: 4.3 }
    ],
    wednesday: [
      { id: 7, title: "여친, 빌리겠습니다 4기", posterUrl: "https://via.placeholder.com/200x280/3182ce/ffffff?text=여친+빌리겠습니다+4기", badges: [], rating: 4.2 },
      { id: 8, title: "환생했는데 제7왕자라 내맘대로 마술을 연마합니다 2기", posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=환생했는데+제7왕자라", badges: [], rating: 4.1 },
      { id: 9, title: "배드 걸", posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=배드+걸", badges: [], rating: 4.0 }
    ],
    thursday: [
      { id: 10, title: "핑퐁 - 판권 부활", posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=핑퐁+판권+부활", badges: ["ONLY"], rating: 4.8 },
      { id: 11, title: "가치아쿠타", posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=가치아쿠타", badges: [], rating: 4.7 },
      { id: 12, title: "작안의 샤나 II", posterUrl: "https://via.placeholder.com/200x280/e53e3e/ffffff?text=작안의+샤나+II", badges: ["ONLY"], rating: 4.6 }
    ],
    friday: [
      { id: 13, title: "타코피의 원죄", posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=타코피의+원죄", badges: ["UP", "ONLY"], rating: 4.7 },
      { id: 14, title: "하나Doll", posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=하나Doll", badges: ["UP", "선독점"], rating: 4.6 },
      { id: 15, title: "단다단 2기", posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=단다단+2기", badges: ["UP"], rating: 4.5 }
    ],
    saturday: [
      { id: 16, title: "철야의 노래 시즌 2", posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=철야의+노래+시즌2", badges: ["ONLY"], rating: 4.8 },
      { id: 17, title: "사일런트 위치 침묵의 마녀의 비밀", posterUrl: "https://via.placeholder.com/200x280/e53e3e/ffffff?text=사일런트+위치", badges: ["선독점"], rating: 4.7 },
      { id: 18, title: "수속성의 마법사", posterUrl: "https://via.placeholder.com/200x280/ed8936/ffffff?text=수속성의+마법사", badges: ["선독점"], rating: 4.6 }
    ],
    sunday: [
      { id: 19, title: "괴수 8호 2기", posterUrl: "https://via.placeholder.com/200x280/3182ce/ffffff?text=괴수+8호+2기", badges: [], rating: 4.9 },
      { id: 20, title: "그 비스크 돌은 사랑을 한다 시즌 2", posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=그+비스크+돌은+사랑을+한다+시즌2", badges: [], rating: 4.8 },
      { id: 21, title: "위치 워치", posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=위치+워치", badges: [], rating: 4.7 }
    ]
  };

  // 현재 선택된 요일의 작품들
  const currentAnimes = weeklyAnimes[activeDay] || [];

  // 애니 카드 클릭 시 모달 열기
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  return (
    <div className="min-h-screen bg-white">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-7xl mx-auto px-6 py-8">
          {/* 페이지 제목 */}
          <h1 className="text-3xl font-bold text-gray-800 mb-6">요일별 신작</h1>

          {/* 서비스 업데이트 안내 박스 */}
          <div className="bg-gray-100 rounded-lg p-6 mb-8">
            <div className="flex items-start space-x-3">
              <div className="text-yellow-500 text-xl">🔔</div>
              <div className="flex-1">
                <div className="space-y-3 text-gray-700 text-sm">
                  <p>
                    8월 12일 서비스 예정이었던 <span className="font-medium">《가치아쿠타》 3화</span>는 
                    판권사 사정으로 인해 4화와 함께 <span className="font-medium text-yellow-600">8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    8월 21일 업데이트 예정이었던 <span className="font-medium">《앤 셜리 (Anne Shirley)》 19화</span>는 
                    현지 휴방으로 인해 <span className="font-medium text-yellow-600">8월 28일 업데이트 예정</span>입니다.
                  </p>
                  <p>
                    <span className="font-medium">《가라오케 가자!》 5화</span>는 
                    현지 휴방으로 인해 <span className="font-medium text-yellow-600">9월 중 서비스 예정</span>입니다.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* 요일별 탭 메뉴 */}
          <div className="border-b border-gray-300 mb-8">
            <div className="flex space-x-1">
              {days.map((day) => (
                <button
                  key={day.id}
                  onClick={() => setActiveDay(day.id)}
                  className={`px-6 py-3 rounded-t-lg transition-colors ${
                    activeDay === day.id
                      ? 'bg-purple-600 text-white'
                      : 'bg-gray-200 text-gray-600 hover:text-gray-800 hover:bg-gray-300'
                  }`}
                >
                  {day.label}
                </button>
              ))}
            </div>
          </div>

          {/* 애니 작품 그리드 */}
          <div className="grid grid-cols-5 gap-4">
            {currentAnimes.map((anime) => (
              <div 
                key={anime.id} 
                className="cursor-pointer transform hover:scale-105 transition-transform"
                onClick={() => handleAnimeClick(anime)}
              >
                <AnimeCard
                  aniId={anime.id}
                  title={anime.title}
                  posterUrl={anime.posterUrl}
                  rating={anime.rating}
                  badge={anime.badges[0]}
                />
              </div>
            ))}
          </div>

          {/* 빈 상태 (작품이 없는 경우) */}
          {currentAnimes.length === 0 && (
            <div className="text-center py-16">
              <div className="w-32 h-32 mx-auto mb-6 bg-gray-200 rounded-full flex items-center justify-center">
                <span className="text-gray-400 text-6xl">📅</span>
              </div>
              <p className="text-gray-500 text-lg">
                {days.find(d => d.id === activeDay)?.label} 신작이 아직 없어요.
              </p>
            </div>
          )}
        </div>
      </main>

      {/* 애니 상세 모달 */}
      {selectedAnime && (
        <AnimeDetailModal 
          isOpen={isModalOpen} 
          onClose={() => {
            setIsModalOpen(false);
            setSelectedAnime(null);
          }} 
        />
      )}
    </div>
  );
}
