"use client";
import { useState, useRef, useEffect } from "react";
import styles from "./DropdownMenu.module.css";

interface DropdownMenuProps {
  children: React.ReactNode;
  items: {
    label: string;
    onClick: () => void;
    className?: string;
  }[];
}

/**
 * 드롭다운 메뉴 컴포넌트
 * 세 점 아이콘을 클릭하면 메뉴 아이템들이 표시됩니다
 */
export default function DropdownMenu({ children, items }: DropdownMenuProps) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleItemClick = (onClick: () => void) => {
    onClick();
    setIsOpen(false);
  };

  return (
    <div className={styles.dropdownContainer} ref={dropdownRef}>
      <button
        className={styles.dropdownTrigger}
        onClick={() => setIsOpen(!isOpen)}
        aria-label="메뉴 열기"
      >
        {children}
      </button>
      
      {isOpen && (
        <div className={styles.dropdownMenu}>
          {items.map((item, index) => (
            <button
              key={index}
              className={`${styles.dropdownItem} ${item.className || ''}`}
              onClick={() => handleItemClick(item.onClick)}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
