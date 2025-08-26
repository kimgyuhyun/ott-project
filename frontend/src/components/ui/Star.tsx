"use client";
import React, { useId } from "react";

interface StarProps {
  value: number; // 0.0 ~ 1.0 채움 비율
  size?: number; // px
  color?: string;
  emptyColor?: string;
  className?: string;
  title?: string;
}

export default function Star({ value, size = 28, color = "#A855F7", emptyColor = "#D1D5DB", className, title }: StarProps) {
  const id = useId();
  const clamped = Math.max(0, Math.min(1, value || 0));
  const gradId = `grad-${id}`;
  const pathId = `path-${id}`;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden={title ? undefined : true}
      role={title ? "img" : "presentation"}
      className={className}
    >
      {title && <title>{title}</title>}
      <defs>
        <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset={`${clamped * 100}%`} stopColor={color} />
          <stop offset={`${clamped * 100}%`} stopColor={emptyColor} />
        </linearGradient>
        <path id={pathId} d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
      </defs>
      <use href={`#${pathId}`} fill={emptyColor} />
      <use href={`#${pathId}`} fill={`url(#${gradId})`} />
    </svg>
  );
}


