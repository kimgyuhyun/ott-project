"use client";

import { useState } from "react";
import styles from "./FilterModal.module.css";

interface FilterModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  items: { id?: number; key?: string; name: string; label?: string }[];
  selectedItems: (number | string)[];
  onItemToggle: (item: number | string) => void;
  onResetAll: () => void;
  type: 'genre' | 'tag' | 'season' | 'status' | 'type';
}

/**
 * 필터 모달 컴포넌트
 * 더보기 버튼 클릭 시 나타나는 모달로 모든 필터 옵션을 표시
 */
export default function FilterModal({
  isOpen,
  onClose,
  title,
  items,
  selectedItems,
  onItemToggle,
  onResetAll,
  type
}: FilterModalProps) {
  if (!isOpen) return null;

  const handleItemClick = (item: number | string) => {
    onItemToggle(item);
  };

  const getItemKey = (item: any) => {
    return item.id || item.key || item.name;
  };

  const getItemLabel = (item: any) => {
    return item.label || item.name;
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <div className={styles.headerContent}>
            <h2 className={styles.modalTitle}>{title}</h2>
            <div className={styles.modalDescription}>
              원치 않는 필터는 체크 박스를 한번 더 누르면 제외 할 수 있어요.
            </div>
          </div>
          <button className={styles.closeButton} onClick={onClose}>
            ×
          </button>
        </div>
        
        <div className={styles.modalBody}>
          <div className={styles.checkboxGrid}>
            {items.map((item) => {
              const key = getItemKey(item);
              const label = getItemLabel(item);
              const isSelected = selectedItems.includes(key);
              
              return (
                <label key={key} className={styles.checkboxItem}>
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={() => handleItemClick(key)}
                    className={`${styles.checkbox} ${styles[`${type}Checkbox`]}`}
                  />
                  <span className={styles.checkboxLabel}>{label}</span>
                </label>
              );
            })}
          </div>
        </div>
        
        <div className={styles.modalFooter}>
          <button className={styles.resetButton} onClick={onResetAll}>
            <svg className={styles.resetIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
              <path d="M21 3v5h-5"/>
              <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
              <path d="M3 21v-5h5"/>
            </svg>
            전체 초기화
          </button>
          <button className={styles.confirmButton} onClick={onClose}>
            확인
          </button>
        </div>
      </div>
    </div>
  );
}
