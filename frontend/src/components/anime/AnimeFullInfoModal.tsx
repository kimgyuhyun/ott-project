"use client";
import { useEffect, useMemo, useState } from "react";
import Modal from "@/components/ui/Modal";
import { getAnimeDetail } from "@/lib/api/anime";
import { Anime } from "@/types/common";
import styles from "./AnimeFullInfoModal.module.css";

interface AnimeFullInfoModalProps {
  isOpen: boolean;
  onClose: () => void;
  detail: Anime;
}

function parseVoiceActors(voiceActorsRaw: unknown): string[] {
  if (!voiceActorsRaw) return [];
  try {
    // JSON 문자열 형태라면 배열/객체에서 이름 필드를 추출
    const parsed = typeof voiceActorsRaw === 'string' ? JSON.parse(voiceActorsRaw) : voiceActorsRaw;
    if (Array.isArray(parsed)) {
      return parsed.map((v) => (typeof v === 'string' ? v : (v?.name ?? ''))).filter(Boolean);
    }
    if (parsed && typeof parsed === 'object') {
      return Object.values(parsed as Record<string, any>).map((v) => (typeof v === 'string' ? v : (v?.name ?? ''))).filter(Boolean);
    }
  } catch (_) {
    // JSON 파싱 실패 시 줄바꿈/쉼표 기준 단순 분리
  }
  const text = String(voiceActorsRaw);
  return text.split(/\r?\n|,|\/|;|\|/).map((s) => s.trim()).filter(Boolean);
}

export default function AnimeFullInfoModal({ isOpen, onClose, detail }: AnimeFullInfoModalProps) {
  type ExtendedAnime = Anime & {
    aniId?: number | string;
    episodes?: unknown[];
    fullSynopsis?: string;
    synopsis?: string;
    tags?: string[] | string;
    studios?: Array<string | { name?: string }>;
    genres?: Array<string | { name?: string }>;
    voiceActors?: unknown;
    director?: string;
    year?: number | string;
    releaseQuarter?: string;
  };
  const [full, setFull] = useState<ExtendedAnime>(detail as ExtendedAnime);
  const [loading, setLoading] = useState<boolean>(false);

  // 모달 열릴 때, 상세 필드가 비어 있으면 재조회
  useEffect(() => {
    if (!isOpen) return;
    const id = (full as any)?.aniId ?? (full as any)?.id;
    const needs = !Array.isArray((full as any)?.studios) || !Array.isArray((full as any)?.genres) || !Array.isArray((full as any)?.episodes) || !Array.isArray((full as any)?.tags) || !(full as any)?.fullSynopsis;
    if (id && needs) {
      setLoading(true);
      getAnimeDetail(Number(id))
        .then((d) => setFull((prev: ExtendedAnime) => ({ ...prev, ...(d as Partial<ExtendedAnime>) })))
        .finally(() => setLoading(false));
    }
  }, [isOpen, (full as any)?.aniId, (full as any)?.id]);

  const synopsis = useMemo(() => (((full as any)?.fullSynopsis ?? (full as any)?.synopsis ?? "")).toString().trim(), [full]);
  const tags: string[] = useMemo(() => {
    if (Array.isArray((full as any)?.tags)) return (full as any).tags as string[];
    if (typeof (full as any)?.tags === 'string') {
      try {
        return JSON.parse((full as any).tags as string);
      } catch {
        return ((full as any).tags as string).split(',').map((t: string) => t.trim()).filter(Boolean);
      }
    }
    return [];
  }, [full]);
  const studios: Array<string | { name?: string }> = useMemo(() => (Array.isArray((full as any)?.studios) ? (full as any).studios : []), [full]);
  const genres: Array<string | { name?: string }> = useMemo(() => (Array.isArray((full as any)?.genres) ? (full as any).genres : []), [full]);
  const voiceList = useMemo(() => {
    const voices = parseVoiceActors((full as any)?.voiceActors);
    return voices.slice(0, 3); // 상위 3개만 표시
  }, [(full as any)?.voiceActors]);

  // 디버깅용 콘솔 로그
  console.log('🔍 AnimeFullInfoModal - full 객체:', full);
  console.log('🔍 synopsis:', synopsis);
  console.log('🔍 tags:', tags);
  console.log('🔍 voiceList:', voiceList);
  console.log('🔍 director:', (full as any)?.director);
  console.log('🔍 studios:', studios);

  if (!isOpen) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.modalWrapper}>
        <div className={styles.headerRow}>
          <span className={styles.title}>작품 정보</span>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={styles.closeIcon} onClick={onClose}>
            <path d="M6.052 4.352a1.202 1.202 0 1 0-1.7 1.7L10.3 12l-5.948 5.948a1.202 1.202 0 0 0 1.7 1.7L12 13.7l5.948 5.948a1.202 1.202 0 0 0 1.7-1.7L13.7 12l5.948-5.948a1.202 1.202 0 0 0-1.7-1.7L12 10.3 6.052 4.352Z" fill="currentColor"/>
          </svg>
        </div>
        
        <div className={styles.modalContainer}>
          <div className={styles.content}>
          {/* 줄거리 */}
          <section className={styles.section}>
            <span className={styles.sectionTitle}>줄거리</span>
            <span className={styles.paragraph}>{loading ? '불러오는 중…' : (synopsis || "줄거리 정보가 없습니다.")}</span>
          </section>

          {/* 태그 */}
          {tags.length > 0 && (
            <section className={styles.section}>
              <span className={styles.sectionTitle}>태그</span>
              <div className={styles.tagsRow}>
                {tags.map((tag, idx) => (
                  <li key={idx} className={styles.tagItem}>
                    <a className={styles.tagLink} href={`/tag/${String(tag)}`}>#{String(tag)}</a>
                  </li>
                ))}
              </div>
            </section>
          )}

          {/* 성우 정보 */}
          {voiceList.length > 0 && (
            <section className={styles.section}>
              <span className={styles.sectionTitle}>성우 정보</span>
              <section className={styles.voiceSection}>
                <div className={styles.voiceRow}>
                  {voiceList.map((name, i) => (
                    <div key={i} className={styles.voiceItem}>
                      <span className={styles.voiceRole}>{name} 역</span>
                      <span className={styles.voiceName}>{name}</span>
                    </div>
                  ))}
                </div>
              </section>
            </section>
          )}

          {/* 제작 정보 */}
          {(studios.length > 0 || (full as any)?.director || (full as any)?.year || (full as any)?.releaseQuarter) && (
            <section className={styles.section}>
              <span className={styles.sectionTitle}>제작 정보</span>
              <section className={styles.productionSection}>
                <div className={styles.productionRow}>
                  {studios.length > 0 && (
                    <div className={styles.productionItem}>
                      <span className={styles.productionLabel}>제작</span>
                      <span className={styles.productionValue}>{studios.map((s: any) => s?.name ?? s).join(", ")}</span>
                    </div>
                  )}
                  {(full as any)?.director && (
                    <div className={styles.productionItem}>
                      <span className={styles.productionLabel}>감독</span>
                      <span className={styles.productionValue}>{(full as any).director}</span>
                    </div>
                  )}
                  {((full as any)?.year || (full as any)?.releaseQuarter) && (
                    <div className={styles.productionItem}>
                      <span className={styles.productionLabel}>출시</span>
                      <span className={styles.productionValue}>{(full as any).releaseQuarter ?? ((full as any).year ? `${(full as any).year}년` : "")}</span>
                    </div>
                  )}
                </div>
              </section>
            </section>
          )}
          </div>
        </div>
      </div>
    </div>
  );
}


