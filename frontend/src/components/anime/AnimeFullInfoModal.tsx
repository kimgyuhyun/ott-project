"use client";
import { useEffect, useMemo, useState } from "react";
import Modal from "@/components/ui/Modal";
import { getAnimeDetail } from "@/lib/api/anime";
import styles from "./AnimeFullInfoModal.module.css";

interface AnimeFullInfoModalProps {
  isOpen: boolean;
  onClose: () => void;
  detail: any;
}

function parseVoiceActors(voiceActorsRaw: any): string[] {
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
  const [full, setFull] = useState<any>(detail);
  const [loading, setLoading] = useState<boolean>(false);

  // 모달 열릴 때, 상세 필드가 비어 있으면 재조회
  useEffect(() => {
    if (!isOpen) return;
    const id = full?.aniId ?? full?.id;
    const needs = !Array.isArray(full?.studios) || !Array.isArray(full?.genres) || !Array.isArray(full?.episodes) || !Array.isArray(full?.tags) || !full?.fullSynopsis;
    if (id && needs) {
      setLoading(true);
      getAnimeDetail(Number(id))
        .then((d) => setFull((prev: any) => ({ ...prev, ...(d as any) })))
        .finally(() => setLoading(false));
    }
  }, [isOpen, full?.aniId, full?.id]);

  const synopsis = useMemo(() => (full?.fullSynopsis ?? full?.synopsis ?? "").toString().trim(), [full]);
  const tags: string[] = useMemo(() => (Array.isArray(full?.tags) ? full.tags : []), [full]);
  const studios: any[] = useMemo(() => (Array.isArray(full?.studios) ? full.studios : []), [full]);
  const genres: any[] = useMemo(() => (Array.isArray(full?.genres) ? full.genres : []), [full]);
  const voiceList = useMemo(() => parseVoiceActors(full?.voiceActors), [full?.voiceActors]);

  return (
    <Modal open={isOpen} onClose={onClose}>
      <div className={styles.container}>
        <div className={styles.headerRow}>
          <h2 className={styles.title}>작품 정보</h2>
          <button className={styles.closeBtn} aria-label="닫기" onClick={onClose}>×</button>
        </div>

        {/* 줄거리 */}
        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>줄거리</h3>
          <p className={styles.paragraph}>{loading ? '불러오는 중…' : (synopsis || "시놉시스 정보가 없습니다.")}</p>
        </section>

        {/* 태그 */}
        {tags.length > 0 && (
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>태그</h3>
            <div className={styles.tagsRow}>
              {tags.map((tag, idx) => (
                <span key={idx} className={styles.tagItem}>#{String(tag)}</span>
              ))}
            </div>
          </section>
        )}

        {/* 성우 정보 */}
        {voiceList.length > 0 && (
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>성우 정보</h3>
            <ul className={styles.voiceList}>
              {voiceList.map((name, i) => (
                <li key={i} className={styles.voiceItem}>{name}</li>
              ))}
            </ul>
          </section>
        )}

        {/* 제작 정보 */}
        {(studios.length > 0 || full?.director || full?.year || full?.releaseQuarter) && (
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>제작 정보</h3>
            <dl className={styles.dl}>
              {studios.length > 0 && (
                <div className={styles.dlRow}>
                  <dt className={styles.dt}>제작</dt>
                  <dd className={styles.dd}>{studios.map(s => s?.name ?? s).join(", ")}</dd>
                </div>
              )}
              {full?.director && (
                <div className={styles.dlRow}>
                  <dt className={styles.dt}>감독</dt>
                  <dd className={styles.dd}>{full.director}</dd>
                </div>
              )}
              {(full?.year || full?.releaseQuarter) && (
                <div className={styles.dlRow}>
                  <dt className={styles.dt}>출시</dt>
                  <dd className={styles.dd}>{full.releaseQuarter ?? (full.year ? `${full.year}년` : "")}</dd>
                </div>
              )}
            </dl>
          </section>
        )}
      </div>
    </Modal>
  );
}


