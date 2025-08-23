"use client";
import { useState } from "react";
import Header from "@/components/layout/Header";
import AnimeCard from "@/components/home/AnimeCard";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";

/**
 * 태그별 검색 페이지
 * 필터링 옵션과 애니 작품 그리드 레이아웃
 */
export default function TagsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  
  // 필터 상태
  const [filters, setFilters] = useState({
    watchable: true,
    membership: false
  });

  const [selectedGenres, setSelectedGenres] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  // 장르 목록
  const genres = [
    'BL', 'GL 백합', 'SF', '개그', '공포', '드라마', 
    '로맨스', '모험', '무협', '미스터리'
  ];

  // 태그 목록
  const tags = [
    '가족', '감동', '게임', '고등학생', '고딩', '고전', '공룡', '공주', '과거', '괴물',
    '교사', '구름', '군대', '귀여움', '기계', '기사', '길', '꿈', '나무', '남자아이'
  ];

  // 애니 작품 데이터
  const animes = [
    {
      id: 1,
      title: "꿈빛 파티시엘 리마스터",
      posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=꿈빛+파티시엘",
      badges: ["더빙", "ONLY"],
      rating: 4.8
    },
    {
      id: 2,
      title: "귀멸의 칼날 : 환락의 거리편",
      posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=귀멸의+칼날",
      badges: ["자막"],
      rating: 4.9
    },
    {
      id: 3,
      title: "하이큐!! 1기",
      posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=하이큐+1기",
      badges: [],
      rating: 4.7
    },
    {
      id: 4,
      title: "데스노트 리마스터",
      posterUrl: "https://via.placeholder.com/200x280/2d3748/ffffff?text=데스노트",
      badges: ["자막", "ONLY"],
      rating: 4.9
    },
    {
      id: 5,
      title: "하이큐!! 2기",
      posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=하이큐+2기",
      badges: ["자막"],
      rating: 4.8
    },
    {
      id: 6,
      title: "진격의 거인 1기",
      posterUrl: "https://via.placeholder.com/200x280/2d3748/ffffff?text=진격의+거인",
      badges: [],
      rating: 4.9
    },
    {
      id: 7,
      title: "장송의 프리렌",
      posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=장송의+프리렌",
      badges: ["자막"],
      rating: 4.6
    },
    {
      id: 8,
      title: "SPY×FAMILY part 1",
      posterUrl: "https://via.placeholder.com/200x280/e53e3e/ffffff?text=SPY+FAMILY",
      badges: ["자막"],
      rating: 4.8
    },
    {
      id: 9,
      title: "호리미야",
      posterUrl: "https://via.placeholder.com/200x280/ed8936/ffffff?text=호리미야",
      badges: [],
      rating: 4.7
    },
    {
      id: 10,
      title: "전생했더니 슬라임이었던 건에 대하여 2기 2부",
      posterUrl: "https://via.placeholder.com/200x280/3182ce/ffffff?text=슬라임+2기+2부",
      badges: [],
      rating: 4.5
    },
    {
      id: 11,
      title: "가정교사 히트맨 리본!",
      posterUrl: "https://via.placeholder.com/200x280/ff69b4/ffffff?text=가정교사",
      badges: ["더빙", "ONLY"],
      rating: 4.6
    },
    {
      id: 12,
      title: "전생했더니 슬라임이었던 건에 대하여 2기 1부",
      posterUrl: "https://via.placeholder.com/200x280/3182ce/ffffff?text=슬라임+2기+1부",
      badges: [],
      rating: 4.4
    },
    {
      id: 13,
      title: "귀멸의 칼날 : 도공 마을편",
      posterUrl: "https://via.placeholder.com/200x280/4a5568/ffffff?text=귀멸의+도공",
      badges: ["자막"],
      rating: 4.8
    },
    {
      id: 14,
      title: "진격의 거인 The FINAL part 1",
      posterUrl: "https://via.placeholder.com/200x280/2d3748/ffffff?text=진격의+FINAL",
      badges: [],
      rating: 4.9
    },
    {
      id: 15,
      title: "그 비스크 돌은 사랑을 한다",
      posterUrl: "https://via.placeholder.com/200x280/ed8936/ffffff?text=비스크+돌",
      badges: [],
      rating: 4.3
    },
    {
      id: 16,
      title: "나의 히어로 아카데미아 2기",
      posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=히어로+2기",
      badges: ["자막"],
      rating: 4.7
    },
    {
      id: 17,
      title: "주술회전 2기 : 시부야 사변",
      posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=주술회전+2기",
      badges: [],
      rating: 4.8
    },
    {
      id: 18,
      title: "하이큐!! TO THE TOP part 1",
      posterUrl: "https://via.placeholder.com/200x280/38a169/ffffff?text=하이큐+TOP",
      badges: [],
      rating: 4.6
    },
    {
      id: 19,
      title: "터무니없는 스킬로 이세계 방랑 밥",
      posterUrl: "https://via.placeholder.com/200x280/3182ce/ffffff?text=이세계+방랑",
      badges: [],
      rating: 4.4
    },
    {
      id: 20,
      title: "나의 히어로 아카데미아 5기",
      posterUrl: "https://via.placeholder.com/200x280/805ad5/ffffff?text=히어로+5기",
      badges: ["자막"],
      rating: 4.7
    }
  ];

  // 필터 초기화
  const resetFilters = () => {
    setFilters({ watchable: true, membership: false });
    setSelectedGenres([]);
    setSelectedTags([]);
  };

  // 애니 카드 클릭 시 모달 열기
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  return (
    <div className="min-h-screen bg-white">
      <Header />
      
      <main className="pt-16">
        {/* 페이지 제목 */}
        <div className="px-6 py-8">
          <h1 className="text-3xl font-bold text-gray-800">태그검색</h1>
        </div>

        <div className="flex gap-8 px-6 pb-8">
          {/* 왼쪽 사이드바 - 필터 */}
          <div className="w-80 flex-shrink-0">
            {/* 필터 섹션 */}
            <div className="bg-gray-100 rounded-lg p-6 mb-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-800">필터</h2>
                <button
                  onClick={resetFilters}
                  className="text-sm text-purple-600 hover:text-purple-700 transition-colors"
                >
                  전체 초기화
                </button>
              </div>
              
              <div className="space-y-3">
                <label className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={filters.watchable}
                    onChange={(e) => setFilters({...filters, watchable: e.target.checked})}
                    className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                  />
                  <span className="text-gray-700">감상 가능한 작품만 보기</span>
                </label>
                
                <label className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={filters.membership}
                    onChange={(e) => setFilters({...filters, membership: e.target.checked})}
                    className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                  />
                  <span className="text-gray-700">멤버십 포함된 작품만 보기</span>
                </label>
              </div>
            </div>

            {/* 장르 섹션 */}
            <div className="bg-gray-100 rounded-lg p-6 mb-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-800">장르</h2>
                <button className="text-sm text-purple-600 hover:text-purple-700 transition-colors">
                  더 보기 &gt;
                </button>
              </div>
              
              <div className="space-y-2">
                {genres.map((genre) => (
                  <label key={genre} className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedGenres.includes(genre)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedGenres([...selectedGenres, genre]);
                        } else {
                          setSelectedGenres(selectedGenres.filter(g => g !== genre));
                        }
                      }}
                      className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                    />
                    <span className="text-gray-700 text-sm">{genre}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* 태그 섹션 */}
            <div className="bg-gray-100 rounded-lg p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-800">태그</h2>
                <button className="text-sm text-purple-600 hover:text-purple-700 transition-colors">
                  더 보기 &gt;
                </button>
              </div>
              
              <div className="space-y-2">
                {tags.slice(0, 20).map((tag) => (
                  <label key={tag} className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedTags.includes(tag)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedTags([...selectedTags, tag]);
                        } else {
                          setSelectedTags(selectedTags.filter(t => t !== tag));
                        }
                      }}
                      className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                    />
                    <span className="text-gray-700 text-sm">{tag}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* 오른쪽 - 애니 작품 그리드 */}
          <div className="flex-1">
            <div className="grid grid-cols-5 gap-4">
              {animes.map((anime) => (
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
          </div>
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
