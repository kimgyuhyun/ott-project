"use client";
import AnimeCard from "@/components/home/AnimeCard";
import styles from "./AnimeGrid.module.css";

interface AnimeGridProps {
  animes: any[];
  isLoading: boolean;
  error: string | null;
  searchQuery: string;
  selectedGenres: string[];
  selectedTags: string[];
  sortBy: string;
  onSortChange: (sort: string) => void;
  onAnimeClick: (anime: any) => void;
}

/**
 * 애니메이션 그리드 컴포넌트
 * 우측 메인 영역에 검색 결과를 5열 그리드로 표시
 */
export default function AnimeGrid({
  animes,
  isLoading,
  error,
  searchQuery,
  selectedGenres,
  selectedTags,
  sortBy,
  onSortChange,
  onAnimeClick
}: AnimeGridProps) {
  // 검색 조건이 있는지 확인
  const hasSearchCriteria = searchQuery.trim() || selectedGenres.length > 0 || selectedTags.length > 0 || animes.length > 0;

  if (isLoading) {
    return (
      <div className={styles.animeGridContainer}>
        <div className={styles.loadingContainer}>
          <div className={styles.loadingSpinner}></div>
          <p className={styles.loadingText}>검색 중...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.animeGridContainer}>
        <div className={styles.errorContainer}>
          <div className={styles.errorMessage}>{error}</div>
          <p className={styles.errorHelp}>검색 중 오류가 발생했습니다.</p>
        </div>
      </div>
    );
  }

  if (!hasSearchCriteria) {
    return (
      <div className={styles.animeGridContainer}>
        <div className={styles.searchGuideContainer}>
          <div className={styles.searchGuideTitle}>검색 조건을 선택하거나 검색어를 입력해주세요</div>
          <p className={styles.searchGuideSubtitle}>장르, 태그, 또는 제목으로 검색할 수 있습니다</p>
          <div className={styles.searchGuideTips}>
            <p>• 상단 검색바에 애니메이션 제목을 입력하세요</p>
            <p>• 좌측 사이드바에서 장르나 태그를 선택하세요</p>
            <p>• 고급 필터 옵션을 활용해보세요</p>
          </div>
        </div>
      </div>
    );
  }

  if (animes.length === 0) {
    return (
      <div className={styles.animeGridContainer}>
        <div className={styles.noResultsContainer}>
          <div className={styles.noResultsTitle}>검색 결과가 없습니다.</div>
          <p className={styles.noResultsSubtitle}>다른 키워드로 검색해보세요.</p>
          <div className={styles.noResultsTips}>
            <p>• 검색어를 변경해보세요</p>
            <p>• 다른 장르나 태그를 선택해보세요</p>
            <p>• 필터 조건을 조정해보세요</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.animeGridContainer}>
      {/* 검색 결과 헤더 */}
      <div className={styles.searchHeader}>
        <div className={styles.searchHeaderTop}>
          <h2 className={styles.searchTitle}>
            검색 결과 ({animes.length}개)
          </h2>
          
          {/* 정렬 옵션 */}
          <div className={styles.sortContainer}>
            <span className={styles.sortLabel}>정렬:</span>
            <select 
              value={sortBy}
              onChange={(e) => onSortChange(e.target.value)}
              className={styles.sortSelect}
            >
              <option value="latest">최신순</option>
              <option value="popular">인기순</option>
              <option value="rating">평점순</option>
            </select>
          </div>
        </div>
        
        {/* 검색 조건 표시 */}
        {(searchQuery.trim() || selectedGenres.length > 0 || selectedTags.length > 0) && (
          <div className={styles.searchCriteria}>
            {searchQuery.trim() && (
              <span className={`${styles.searchCriteriaTag} ${styles.searchCriteriaTagSearch}`}>
                검색어: {searchQuery}
              </span>
            )}
            {selectedGenres.map((genre) => (
              <span key={genre} className={`${styles.searchCriteriaTag} ${styles.searchCriteriaTagGenre}`}>
                장르: {genre}
              </span>
            ))}
            {selectedTags.map((tag) => (
              <span key={tag} className={`${styles.searchCriteriaTag} ${styles.searchCriteriaTagTag}`}>
                태그: {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* 애니메이션 그리드 */}
      <div className={styles.animeGrid}>
        {(() => {
          const seen = new Set<string | number>();
          const safeItems = (Array.isArray(animes) ? animes : [])
            .filter((item) => {
              // 더 엄격한 필터링: title이 반드시 있어야 함
              const hasValidId = item && (item.id != null || item.aniId != null);
              
              // key는 aniId 우선, 없으면 id 사용
              const key = item.aniId ?? item.id;
              
              const hasValidTitle = item.title && typeof item.title === 'string' && item.title.trim() !== '';
              console.log('[DEBUG] 필터링:', { item, hasValidId, hasValidTitle, title: item.title });
              return hasValidId && hasValidTitle;
            })
            .filter((item) => {
              const key = item.id ?? item.aniId;
              if (seen.has(key)) return false;
              seen.add(key);
              return true;
            });
          
          console.log('[DEBUG] 최종 safeItems:', safeItems);
          
          return safeItems.map((anime: any, index: number) => {
            const itemId = anime.aniId ?? anime.id ?? index;
            const key = `${itemId}-${anime.title}`;
            
            console.log('[DEBUG] AnimeCard 렌더링:', { anime, title: anime.title, posterUrl: anime.posterUrl, imageUrl: anime.imageUrl });
            
            // 백엔드 데이터 구조에 맞게 이미지 URL 매핑
            const posterUrl = anime.posterUrl || anime.imageUrl || anime.thumbnail || 
                             `https://placehold.co/200x280/4a5568/ffffff?text=${encodeURIComponent(anime.title)}`;
            
            return (
              <AnimeCard
                key={key}
                aniId={Number(itemId)}
                title={anime.title}
                posterUrl={posterUrl}
                rating={anime.rating}
                badge={anime.badges?.[0]}
                episode={anime.episode}
                onClick={() => onAnimeClick(anime)}
              />
            );
          });
        })()}
      </div>
    </div>
  );
}
