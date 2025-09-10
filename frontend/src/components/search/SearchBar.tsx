"use client";
import { useState, useEffect, useRef } from "react";
import { getSearchSuggestions } from "@/lib/api/search";
import { fetchRecentSearches, addRecentSearch, removeRecentSearch, clearRecentSearches } from "@/lib/api/recentSearch";
import styles from "./SearchBar.module.css";

interface SearchBarProps {
  onSearch: (query: string) => void;
  placeholder?: string;
  className?: string;
  align?: 'left' | 'right';
  autoShow?: boolean;
  showSuggestions?: boolean;
}

export default function SearchBar({ onSearch, placeholder = "검색어를 입력하세요...", className = "", align = 'right', autoShow = true, showSuggestions: externalShowSuggestions }: SearchBarProps) {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isRecentLoading, setIsRecentLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchTimeoutRef = useRef<NodeJS.Timeout>();
  const suggestionsRef = useRef<HTMLDivElement>(null);
  const [hasLoadedRecent, setHasLoadedRecent] = useState(false);

  // 최근 검색어 로드 (초기 1회만)
  useEffect(() => {
    if (!hasLoadedRecent) {
      loadRecentSearches();
      setHasLoadedRecent(true);
    }
  }, [hasLoadedRecent]);

  // autoShow가 true일 때만 자동으로 드롭다운 표시
  useEffect(() => {
    if (autoShow && hasLoadedRecent) {
      setShowSuggestions(true);
    }
  }, [autoShow, hasLoadedRecent]);

  // 외부에서 showSuggestions 제어
  useEffect(() => {
    if (externalShowSuggestions !== undefined) {
      setShowSuggestions(externalShowSuggestions);
    } else if (autoShow && hasLoadedRecent) {
      setShowSuggestions(true);
    }
  }, [externalShowSuggestions, autoShow, hasLoadedRecent]);

  // 검색어가 변경될 때마다 자동완성 요청
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }

    if (query.trim().length > 0) {
      searchTimeoutRef.current = setTimeout(async () => {
        try {
          setIsLoading(true);
          setError(null);
          const results = await getSearchSuggestions(query, 8);
          const titles = Array.isArray(results) ? results.map((item: any) => item.title) : [];
          setSuggestions(titles.filter(Boolean));
          setShowSuggestions(true);
        } catch (error) {
          console.error('자동완성 로드 실패:', error);
          setError('자동완성 로드에 실패했습니다.');
          setSuggestions([]);
          setShowSuggestions(true);
        } finally {
          setIsLoading(false);
        }
      }, 300); // 300ms 딜레이
    } else {
      setSuggestions([]);
      setShowSuggestions(false);
      setError(null);
    }

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
  }, [query]);

  useEffect(() => {
    // 외부 클릭 시 자동완성 닫기
    const handleClickOutside = (event: MouseEvent) => {
      if (suggestionsRef.current && !suggestionsRef.current.contains(event.target as Node)) {
        setShowSuggestions(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // 최근 검색어 로드
  const loadRecentSearches = async () => {
    try {
      setIsRecentLoading(true);
      const searches = await fetchRecentSearches();
      setRecentSearches(searches);
    } catch (error) {
      console.error('최근 검색어 로드 실패:', error);
    } finally {
      setIsRecentLoading(false);
    }
  };

  // 검색어 추가 (낙관적 업데이트)
  const handleAddRecentSearch = async (term: string) => {
    try {
      // 낙관적 업데이트
      const optimisticSearches = [term, ...recentSearches.filter(s => s !== term)].slice(0, 10);
      setRecentSearches(optimisticSearches);
      
      // 서버 동기화
      const updatedSearches = await addRecentSearch(term);
      setRecentSearches(updatedSearches);
    } catch (error) {
      console.error('최근 검색어 추가 실패:', error);
      // 롤백
      loadRecentSearches();
    }
  };

  // 검색어 삭제 (낙관적 업데이트)
  const handleRemoveRecentSearch = async (term: string) => {
    try {
      // 낙관적 업데이트
      const optimisticSearches = recentSearches.filter(s => s !== term);
      setRecentSearches(optimisticSearches);
      
      // 서버 동기화
      const updatedSearches = await removeRecentSearch(term);
      setRecentSearches(updatedSearches);
    } catch (error) {
      console.error('최근 검색어 삭제 실패:', error);
      // 롤백
      loadRecentSearches();
    }
  };

  // 전체 삭제
  const handleClearRecentSearches = async () => {
    if (!confirm('모든 최근 검색어를 삭제하시겠습니까?')) return;
    
    try {
      // 낙관적 업데이트
      setRecentSearches([]);
      
      // 서버 동기화
      await clearRecentSearches();
    } catch (error) {
      console.error('최근 검색어 전체 삭제 실패:', error);
      // 롤백
      loadRecentSearches();
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSearch(query.trim());
      handleAddRecentSearch(query.trim()); // 최근 검색어에 추가
      setShowSuggestions(false);
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    setQuery(suggestion);
    onSearch(suggestion);
    handleAddRecentSearch(suggestion); // 최근 검색어에 추가
    setShowSuggestions(false);
  };

  return (
    <div className={`${styles.searchContainer} ${align === 'right' ? styles.alignRight : styles.alignLeft} ${className}`} ref={suggestionsRef}>
      <form className={styles.searchForm} onSubmit={handleSubmit}>
        <input
          type="text"
          className={styles.searchInput}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => {
            if (autoShow || externalShowSuggestions !== undefined) {
              setShowSuggestions(true);
            }
          }}
          placeholder={placeholder}
        />
        <button type="submit" className={styles.searchButton}>
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </button>
      </form>

      {/* 드롭다운 */}
      {showSuggestions && (
        <div className={styles.searchSuggestions}>
          {query.trim().length > 0 ? (
            // 검색어가 있을 때: 자동완성
            <>
              {isLoading ? (
                <div className={styles.searchLoading}>
                  검색 중...
                </div>
              ) : error ? (
                <div className={styles.searchError}>
                  {error}
                  <button
                    className={styles.searchRetryButton}
                    onClick={() => {
                      setError(null);
                      setIsLoading(true);
                      getSearchSuggestions(query, 8)
                        .then(results => {
                          const titles = Array.isArray(results) ? results.map((item: any) => item.title) : [];
                          setSuggestions(titles.filter(Boolean));
                        })
                        .catch(() => setError('자동완성 로드에 실패했습니다.'))
                        .finally(() => setIsLoading(false));
                    }}
                  >
                    재시도
                  </button>
                </div>
              ) : (
                suggestions.map((suggestion, index) => (
                  <div
                    key={index}
                    className={styles.searchSuggestionItem}
                    onClick={() => handleSuggestionClick(suggestion)}
                  >
                    <div className={styles.searchSuggestionContent}>
                      <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      <span>{suggestion}</span>
                    </div>
                  </div>
                ))
              )}
            </>
          ) : (
            // 검색어가 없을 때: 최근 검색어 + 인기 검색어
            <>
              {/* 최근 검색어 섹션 */}
              {recentSearches.length > 0 && (
                <div className={styles.recentSection}>
                  <div className={styles.recentHeader}>
                    <h3 className={styles.recentTitle}>최근 검색어</h3>
                    <button
                      className={styles.clearAllButton}
                      onClick={handleClearRecentSearches}
                      type="button"
                    >
                      전체 삭제
                    </button>
                  </div>
                  <div className={styles.recentList}>
                    {isRecentLoading ? (
                      <div className={styles.searchLoading}>로딩 중...</div>
                    ) : (
                      recentSearches.map((search, index) => (
                        <div
                          key={index}
                          className={styles.recentItem}
                          onClick={() => handleSuggestionClick(search)}
                        >
                          <div className={styles.recentItemContent}>
                            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <span>{search}</span>
                          </div>
                          <button
                            className={styles.removeButton}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRemoveRecentSearch(search);
                            }}
                            type="button"
                            aria-label={`${search} 삭제`}
                          >
                            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}

              {/* 인기 검색어 섹션 */}
              <div className={styles.popularSection}>
                <h3 className={styles.popularTitle}>지금 사람들이 많이 보는 작품</h3>
                <div className={styles.popularList}>
                  {[
                    "귀멸의 칼날 (자막)",
                    "귀멸의 칼날 (무삭제)",
                    "귀멸의 칼날 극장판",
                    "귀멸의 칼날 시즌2",
                    "귀멸의 칼날 시즌3",
                    "귀멸의 칼날 시즌4",
                    "귀멸의 칼날 시즌5",
                    "귀멸의 칼날 시즌6",
                    "귀멸의 칼날 시즌7",
                    "귀멸의 칼날 시즌8"
                  ].map((item, index) => (
                    <div
                      key={index}
                      className={styles.popularItem}
                      onClick={() => handleSuggestionClick(item)}
                    >
                      <span className={styles.popularNumber}>{index + 1}</span>
                      <span className={styles.popularText}>{item}</span>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
