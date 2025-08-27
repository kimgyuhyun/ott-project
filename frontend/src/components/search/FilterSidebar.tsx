"use client";

interface FilterSidebarProps {
  selectedGenreIds: number[];
  selectedTagIds: number[];
  filters: {
    watchable: boolean;
    membership: boolean;
  };
  searchQuery: string;
  selectedYear?: number | null;
  selectedStatus?: string | null;
  selectedType?: string | null;
  genreOptions: { id: number; name: string; color?: string }[];
  tagOptions: { id: number; name: string; color?: string }[];
  onGenreChange: (genreId: number) => void;
  onTagChange: (tagId: number) => void;
  onFilterChange: (key: string, value: boolean) => void;
  onYearChange?: (year: number | null) => void;
  onStatusChange?: (status: string | null) => void;
  onTypeChange?: (type: string | null) => void;
  onResetFilters: () => void;
}

/**
 * 필터 사이드바 컴포넌트
 * 좌측에 위치하며 장르, 태그, 고급 필터 옵션을 제공
 */
export default function FilterSidebar({
  selectedGenreIds,
  selectedTagIds,
  filters,
  searchQuery,
  selectedYear,
  selectedStatus,
  selectedType,
  genreOptions,
  tagOptions,
  onGenreChange,
  onTagChange,
  onFilterChange,
  onYearChange,
  onStatusChange,
  onTypeChange,
  onResetFilters
}: FilterSidebarProps) {
  const genres = genreOptions ?? [];
  const tags = tagOptions ?? [];

  const years = Array.from({length: 6}, (_, i) => new Date().getFullYear() - i);
  const statuses = [
    { key: 'ONGOING', label: '방영중' },
    { key: 'COMPLETED', label: '완결' },
    { key: 'UPCOMING', label: '방영예정' },
    { key: 'HIATUS', label: '방영중단' },
  ];

  return (
    <div className="w-full lg:w-80 bg-white lg:border-r border-gray-200 p-6 overflow-y-auto">
      {/* 필터 헤더 */}
      <div className="mb-6">
        <h2 className="text-xl font-bold text-gray-800 mb-3">필터</h2>
        <button
          onClick={onResetFilters}
          className="w-full px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors text-sm"
        >
          전체 초기화
          {(selectedGenreIds.length > 0 || selectedTagIds.length > 0 || searchQuery.trim()) && (
            <span className="ml-2 text-xs bg-gray-300 text-gray-600 px-2 py-1 rounded-full">
              {selectedGenreIds.length + selectedTagIds.length + (searchQuery.trim() ? 1 : 0)}개
            </span>
          )}
        </button>
      </div>

      {/* 장르 필터 */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-800">
            장르 {selectedGenreIds.length > 0 && (
              <span className="text-sm text-purple-600 font-normal">({selectedGenreIds.length}개 선택)</span>
            )}
          </h3>
          <button className="text-purple-600 text-sm hover:text-purple-700 transition-colors">
            더 보기 &gt;
          </button>
        </div>
        <div className="space-y-2 max-h-48 overflow-y-auto">
          {genres.map((genre) => (
            <label key={genre.id} className="flex items-center space-x-3 cursor-pointer hover:bg-gray-50 p-2 rounded transition-colors">
              <input
                type="checkbox"
                checked={selectedGenreIds.includes(genre.id)}
                onChange={() => onGenreChange(genre.id)}
                className="w-4 h-4 text-purple-600 bg-gray-100 border-gray-300 rounded focus:ring-purple-500 focus:ring-2"
              />
              <span className="text-gray-700 text-sm">{genre.name}</span>
            </label>
          ))}
        </div>
      </div>
      {/* 태그 필터 */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-800">
            태그 {selectedTagIds.length > 0 && (
              <span className="text-sm text-blue-600 font-normal">({selectedTagIds.length}개 선택)</span>
            )}
          </h3>
          <button className="text-purple-700 text-sm hover:text-purple-800 transition-colors">
            더 보기 &gt;
          </button>
        </div>
        <div className="space-y-2 max-h-48 overflow-y-auto">
          {tags.map((tag) => (
            <label key={tag.id} className="flex items-center space-x-3 cursor-pointer hover:bg-gray-50 p-2 rounded transition-colors">
              <input
                type="checkbox"
                checked={selectedTagIds.includes(tag.id)}
                onChange={() => onTagChange(tag.id)}
                className="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 focus:ring-2"
              />
              <span className="text-gray-700 text-sm">{tag.name}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 연도 필터 */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-2">
          <h3 className="text-lg font-semibold text-gray-800">연도</h3>
        </div>
        <select
          value={selectedYear ?? ''}
          onChange={(e) => onYearChange?.(e.target.value ? Number(e.target.value) : null)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <option value="">전체</option>
          {years.map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* 방영 상태 필터 */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-2">
          <h3 className="text-lg font-semibold text-gray-800">방영 상태</h3>
        </div>
        <select
          value={selectedStatus ?? ''}
          onChange={(e) => onStatusChange?.(e.target.value ? e.target.value : null)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <option value="">전체</option>
          {statuses.map(s => (
            <option key={s.key} value={s.key}>{s.label}</option>
          ))}
        </select>
      </div>

      {/* 출시 타입 필터 */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-2">
          <h3 className="text-lg font-semibold text-gray-800">출시 타입</h3>
        </div>
        <select
          value={selectedType ?? ''}
          onChange={(e) => onTypeChange?.(e.target.value ? e.target.value : null)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <option value="">전체</option>
          <option value="TV">TV</option>
          <option value="MOVIE">MOVIE</option>
          <option value="OVA">OVA</option>
          <option value="SPECIAL">SPECIAL</option>
          <option value="WEB">WEB</option>
        </select>
      </div>
    </div>
  );
}
