"use client";
import { useState, useEffect, useRef } from "react";
import { getSearchSuggestions } from "@/lib/api/search";
import styles from "./SearchBar.module.css";

interface SearchBarProps {
  onSearch: (query: string) => void;
  placeholder?: string;
  className?: string;
}

export default function SearchBar({ onSearch, placeholder = "검색어를 입력하세요...", className = "" }: SearchBarProps) {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchTimeoutRef = useRef<NodeJS.Timeout>();
  const suggestionsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 검색어가 변경될 때마다 자동완성 요청
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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSearch(query.trim());
      setShowSuggestions(false);
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    setQuery(suggestion);
    onSearch(suggestion);
    setShowSuggestions(false);
  };

  return (
    <div className={`${styles.searchContainer} ${className}`} ref={suggestionsRef}>
      <form className={styles.searchForm} onSubmit={handleSubmit}>
        <input
          type="text"
          className={styles.searchInput}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={placeholder}
        />
        <button type="submit" className={styles.searchButton}>
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </button>
      </form>

      {/* 자동완성 드롭다운 */}
      {showSuggestions && (suggestions.length > 0 || isLoading || error) && (
        <div className={styles.searchSuggestions}>
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
        </div>
      )}
    </div>
  );
}
