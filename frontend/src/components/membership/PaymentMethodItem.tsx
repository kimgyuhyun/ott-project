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
      <div className="flex items-center">
        <div className={`w-8 h-8 ${colorClass} rounded-full flex items-center justify-center mr-3`}>
          <span className={`text-sm font-bold ${colorClass.includes('yellow') ? 'text-black' : 'text-white'}`}>
            {id === 'kakao' ? 'K' : id === 'toss' ? 'T' : 'N'}
          </span>
        </div>
        <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{label}</span>
      </div>
    </button>
  );
}


