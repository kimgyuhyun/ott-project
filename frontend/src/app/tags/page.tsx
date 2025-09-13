"use client";
import { useState, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import Header from "@/components/layout/Header";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import FilterSidebar from "@/components/search/FilterSidebar";
import AnimeGrid from "@/components/search/AnimeGrid";
import { searchContent } from "@/lib/api/search";
import { getGenres, getTags, getSeasons, getStatuses, getTypes, getAnimeList, listAnime } from "@/lib/api/anime";
import styles from "./TagsPage.module.css";

/**
 * 태그별 검색 페이지
 * 2단 레이아웃: 좌측 필터 사이드바 + 우측 애니메이션 그리드
 */
export default function TagsPage() {
  const searchParams = useSearchParams();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  const [animes, setAnimes] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 무한스크롤링을 위한 상태
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [cursorId, setCursorId] = useState<number | null>(null);
  const [cursorRating, setCursorRating] = useState<number | null>(null);
  
  // 필터 상태
  const [filters, setFilters] = useState({
    watchable: true,
    membership: false
  });

  const [selectedGenreIds, setSelectedGenreIds] = useState<number[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [selectedSeasons, setSelectedSeasons] = useState<string[]>([]);
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>([]);
  const [selectedTypes, setSelectedTypes] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<string>('popular');
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<string | null>(null);
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [genreOptions, setGenreOptions] = useState<{id:number; name:string; color?:string}[]>([]);
  const [tagOptions, setTagOptions] = useState<{id:number; name:string; color?:string}[]>([]);
  const [seasonOptions, setSeasonOptions] = useState<string[]>([]);
  const [statusOptions, setStatusOptions] = useState<{key: string; label: string}[]>([]);
  const [typeOptions, setTypeOptions] = useState<{key: string; label: string}[]>([]);

  // URL 검색 파라미터 처리 및 초기 데이터 로드
  useEffect(() => {
    const urlSearch = searchParams.get('search');
    if (urlSearch) {
      setSearchQuery(urlSearch);
      // URL 검색어가 있으면 자동으로 검색 실행
      setTimeout(() => {
        handleSearch(urlSearch);
      }, 100);
    } else {
      // 초기: 필터 옵션 + 인기순 목록 동시 로드(목록 API 사용)
      (async () => {
        try {
          setIsLoading(true);
          setError(null);
          const [gs, ts, ss, sts, tps, listRaw] = await Promise.all([
            getGenres(),
            getTags(),
            getSeasons(),
            getStatuses(),
            getTypes(),
            getAnimeList(0, 20, 'popular')
          ]);
          // API 응답 로그
          console.log('API 로딩 완료:', {
            genres: Array.isArray(gs) ? gs.length : 0,
            tags: Array.isArray(ts) ? ts.length : 0,
            seasons: Array.isArray(ss) ? ss.length : 0,
            statuses: Array.isArray(sts) ? sts.length : 0,
            types: Array.isArray(tps) ? tps.length : 0,
            animeList: (listRaw as any)?.items?.length || 0
          });
          
          // 장르/태그 데이터 구조 확인
          console.log('장르 데이터 샘플:', Array.isArray(gs) ? gs[0] : null);
          console.log('태그 데이터 샘플:', Array.isArray(ts) ? ts[0] : null);
          
          // window 객체에 직접 저장
          (window as any).debugGenres = gs;
          (window as any).debugTags = ts;
          setGenreOptions(Array.isArray(gs) ? gs : []);
          setTagOptions(Array.isArray(ts) ? ts : []);
          setSeasonOptions(Array.isArray(ss) ? ss : []);
          setStatusOptions(Array.isArray(sts) ? sts : []);
          setTypeOptions(Array.isArray(tps) ? tps : []);

          const normalizeToArray = (data: any): any[] => {
            const isArr = Array.isArray(data);
            const hasContent = data && Array.isArray((data as any).content);
            const hasItems = data && Array.isArray((data as any).items);
            if (isArr) return data as any[];
            if (hasContent) return (data as any).content as any[];
            if (hasItems) return (data as any).items as any[];
            return [];
          };
                     console.log('[DEBUG] 초기 listRaw:', listRaw);
           console.log('[DEBUG] listRaw.items:', (listRaw as any)?.items);
           console.log('[DEBUG] listRaw.items[0]:', (listRaw as any)?.items?.[0]);
           // 백엔드 응답 구조에 맞게 .items 사용
           const list = (listRaw as any)?.items || normalizeToArray(listRaw);
           console.log('[DEBUG] 초기 최종 list:', list);
           console.log('[DEBUG] 초기 list[0]:', list[0]);
           console.log('[DEBUG] 초기 list[0] 전체 키:', list[0] ? Object.keys(list[0]) : 'no list[0]');
           setAnimes(Array.isArray(list) ? list : []);
        } catch (e) {
          console.error('초기 데이터 로드 실패', e);
          setError('초기 데이터를 불러오지 못했습니다.');
          setAnimes([]);
        } finally {
          setIsLoading(false);
        }
      })();
    }
  }, [searchParams]);

  // 필터 변경 시 자동 검색 실행 (searchQuery 제외)
  useEffect(() => {
    // 필터가 있으면 필터링된 결과, 없으면 초기 목록
    executeSearch();
  }, [selectedGenreIds, selectedTagIds, selectedYear, selectedStatus, selectedType, filters, sortBy]);

  // searchQuery 변경 시 자동 검색 실행
  useEffect(() => {
    if (searchQuery.trim()) {
      executeSearch();
    }
  }, [searchQuery]);

  // 애니메이션 검색 실행
  const executeSearch = async (isLoadMore: boolean = false) => {
    try {
      if (isLoadMore) {
        setIsLoadingMore(true);
      } else {
        setIsLoading(true);
        setCurrentPage(0); // 새 검색 시 페이지 초기화
      }
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
      const page = isLoadMore ? currentPage + 1 : 0;

      // 정렬 값을 백엔드 API 형식으로 변환
      const getSortValue = (sort: string) => {
        switch (sort) {
          case 'latest': return 'id';
          case 'popular': return 'popular';
          case 'rating': return 'rating';
          default: return 'id';
        }
      };

      // 목록 API 사용: 키워드가 있으면 searchContent, 없으면 listAnime
      if (searchQuery.trim()) {
        const raw = await searchContent(searchQuery.trim(), undefined, undefined, getSortValue(sortBy), page, 10);
        collected = normalizeToArray(raw);
      } else {
        // listAnime 사용하여 모든 필터링을 백엔드에서 처리
        const list = await listAnime({
          genreIds: selectedGenreIds.length > 0 ? selectedGenreIds : null,
          tagIds: selectedTagIds.length > 0 ? selectedTagIds : null,
          year: selectedYear,
          status: selectedStatus,
          type: selectedType,
          sort: getSortValue(sortBy),
          page: page,
          size: 10,
          cursorId: isLoadMore ? (cursorId ?? undefined) : undefined,
          cursorRating: isLoadMore && sortBy === 'rating' ? (cursorRating ?? undefined) : undefined
        });
        console.log('[DEBUG] listAnime 응답:', list);
        console.log('[DEBUG] list.items:', (list as any)?.items);
        console.log('[DEBUG] list.items[0]:', (list as any)?.items?.[0]);
        console.log('[DEBUG] list.items[0] 전체 키:', (list as any)?.items?.[0] ? Object.keys((list as any).items[0]) : 'no items');
        // 백엔드 응답 구조에 맞게 .items 사용
        collected = (list as any)?.items || normalizeToArray(list);
        console.log('[DEBUG] 최종 collected:', collected);
        console.log('[DEBUG] collected[0]:', collected[0]);
        console.log('[DEBUG] collected[0] 전체 키:', collected[0] ? Object.keys(collected[0]) : 'no collected[0]');
      }

      // 백엔드에서 이미 필터링된 결과이므로 클라이언트 측 필터링은 최소화
      // 연도, 상태, 타입은 백엔드에서 처리됨
      
      // 고급 필터만 클라이언트에서 처리 (백엔드에서 지원하지 않는 필터들)
      if (filters.watchable) {
        collected = collected.filter(anime => anime.status !== 'UNAVAILABLE');
      }
      
      if (filters.membership) {
        collected = collected.filter(anime => anime.isExclusive === true);
      }

      let uniqueResults: any[] = [];
      if (Array.isArray(collected)) {
        uniqueResults = collected.filter((anime, index, self) => {
          const key = anime?.aniId ?? anime?.id;
          return index === self.findIndex(a => (a?.aniId ?? a?.id) === key);
        });
      } else {
        console.warn('[검색응답] 배열이 아님. 빈 배열로 처리:', collected);
        uniqueResults = [];
      }

      // 무한스크롤링 처리 + 커서 업데이트
      if (isLoadMore) {
        setAnimes(prev => [...prev, ...uniqueResults]);
        setCurrentPage(page);
      } else {
        setAnimes(uniqueResults);
        setCurrentPage(0);
        setCursorId(null);
        setCursorRating(null);
      }

      // 다음 커서 계산
      if (uniqueResults.length > 0) {
        const last = uniqueResults[uniqueResults.length - 1];
        const lastId = last?.aniId ?? last?.id;
        const lastRating = last?.rating;
        setCursorId(Number(lastId));
        setCursorRating(typeof lastRating === 'number' ? lastRating : null);
      }

      // 더 불러올 데이터가 있는지 확인 (10개 미만이면 끝)
      setHasMore(uniqueResults.length === 10);

      if ((searchQuery.trim() || selectedGenreIds.length || selectedTagIds.length) && uniqueResults.length === 0) {
        console.log('[검색] 결과 없음', { searchQuery, selectedGenreIds, selectedTagIds });
      }
    } catch (err) {
      console.error('애니메이션 검색 실패:', err);
      setError('검색에 실패했습니다. 다시 시도해주세요.');
      if (!isLoadMore) {
        setAnimes([]);
      }
    } finally {
      if (isLoadMore) {
        setIsLoadingMore(false);
      } else {
        setIsLoading(false);
      }
    }
  };

  // 검색어 변경 핸들러
  const handleSearch = (query: string) => {
    setSearchQuery(query);
    // 검색어는 useEffect에서 자동으로 처리되므로 여기서는 호출하지 않음
  };

  // 장르 선택/해제
  const toggleGenre = (genreId: number) => {
    setSelectedGenreIds(prev => 
      prev.includes(genreId) 
        ? prev.filter(g => g !== genreId)
        : [...prev, genreId]
    );
  };

  // 태그 선택/해제
  const toggleTag = (tagId: number) => {
    setSelectedTagIds(prev => 
      prev.includes(tagId) 
        ? prev.filter(g => g !== tagId)
        : [...prev, tagId]
    );
  };

  // 고급 필터 변경
  const handleFilterChange = (key: string, value: boolean) => {
    setFilters(prev => ({
      ...prev,
      [key]: value
    }));
  };

  // 연도/상태 변경 (기존 단일 선택)
  const handleYearChange = (year: number | null) => setSelectedYear(year);
  const handleStatusChange = (status: string | null) => setSelectedStatus(status);
  const handleTypeChange = (type: string | null) => setSelectedType(type);

  // 시즌/상태/타입 다중 선택 핸들러
  const handleSeasonToggle = (season: string) => {
    setSelectedSeasons(prev => 
      prev.includes(season) 
        ? prev.filter(s => s !== season)
        : [...prev, season]
    );
  };

  const handleStatusToggle = (status: string) => {
    setSelectedStatuses(prev => 
      prev.includes(status) 
        ? prev.filter(s => s !== status)
        : [...prev, status]
    );
  };

  const handleTypeToggle = (type: string) => {
    setSelectedTypes(prev => 
      prev.includes(type) 
        ? prev.filter(t => t !== type)
        : [...prev, type]
    );
  };

  // 필터 초기화
  const resetFilters = () => {
    setSelectedGenreIds([]);
    setSelectedTagIds([]);
    setSelectedSeasons([]);
    setSelectedStatuses([]);
    setSelectedTypes([]);
    setSearchQuery('');
    setSortBy('popular');
    setSelectedYear(null);
    setSelectedStatus(null);
    setSelectedType(null);
    setFilters({
      watchable: true,
      membership: false
    });
    setAnimes([]);
    setCurrentPage(0);
    setHasMore(true);
  };

  // 스크롤 이벤트 감지하여 무한스크롤링 처리
  const handleScroll = () => {
    if (isLoadingMore || !hasMore || isLoading) return; // 로딩 중이면 추가 호출 방지
    
    const scrollTop = window.scrollY;
    const windowHeight = window.innerHeight;
    const documentHeight = document.documentElement.scrollHeight;
    
    // 페이지 하단에 거의 도달했을 때 (800px 여유로 조정)
    if (scrollTop + windowHeight >= documentHeight - 800) {
      // 디바운싱: 연속 호출 방지
      if (!isLoadingMore) {
        executeSearch(true); // 추가 로드
      }
    }
  };

  // 스크롤 이벤트 리스너 등록/해제 (디바운싱 적용)
  useEffect(() => {
    let timeoutId: NodeJS.Timeout;
    
    const debouncedHandleScroll = () => {
      clearTimeout(timeoutId);
      timeoutId = setTimeout(handleScroll, 100); // 100ms 디바운싱
    };
    
    window.addEventListener('scroll', debouncedHandleScroll);
    return () => {
      window.removeEventListener('scroll', debouncedHandleScroll);
      clearTimeout(timeoutId);
    };
  }, [currentPage, hasMore, isLoadingMore, isLoading]);

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  return (
    <div className={styles.root}>
      <Header />
      
      <main className={styles.mainContainer}>
        {/* 2단 레이아웃: 좌측 필터 + 우측 결과 */}
        <div className={styles.layoutContainer}>
          {/* 좌측 필터 사이드바 */}
          <FilterSidebar
            selectedGenreIds={selectedGenreIds}
            selectedTagIds={selectedTagIds}
            selectedSeasons={selectedSeasons}
            selectedStatuses={selectedStatuses}
            selectedTypes={selectedTypes}
            filters={filters}
            searchQuery={searchQuery}
            selectedYear={selectedYear}
            selectedStatus={selectedStatus}
            selectedType={selectedType}
            genreOptions={genreOptions}
            tagOptions={tagOptions}
            seasonOptions={seasonOptions}
            statusOptions={statusOptions}
            typeOptions={typeOptions}
            onGenreChange={toggleGenre}
            onTagChange={toggleTag}
            onSeasonChange={handleSeasonToggle}
            onStatusChange={handleStatusToggle}
            onTypeChange={handleTypeToggle}
            onFilterChange={handleFilterChange}
            onResetFilters={resetFilters}
          />

          {/* 우측 애니메이션 그리드 */}
          <AnimeGrid
            animes={animes}
            isLoading={isLoading}
            error={error}
            searchQuery={searchQuery}
            selectedGenres={selectedGenreIds.map(id => genreOptions.find(g=>g.id===id)?.name ?? String(id))}
            selectedTags={selectedTagIds.map(id => tagOptions.find(t=>t.id===id)?.name ?? String(id))}
            selectedSeasons={selectedSeasons}
            selectedStatuses={selectedStatuses}
            selectedTypes={selectedTypes}
            sortBy={sortBy}
            onSortChange={setSortBy}
            onAnimeClick={handleAnimeClick}
          />
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
