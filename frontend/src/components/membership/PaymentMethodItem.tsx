"use client";
import React from "react";

interface PaymentMethodItemProps {
  id: string; // 'kakao' | 'toss' | 'nice'
  label: string;
  colorClass: string; // e.g., 'bg-yellow-400'
  selected: boolean;
  onSelect: () => void;
}

export default function PaymentMethodItem({ id, label, colorClass, selected, onSelect }: PaymentMethodItemProps) {
  const [isHovered, setIsHovered] = React.useState(false);

  const backgroundColor = selected ? 'var(--background-highlight, rgba(129, 107, 255, 0.1))' : (isHovered ? 'var(--background-2, #000000)' : 'var(--background-1, #121212)');
  const borderColor = selected ? 'var(--foreground-slight, #816BFF)' : (isHovered ? 'var(--border-2, #505050)' : 'var(--border-1, #323232)');
  const boxShadow = selected ? '0 0 20px rgba(129, 107, 255, 0.2)' : 'none';

  return (
    <button
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={onSelect}
      className={`p-3 rounded-lg border-2 text-left transition-all duration-200`}
      style={{ backgroundColor, borderColor, boxShadow }}
    >
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <div className={`w-8 h-8 ${colorClass} rounded-full flex items-center justify-center mr-3`}>
          {id === 'kakao' && (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect width="24" height="24" rx="4" fill="#FEE500"/>
              <path d="M12 7c-3.59 0-6.5 1.94-6.5 4.33 0 1.58 1.15 2.95 2.9 3.74l-.46 2.76 3.05-2.07h1.01c3.59 0 6.5-1.94 6.5-4.33S15.59 7 12 7z" fill="#000000"/>
            </svg>
          )}
          {id === 'toss' && (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect width="24" height="24" rx="4" fill="#FFFFFF"/>
              <path d="M12 5c4.418 0 8 3.134 8 7-5.2-.1-8 3.8-12 0 0-3.866 3.134-7 8-7z" fill="#0064FF"/>
              <circle cx="10.4" cy="9.2" r="1.6" fill="#FFFFFF"/>
            </svg>
          )}
          {id === 'nice' && (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect width="24" height="24" rx="4" fill="#0A68F5"/>
              <text x="12" y="15" textAnchor="middle" fontSize="11" fill="#FFFFFF" fontWeight="bold">NP</text>
            </svg>
          )}
        </div>
        <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{label}</span>
      </div>
    </button>
  );
}


