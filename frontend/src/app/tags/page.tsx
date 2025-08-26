"use client";
import { useState, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import Header from "@/components/layout/Header";
import AnimeCard from "@/components/home/AnimeCard";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import SearchBar from "@/components/search/SearchBar";
import { searchContent } from "@/lib/api/search";
import { getAnimeByGenre, getAnimeByTag } from "@/lib/api/anime";

/**
 * 태그별 검색 페이지
 * 필터링 옵션과 애니 작품 그리드 레이아웃
 */
export default function TagsPage() {
  const searchParams = useSearchParams();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  const [animes, setAnimes] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 필터 상태
  const [filters, setFilters] = useState({
    watchable: true,
    membership: false
  });

  const [selectedGenres, setSelectedGenres] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');

  // URL 검색 파라미터 처리
  useEffect(() => {
    const urlSearch = searchParams.get('search');
    if (urlSearch) {
      setSearchQuery(urlSearch);
      // URL 검색어가 있으면 자동으로 검색 실행
      setTimeout(() => {
        handleSearch(urlSearch);
      }, 100);
    }
  }, [searchParams]);

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

  // 애니메이션 검색 실행
  const executeSearch = async () => {
    try {
      setIsLoading(true);
      setError(null);

      const normalizeToArray = (data: any): any[] => {
        const isArr = Array.isArray(data);
        const hasContent = data && Array.isArray((data as any).content);
        const hasItems = data && Array.isArray((data as any).items);
        console.log('[검색응답] typeof=', typeof data, 'isArray=', isArr, 'keys=', data ? Object.keys(data) : null);
        if (isArr) return data as any[];
        if (hasContent) return (data as any).content as any[];
        if (hasItems) return (data as any).items as any[];
        console.warn('[검색응답] 예상치 못한 구조, 빈 배열로 처리:', data);
        return [];
      };

      let collected: any[] = [];

      if (searchQuery.trim()) {
        // 검색어가 있는 경우 - 통합 검색 API 사용
        const raw = await searchContent(searchQuery);
        collected = normalizeToArray(raw);
      } else if (selectedGenres.length > 0) {
        // 장르가 선택된 경우
        const genrePromises = selectedGenres.map(genre => getAnimeByGenre(genre));
        const genreResults = await Promise.all(genrePromises);
        collected = genreResults.flatMap(normalizeToArray);
      } else if (selectedTags.length > 0) {
        // 태그가 선택된 경우
        const tagPromises = selectedTags.map(tag => getAnimeByTag(tag));
        const tagResults = await Promise.all(tagPromises);
        collected = tagResults.flatMap(normalizeToArray);
      }

      let uniqueResults: any[] = [];
      if (Array.isArray(collected)) {
        uniqueResults = collected.filter((anime, index, self) => 
          index === self.findIndex(a => a && anime && a.id === anime.id)
        );
      } else {
        console.warn('[검색응답] 배열이 아님. 빈 배열로 처리:', collected);
        uniqueResults = [];
      }

      setAnimes(uniqueResults);
      if ((searchQuery.trim() || selectedGenres.length || selectedTags.length) && uniqueResults.length === 0) {
        console.log('[검색] 결과 없음', { searchQuery, selectedGenres, selectedTags });
      }
    } catch (err) {
      console.error('애니메이션 검색 실패:', err);
      setError('검색에 실패했습니다. 다시 시도해주세요.');
      setAnimes([]);
    } finally {
      setIsLoading(false);
    }
  };

  // 검색어 변경 핸들러
  const handleSearch = (query: string) => {
    setSearchQuery(query);
    // 검색어가 변경되면 자동으로 검색 실행
    if (query.trim()) {
      executeSearch();
    }
  };

  // 장르 선택/해제
  const toggleGenre = (genre: string) => {
    setSelectedGenres(prev => 
      prev.includes(genre) 
        ? prev.filter(g => g !== genre)
        : [...prev, genre]
    );
  };

  // 태그 선택/해제
  const toggleTag = (tag: string) => {
    setSelectedTags(prev => 
      prev.includes(tag) 
        ? prev.filter(t => t !== tag)
        : [...prev, tag]
    );
  };

  // 필터 적용
  const applyFilters = () => {
    executeSearch();
  };

  // 필터 초기화
  const resetFilters = () => {
    setSelectedGenres([]);
    setSelectedTags([]);
    setSearchQuery('');
    setAnimes([]);
  };

  // 애니메이션 클릭 핸들러
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
          <h1 className="text-3xl font-bold text-gray-800 mb-6">태그별 검색</h1>

          {/* 검색 바 */}
          <div className="mb-8">
            <SearchBar 
              onSearch={handleSearch}
              placeholder="애니메이션 제목을 검색하세요..."
              className="mb-4"
            />
            <div className="flex gap-4">
              <button
                onClick={applyFilters}
                disabled={isLoading}
                className="px-6 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:bg-gray-400 transition-colors"
              >
                {isLoading ? '검색 중...' : '필터 적용'}
              </button>
              <button
                onClick={resetFilters}
                className="px-4 py-2 bg-gray-500 text-white rounded-lg hover:bg-gray-600 transition-colors"
              >
                초기화
              </button>
            </div>
          </div>

          {/* 필터 옵션들 */}
          <div className="grid md:grid-cols-2 gap-8 mb-8">
            {/* 장르 선택 */}
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">장르</h3>
              <div className="flex flex-wrap gap-2">
                {genres.map((genre) => (
                  <button
                    key={genre}
                    onClick={() => toggleGenre(genre)}
                    className={`px-3 py-1 rounded-full text-sm transition-colors ${
                      selectedGenres.includes(genre)
                        ? 'bg-purple-600 text-white'
                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                    }`}
                  >
                    {genre}
                  </button>
                ))}
              </div>
            </div>

            {/* 태그 선택 */}
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">태그</h3>
              <div className="flex flex-wrap gap-2">
                {tags.map((tag) => (
                  <button
                    key={tag}
                    onClick={() => toggleTag(tag)}
                    className={`px-3 py-1 rounded-full text-sm transition-colors ${
                      selectedTags.includes(tag)
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                    }`}
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* 검색 결과 */}
          {error && (
            <div className="text-center py-8">
              <div className="text-red-600 text-lg mb-3">{error}</div>
              <button onClick={executeSearch} className="px-4 py-2 bg-purple-600 text-white rounded hover:bg-purple-700">재시도</button>
            </div>
          )}

          {animes.length > 0 ? (
            <div>
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-semibold text-gray-800">
                  검색 결과 ({animes.length}개)
                </h2>
              </div>
              
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
                {animes.map((anime: any) => (
                  <AnimeCard
                    key={anime.id}
                    aniId={anime.id}
                    title={anime.title}
                    posterUrl={anime.posterUrl || "https://placehold.co/200x280/4a5568/ffffff?text=No+Image"}
                    rating={anime.rating}
                    badge={anime.badges?.[0]}
                    episode={anime.episode}
                  />
                ))}
              </div>
            </div>
          ) : !isLoading && !error && (
            <div className="text-center py-12">
              {searchQuery.trim() || selectedGenres.length > 0 || selectedTags.length > 0 ? (
                <>
                  <div className="text-gray-500 text-lg mb-2">검색 결과가 없습니다.</div>
                  <p className="text-gray-400">다른 키워드로 검색해보세요.</p>
                </>
              ) : (
                <>
                  <div className="text-gray-500 text-lg mb-2">검색 조건을 선택하거나 검색어를 입력해주세요</div>
                  <p className="text-gray-400">장르, 태그, 또는 제목으로 검색할 수 있습니다</p>
                </>
              )}
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
