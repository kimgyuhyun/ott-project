"use client";
import { useState, useEffect, useRef } from "react";
import { getSearchSuggestions } from "@/lib/api/search";

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
    <div className={`relative ${className}`} ref={suggestionsRef}>
      <form onSubmit={handleSubmit} className="relative">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={placeholder}
          className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent pr-12"
        />
        <button
          type="submit"
          className="absolute right-2 top-1/2 transform -translate-y-1/2 p-2 text-gray-500 hover:text-purple-600 transition-colors"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </button>
      </form>

      {/* 자동완성 드롭다운 */}
      {showSuggestions && (suggestions.length > 0 || isLoading || error) && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 max-h-60 overflow-y-auto">
          {isLoading ? (
            <div className="p-4 text-center text-gray-500">
              검색 중...
            </div>
          ) : error ? (
            <div className="p-4 text-center text-red-600">
              {error}
              <button
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
                className="ml-3 px-2 py-1 text-sm bg-purple-600 text-white rounded hover:bg-purple-700"
              >
                재시도
              </button>
            </div>
          ) : (
            suggestions.map((suggestion, index) => (
              <button
                key={index}
                onClick={() => handleSuggestionClick(suggestion)}
                className="w-full text-left px-4 py-3 hover:bg-gray-50 transition-colors border-b border-gray-100 last:border-b-0"
              >
                <div className="flex items-center space-x-3">
                  <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  <span className="text-gray-700">{suggestion}</span>
                </div>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
