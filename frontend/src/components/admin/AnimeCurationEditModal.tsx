"use client";
import { useState } from "react";
import { updateAnimeCuration } from "@/lib/api/admin";
import type { AdminAnimeItem, AnimeCurationUpdateRequest } from "@/lib/api/admin";
import styles from "@/app/admin/admin.module.css";

interface Props {
  anime: AdminAnimeItem;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * 애니 단건 큐레이션 수정 모달
 *
 * 백엔드 계약(그대로 따라야 하는 것)
 * - 부분 수정이다. 전달하지 않은 필드는 변경되지 않으므로, 실제로 바꾼 값만 보낸다.
 * - null 로 되돌리는 수단이 없다(null = 변경 없음). 그래서 제목/포스터를 비우는 건 지원하지 않고,
 *   비운 채 저장하면 그 필드는 보내지 않는다(원래 값 유지).
 * - 제목/포스터가 실제로 바뀌면 백엔드가 curated 를 켜고, 이후 TMDB 자동 보강이 이 작품을 건너뛴다.
 *   배지/노출만 바꾸면 켜지지 않는다.
 */
export default function AnimeCurationEditModal({ anime, onClose, onSaved }: Props) {
  const [title, setTitle] = useState(anime.title ?? "");
  const [titleEn, setTitleEn] = useState(anime.titleEn ?? "");
  const [titleJp, setTitleJp] = useState(anime.titleJp ?? "");
  const [posterUrl, setPosterUrl] = useState(anime.posterUrl ?? "");
  const [isActive, setIsActive] = useState(anime.isActive);
  const [isExclusive, setIsExclusive] = useState(anime.isExclusive);
  const [isPopular, setIsPopular] = useState(anime.isPopular);
  const [isNew, setIsNew] = useState(anime.isNew);

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 바뀐 값만 담은 요청을 만든다.
   * 문자열은 비어 있으면 보내지 않는다 — 빈 문자열을 보내면 백엔드가 "빈 값으로 바꿔달라"로 읽는다.
   */
  const buildRequest = (): AnimeCurationUpdateRequest => {
    const request: AnimeCurationUpdateRequest = {};
    const putIfChanged = (
      key: "title" | "titleEn" | "titleJp" | "posterUrl",
      value: string,
      original: string | null,
    ) => {
      const trimmed = value.trim();
      if (trimmed && trimmed !== (original ?? "")) request[key] = trimmed;
    };

    putIfChanged("title", title, anime.title);
    putIfChanged("titleEn", titleEn, anime.titleEn);
    putIfChanged("titleJp", titleJp, anime.titleJp);
    putIfChanged("posterUrl", posterUrl, anime.posterUrl);

    if (isActive !== anime.isActive) request.isActive = isActive;
    if (isExclusive !== anime.isExclusive) request.isExclusive = isExclusive;
    if (isPopular !== anime.isPopular) request.isPopular = isPopular;
    if (isNew !== anime.isNew) request.isNew = isNew;

    return request;
  };

  const contentWillChange = () => {
    const request = buildRequest();
    return (
      request.title !== undefined ||
      request.titleEn !== undefined ||
      request.titleJp !== undefined ||
      request.posterUrl !== undefined
    );
  };

  const handleSave = async () => {
    const request = buildRequest();
    if (Object.keys(request).length === 0) {
      setError("변경된 내용이 없습니다.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateAnimeCuration(anime.id, request);
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      {/* 모달 내부 클릭이 오버레이까지 올라가 창을 닫아버리지 않게 한다 */}
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>큐레이션 수정 (ID: {anime.id})</h3>
        <p className={styles.modalHint}>
          전달하지 않은 항목은 그대로 둡니다. 제목/포스터는 이 화면에서 비울 수 없습니다(비우면 무시).
          {!anime.curated && contentWillChange() && (
            <>
              <br />
              제목·포스터를 바꾸면 <strong>큐레이션 표시</strong>가 켜져, 이후 TMDB 자동 보강이 이 작품을 건너뜁니다.
            </>
          )}
        </p>

        <div className={styles.modalField}>
          <label className={styles.filterLabel}>한국어 제목</label>
          <input className={styles.input} value={title} onChange={(e) => setTitle(e.target.value)} disabled={saving} />
        </div>
        <div className={styles.modalField}>
          <label className={styles.filterLabel}>영어 제목</label>
          <input className={styles.input} value={titleEn} onChange={(e) => setTitleEn(e.target.value)} disabled={saving} />
        </div>
        <div className={styles.modalField}>
          <label className={styles.filterLabel}>일본어 제목</label>
          <input className={styles.input} value={titleJp} onChange={(e) => setTitleJp(e.target.value)} disabled={saving} />
        </div>
        <div className={styles.modalField}>
          <label className={styles.filterLabel}>포스터 URL</label>
          <input className={styles.input} value={posterUrl} onChange={(e) => setPosterUrl(e.target.value)} disabled={saving} />
        </div>

        <div className={styles.checkGrid}>
          <label className={styles.checkLabel}>
            <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} disabled={saving} />
            사용자에게 노출
          </label>
          <label className={styles.checkLabel}>
            <input type="checkbox" checked={isExclusive} onChange={(e) => setIsExclusive(e.target.checked)} disabled={saving} />
            독점
          </label>
          <label className={styles.checkLabel}>
            <input type="checkbox" checked={isPopular} onChange={(e) => setIsPopular(e.target.checked)} disabled={saving} />
            인기
          </label>
          <label className={styles.checkLabel}>
            <input type="checkbox" checked={isNew} onChange={(e) => setIsNew(e.target.checked)} disabled={saving} />
            신작
          </label>
        </div>

        {error && <div className={`${styles.result} ${styles.resultErr}`}>{error}</div>}

        <div className={styles.modalActions}>
          <button className={styles.pagerBtn} onClick={onClose} disabled={saving}>취소</button>
          <button className={styles.button} onClick={handleSave} disabled={saving}>
            {saving ? "저장 중..." : "저장"}
          </button>
        </div>
      </div>
    </div>
  );
}
