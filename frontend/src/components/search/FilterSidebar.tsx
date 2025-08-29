"use client";

import styles from "./FilterSidebar.module.css";

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
    <div className={styles.filterSidebar}>
      {/* 필터 헤더 */}
      <div className={styles.filterHeader}>
        <h2 className={styles.filterTitle}>필터</h2>
        <button
          onClick={onResetFilters}
          className={styles.resetButton}
        >
          전체 초기화
          {(selectedGenreIds.length > 0 || selectedTagIds.length > 0 || searchQuery.trim()) && (
            <span className={styles.filterCount}>
              {selectedGenreIds.length + selectedTagIds.length + (searchQuery.trim() ? 1 : 0)}개
            </span>
          )}
        </button>
      </div>

      {/* 장르 필터 */}
      <div className={styles.filterSection}>
        <div className={styles.filterSectionHeader}>
          <h3 className={styles.filterSectionTitle}>
            장르 {selectedGenreIds.length > 0 && (
              <span className={styles.filterCount}>({selectedGenreIds.length}개 선택)</span>
            )}
          </h3>
          <button className={styles.moreButton}>
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {genres.map((genre) => (
            <label key={genre.id} className={styles.checkboxItem}>
              <input
                type="checkbox"
                checked={selectedGenreIds.includes(genre.id)}
                onChange={() => onGenreChange(genre.id)}
                className={`${styles.checkbox} ${styles.genreCheckbox}`}
              />
              <span className={styles.checkboxLabel}>{genre.name}</span>
            </label>
          ))}
        </div>
      </div>
      
      {/* 태그 필터 */}
      <div className={styles.filterSection}>
        <div className={styles.filterSectionHeader}>
          <h3 className={styles.filterSectionTitle}>
            태그 {selectedTagIds.length > 0 && (
              <span className={styles.filterCount}>({selectedTagIds.length}개 선택)</span>
            )}
          </h3>
          <button className={styles.moreButton}>
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {tags.map((tag) => (
            <label key={tag.id} className={styles.checkboxItem}>
              <input
                type="checkbox"
                checked={selectedTagIds.includes(tag.id)}
                onChange={() => onTagChange(tag.id)}
                className={`${styles.checkbox} ${styles.tagCheckbox}`}
              />
              <span className={styles.checkboxLabel}>{tag.name}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 연도 필터 */}
      <div className={styles.selectContainer}>
        <div className={styles.selectLabel}>연도</div>
        <select
          value={selectedYear ?? ''}
          onChange={(e) => onYearChange?.(e.target.value ? Number(e.target.value) : null)}
          className={styles.select}
        >
          <option value="">전체</option>
          {years.map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* 방영 상태 필터 */}
      <div className={styles.selectContainer}>
        <div className={styles.selectLabel}>방영 상태</div>
        <select
          value={selectedStatus ?? ''}
          onChange={(e) => onStatusChange?.(e.target.value ? e.target.value : null)}
          className={styles.select}
        >
          <option value="">전체</option>
          {statuses.map(s => (
            <option key={s.key} value={s.key}>{s.label}</option>
          ))}
        </select>
      </div>

      {/* 출시 타입 필터 */}
      <div className={styles.selectContainer}>
        <div className={styles.selectLabel}>출시 타입</div>
        <select
          value={selectedType ?? ''}
          onChange={(e) => onTypeChange?.(e.target.value ? e.target.value : null)}
          className={styles.select}
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
