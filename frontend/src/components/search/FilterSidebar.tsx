"use client";

import { useState } from "react";
import styles from "./FilterSidebar.module.css";
import FilterModal from "./FilterModal";

interface FilterSidebarProps {
  selectedGenreIds: number[];
  selectedTagIds: number[];
  selectedSeasons: string[];
  selectedStatuses: string[];
  selectedTypes: string[];
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
  seasonOptions: string[];
  yearOptions: {value: string; label: string; type: string}[];
  statusOptions: {key: string; label: string}[];
  typeOptions: {key: string; label: string}[];
  onGenreChange: (genreId: number) => void;
  onTagChange: (tagId: number) => void;
  onSeasonChange: (season: string) => void;
  onStatusChange: (status: string) => void;
  onTypeChange: (type: string) => void;
  onFilterChange: (key: string, value: boolean) => void;
  onResetFilters: () => void;
}

/**
 * 필터 사이드바 컴포넌트
 * 좌측에 위치하며 장르, 태그, 고급 필터 옵션을 제공
 */
export default function FilterSidebar({
  selectedGenreIds,
  selectedTagIds,
  selectedSeasons,
  selectedStatuses,
  selectedTypes,
  searchQuery,
  genreOptions,
  tagOptions,
  seasonOptions,
  yearOptions,
  statusOptions,
  typeOptions,
  onGenreChange,
  onTagChange,
  onSeasonChange,
  onStatusChange,
  onTypeChange,
  onResetFilters
}: FilterSidebarProps) {
  // 백엔드에서 이미 번역된 데이터 사용
  const genres = genreOptions ?? [];
  const tags = tagOptions ?? [];
  const years = yearOptions ?? [];
  const statuses = statusOptions ?? [];
  const types = typeOptions ?? [];

  // 더보기 상태 관리
  const [showMoreGenres, setShowMoreGenres] = useState(false);
  const [showMoreTags, setShowMoreTags] = useState(false);
  const [showMoreSeasons, setShowMoreSeasons] = useState(false);
  const [showMoreStatuses, setShowMoreStatuses] = useState(false);
  const [showMoreTypes, setShowMoreTypes] = useState(false);

  // 모달용 토글 어댑터 (매개변수 통일)
  const handleToggleGenre = (item: number | string) => {
    if (typeof item === 'number') onGenreChange(item);
  };
  const handleToggleTag = (item: number | string) => {
    if (typeof item === 'number') onTagChange(item);
  };
  const handleToggleSeason = (item: number | string) => {
    if (typeof item === 'string') onSeasonChange(item);
  };
  const handleToggleStatus = (item: number | string) => {
    if (typeof item === 'string') onStatusChange(item);
  };
  const handleToggleType = (item: number | string) => {
    if (typeof item === 'string') onTypeChange(item);
  };

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
            장르
          </h3>
          <button 
            className={styles.moreButton}
            onClick={() => setShowMoreGenres(true)}
          >
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {genres.slice(0, 5).map((genre) => (
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
            태그
          </h3>
          <button 
            className={styles.moreButton}
            onClick={() => setShowMoreTags(true)}
          >
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {tags.slice(0, 5).map((tag) => (
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

      {/* 년도 필터 */}
      <div className={styles.filterSection}>
        <div className={styles.filterSectionHeader}>
          <h3 className={styles.filterSectionTitle}>
            년도
          </h3>
          <button 
            className={styles.moreButton}
            onClick={() => setShowMoreSeasons(true)}
          >
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {years.slice(0, 5).map((year) => (
            <label key={year.value} className={styles.checkboxItem}>
              <input
                type="checkbox"
                checked={selectedSeasons.includes(year.value)}
                onChange={() => onSeasonChange(year.value)}
                className={`${styles.checkbox} ${styles.yearCheckbox}`}
              />
              <span className={styles.checkboxLabel}>{year.label}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 방영 상태 필터 */}
      <div className={styles.filterSection}>
        <div className={styles.filterSectionHeader}>
          <h3 className={styles.filterSectionTitle}>
            방영
          </h3>
          <button 
            className={styles.moreButton}
            onClick={() => setShowMoreStatuses(true)}
          >
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {statuses.slice(0, 5).map((status) => (
            <label key={status.key} className={styles.checkboxItem}>
              <input
                type="checkbox"
                checked={selectedStatuses.includes(status.key)}
                onChange={() => onStatusChange(status.key)}
                className={`${styles.checkbox} ${styles.statusCheckbox}`}
              />
              <span className={styles.checkboxLabel}>{status.label}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 출시 타입 필터 */}
      <div className={styles.filterSection}>
        <div className={styles.filterSectionHeader}>
          <h3 className={styles.filterSectionTitle}>
            출시타입
          </h3>
          <button 
            className={styles.moreButton}
            onClick={() => setShowMoreTypes(true)}
          >
            더 보기 &gt;
          </button>
        </div>
        <div className={styles.checkboxList}>
          {types.slice(0, 5).map((type) => (
            <label key={type.key} className={styles.checkboxItem}>
              <input
                type="checkbox"
                checked={selectedTypes.includes(type.key)}
                onChange={() => onTypeChange(type.key)}
                className={`${styles.checkbox} ${styles.typeCheckbox}`}
              />
              <span className={styles.checkboxLabel}>{type.label}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 장르 모달 */}
      <FilterModal
        isOpen={showMoreGenres}
        onClose={() => setShowMoreGenres(false)}
        title="장르 전체"
        items={genres}
        selectedItems={selectedGenreIds}
        onItemToggle={handleToggleGenre}
        onResetAll={() => {
          selectedGenreIds.forEach(id => onGenreChange(id));
        }}
        type="genre"
      />

      {/* 태그 모달 */}
      <FilterModal
        isOpen={showMoreTags}
        onClose={() => setShowMoreTags(false)}
        title="태그 전체"
        items={tags}
        selectedItems={selectedTagIds}
        onItemToggle={handleToggleTag}
        onResetAll={() => {
          selectedTagIds.forEach(id => onTagChange(id));
        }}
        type="tag"
      />

      {/* 년도 모달 */}
      <FilterModal
        isOpen={showMoreSeasons}
        onClose={() => setShowMoreSeasons(false)}
        title="년도 전체"
        items={years.map(year => ({ key: year.value, name: year.label }))}
        selectedItems={selectedSeasons}
        onItemToggle={handleToggleSeason}
        onResetAll={() => {
          selectedSeasons.forEach(season => onSeasonChange(season));
        }}
        type="season"
      />

      {/* 상태 모달 */}
      <FilterModal
        isOpen={showMoreStatuses}
        onClose={() => setShowMoreStatuses(false)}
        title="방영 전체"
        items={statuses.map(s => ({ ...s, name: s.label }))}
        selectedItems={selectedStatuses}
        onItemToggle={handleToggleStatus}
        onResetAll={() => {
          selectedStatuses.forEach(status => onStatusChange(status));
        }}
        type="status"
      />

      {/* 타입 모달 */}
      <FilterModal
        isOpen={showMoreTypes}
        onClose={() => setShowMoreTypes(false)}
        title="출시타입 전체"
        items={types.map(t => ({ ...t, name: t.label }))}
        selectedItems={selectedTypes}
        onItemToggle={handleToggleType}
        onResetAll={() => {
          selectedTypes.forEach(type => onTypeChange(type));
        }}
        type="type"
      />
    </div>
  );
}
