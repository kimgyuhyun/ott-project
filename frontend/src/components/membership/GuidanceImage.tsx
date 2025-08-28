"use client";
import { useEffect, useMemo, useState } from "react";

interface GuidanceImageProps {
  title: string;
  items: string[];
  width?: number; // px
}

export default function GuidanceImage({ title, items, width = 900 }: GuidanceImageProps) {
  const [src, setSrc] = useState<string>("");

  const lines = useMemo(() => {
    // 서버 렌더링 시에는 계산하지 않음
    if (typeof window === "undefined") return [] as string[];
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (!ctx) return [] as string[];

    const padding = 28;
    const bullet = "• ";
    const contentWidth = width - padding * 2;
    const out: string[] = [];

    ctx.font = "bold 22px system-ui, -apple-system, Segoe UI, Roboto, sans-serif";
    out.push(title);
    ctx.font = "16px system-ui, -apple-system, Segoe UI, Roboto, sans-serif";

    const wrap = (text: string) => {
      const words = text.split(/\s+/);
      let line = bullet;
      for (const w of words) {
        const test = line + (line === bullet ? "" : " ") + w;
        const m = ctx.measureText(test);
        if (m.width > contentWidth) {
          out.push(line);
          line = "  " + w; // 이월 라인은 들여쓰기
        } else {
          line = test;
        }
      }
      if (line.trim().length > 0) out.push(line);
    };

    items.forEach(wrap);
    return out;
  }, [title, items, width]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const padding = 28;
    const lineHeight = 24;
    const titleGap = 12;

    // 측정: 총 높이
    const titleLines = 1;
    const contentLines = Math.max(0, lines.length - titleLines);
    const height = padding + 22 + titleGap + contentLines * lineHeight + padding;

    canvas.width = width;
    canvas.height = height;

    // 배경
    ctx.fillStyle = "var(--background-1, #121212)";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // 테두리
    ctx.strokeStyle = "var(--border-1, #323232)";
    ctx.lineWidth = 1;
    ctx.strokeRect(0.5, 0.5, canvas.width - 1, canvas.height - 1);

    // 제목
    ctx.fillStyle = "var(--foreground-1, #F7F7F7)";
    ctx.font = "bold 22px system-ui, -apple-system, Segoe UI, Roboto, sans-serif";
    ctx.fillText(lines[0] || title, padding, padding + 20);

    // 본문
    ctx.font = "16px system-ui, -apple-system, Segoe UI, Roboto, sans-serif";
    ctx.fillStyle = "var(--foreground-3, #ABABAB)";
    let y = padding + 20 + titleGap + 16;
    for (let i = 1; i < lines.length; i++) {
      ctx.fillText(lines[i], padding, y);
      y += lineHeight;
    }

    setSrc(canvas.toDataURL("image/png"));
  }, [lines]);

  if (!src) return <div style={{ 
    height: 200, 
    backgroundColor: 'var(--background-1, #121212)', 
    border: '1px solid var(--border-1, #323232)' 
  }} />;
  return (
    <img src={src} alt={title} style={{ 
      width: '100%', 
      border: '1px solid var(--border-1, #323232)', 
      borderRadius: 8 
    }} />
  );
}


