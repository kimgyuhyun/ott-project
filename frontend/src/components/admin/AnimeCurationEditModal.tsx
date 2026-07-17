"use client";
import { useEffect, useState } from "react";
import { getAnimeForCuration, updateAnimeCuration } from "@/lib/api/admin";
import type { AdminAnimeDetail, AnimeCurationUpdateRequest } from "@/lib/api/admin";
import styles from "@/app/admin/admin.module.css";

interface Props {
  animeId: number;
  onClose: () => void;
  onSaved: () => void;
}

/** 폼 상태 — 문자열은 빈 값을 "그대로 둠"으로 다룬다(아래 buildRequest 참고). */
type FormState = {
  title: string;
  titleEn: string;
  titleJp: string;
  synopsis: string;
  fullSynopsis: string;
  posterUrl: string;
  backdropUrl: string;
  isActive: boolean;
  isExclusive: boolean;
  isPopular: boolean;
  isNew: boolean;
  isCompleted: boolean;
  isSubtitle: boolean;
  isDub: boolean;
  isSimulcast: boolean;
};

const toForm = (a: AdminAnimeDetail): FormState => ({
  title: a.title ?? "",
  titleEn: a.titleEn ?? "",
  titleJp: a.titleJp ?? "",
  synopsis: a.synopsis ?? "",
  fullSynopsis: a.fullSynopsis ?? "",
  posterUrl: a.posterUrl ?? "",
  backdropUrl: a.backdropUrl ?? "",
  isActive: a.isActive,
  isExclusive: a.isExclusive,
  isPopular: a.isPopular,
  isNew: a.isNew,
  isCompleted: a.isCompleted,
  isSubtitle: a.isSubtitle,
  isDub: a.isDub,
  isSimulcast: a.isSimulcast,
});

/** 보강(AnimeEnhancementService)이 덮어쓰는 필드. 이걸 바꾸면 백엔드가 curated 를 켠다. */
const CONTENT_KEYS = ["title", "titleEn", "titleJp", "synopsis", "fullSynopsis", "posterUrl", "backdropUrl"] as const;

/**
 * 애니 단건 큐레이션 수정 모달
 *
 * 목록 항목이 아니라 상세를 따로 받아 채우는 이유
 * - 줄거리(특히 fullSynopsis)는 목록 응답에 없다. TEXT 라 20건에 실으면 응답이 본문 덩어리가 된다.
 *
 * 백엔드 계약(그대로 따라야 하는 것)
 * - 부분 수정이다. 전달하지 않은 필드는 변경되지 않으므로, 실제로 바꾼 값만 보낸다.
 * - null 로 되돌리는 수단이 없다(null = 변경 없음). 비운 채 저장하면 그 필드는 보내지 않는다(원래 값 유지).
 * - 콘텐츠(제목/줄거리/이미지)가 실제로 바뀌면 curated 가 켜지고, 이후 TMDB 보강이 이 작품을 건너뛴다.
 *   그래서 보강이 채우던 줄거리/배경이미지도 이 화면에서 관리할 수 있어야 한다(안 그러면 영영 빈 채로 남는다).
 */
export default function AnimeCurationEditModal({ animeId, onClose, onSaved }: Props) {
  const [original, setOriginal] = useState<AdminAnimeDetail | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const detail = await getAnimeForCuration(animeId);
        if (!alive) return;
        setOriginal(detail);
        setForm(toForm(detail));
      } catch (e) {
        if (alive) setError(e instanceof Error ? e.message : "불러오지 못했습니다.");
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; }; // 응답 도착 전에 닫으면 setState 하지 않는다
  }, [animeId]);

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));

  /**
   * 바뀐 값만 담은 요청을 만든다.
   * 문자열이 비어 있으면 보내지 않는다 — 빈 문자열은 null 이 아니라서, 백엔드가 "빈 값으로 바꿔달라"로 읽는다.
   */
  const buildRequest = (): AnimeCurationUpdateRequest => {
    if (!form || !original) return {};
    const request: AnimeCurationUpdateRequest = {};

    CONTENT_KEYS.forEach((key) => {
      const next = form[key].trim();
      if (next && next !== (original[key] ?? "")) request[key] = next;
    });

    const booleanKeys = ["isActive", "isExclusive", "isPopular", "isNew",
      "isCompleted", "isSubtitle", "isDub", "isSimulcast"] as const;
    booleanKeys.forEach((key) => {
      if (form[key] !== original[key]) request[key] = form[key];
    });

    return request;
  };

  const willMarkCurated = () =>
    !!original && !original.curated && CONTENT_KEYS.some((key) => buildRequest()[key] !== undefined);

  const handleSave = async () => {
    const request = buildRequest();
    if (Object.keys(request).length === 0) {
      setError("변경된 내용이 없습니다.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateAnimeCuration(animeId, request);
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const textField = (label: string, key: typeof CONTENT_KEYS[number], hint?: string) => (
    <div className={styles.modalField}>
      <label className={styles.filterLabel}>{label}</label>
      <input
        className={styles.input}
        value={form![key]}
        onChange={(e) => setField(key, e.target.value)}
        disabled={saving}
      />
      {hint && <span style={{ color: "#6f7681", fontSize: 11 }}>{hint}</span>}
    </div>
  );

  const checkbox = (label: string, key: keyof FormState, hint?: string) => (
    <label className={styles.checkLabel} title={hint}>
      <input
        type="checkbox"
        checked={form![key] as boolean}
        onChange={(e) => setField(key, e.target.checked as FormState[typeof key])}
        disabled={saving}
      />
      {label}
    </label>
  );

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      {/* 모달 내부 클릭이 오버레이까지 올라가 창을 닫아버리지 않게 한다 */}
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>큐레이션 수정 (ID: {animeId})</h3>

        {loading ? (
          <p className={styles.modalHint}>불러오는 중...</p>
        ) : !form || !original ? (
          <>
            <div className={`${styles.result} ${styles.resultErr}`}>{error ?? "불러오지 못했습니다."}</div>
            <div className={styles.modalActions}>
              <button className={styles.pagerBtn} onClick={onClose}>닫기</button>
            </div>
          </>
        ) : (
          <>
            <p className={styles.modalHint}>
              전달하지 않은 항목은 그대로 둡니다. 값을 비워도 지워지지 않습니다(무시).
              {original.curated && (
                <>
                  <br />
                  이 작품은 <strong>큐레이션 표시</strong> 상태라 TMDB 자동 보강 대상이 아닙니다 — 줄거리·이미지는 여기서만 채워집니다.
                </>
              )}
              {willMarkCurated() && (
                <>
                  <br />
                  콘텐츠를 바꾸면 <strong>큐레이션 표시</strong>가 켜져, 이후 TMDB 자동 보강이 이 작품을 건너뜁니다.
                </>
              )}
            </p>

            {textField("한국어 제목", "title")}
            {textField("영어 제목", "titleEn")}
            {textField("일본어 제목", "titleJp")}
            {textField("포스터 URL", "posterUrl")}
            {textField("배경 이미지 URL", "backdropUrl")}

            <div className={styles.modalField}>
              <label className={styles.filterLabel}>줄거리 (목록용, 500자)</label>
              <textarea
                className={styles.input}
                style={{ minHeight: 70, resize: "vertical", fontFamily: "inherit" }}
                value={form.synopsis}
                onChange={(e) => setField("synopsis", e.target.value)}
                disabled={saving}
              />
            </div>

            <div className={styles.modalField}>
              <label className={styles.filterLabel}>전체 줄거리 (상세용)</label>
              <textarea
                className={styles.input}
                style={{ minHeight: 110, resize: "vertical", fontFamily: "inherit" }}
                value={form.fullSynopsis}
                onChange={(e) => setField("fullSynopsis", e.target.value)}
                disabled={saving}
              />
            </div>

            <label className={styles.filterLabel} style={{ display: "block", marginTop: 6 }}>배지 / 노출</label>
            <div className={styles.checkGrid}>
              {checkbox("사용자에게 노출", "isActive", "끄면 사용자 목록·검색에서 사라집니다")}
              {checkbox("독점", "isExclusive")}
              {checkbox("인기", "isPopular")}
              {checkbox("신작", "isNew")}
              {checkbox("완결", "isCompleted")}
              {checkbox("자막", "isSubtitle", "수집 시 항상 켜진 값이라 실제와 다를 수 있습니다")}
              {checkbox("더빙", "isDub", "수집 시 평점으로 추측된 값이라 실제와 다를 수 있습니다")}
              {checkbox("동시방영", "isSimulcast")}
            </div>

            {error && <div className={`${styles.result} ${styles.resultErr}`}>{error}</div>}

            <div className={styles.modalActions}>
              <button className={styles.pagerBtn} onClick={onClose} disabled={saving}>취소</button>
              <button className={styles.button} onClick={handleSave} disabled={saving}>
                {saving ? "저장 중..." : "저장"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
