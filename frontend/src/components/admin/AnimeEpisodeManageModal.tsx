"use client";
import { useEffect, useState } from "react";
import { listEpisodesForAdmin, updateEpisodeForAdmin } from "@/lib/api/admin";
import type { AdminEpisode, EpisodeUpdateRequest } from "@/lib/api/admin";
import styles from "@/app/admin/admin.module.css";

interface Props {
  animeId: number;
  animeTitle: string;
  onClose: () => void;
}

interface FormState {
  title: string;
  thumbnailUrl: string;
  videoUrl: string;
  isActive: boolean;
  isReleased: boolean;
}

const TEXT_KEYS = ["title", "thumbnailUrl", "videoUrl"] as const;
const BOOLEAN_KEYS = ["isActive", "isReleased"] as const;

function toForm(episode: AdminEpisode): FormState {
  return {
    title: episode.title ?? "",
    thumbnailUrl: episode.thumbnailUrl ?? "",
    videoUrl: episode.videoUrl ?? "",
    isActive: episode.isActive,
    isReleased: episode.isReleased,
  };
}

/**
 * 작품별 에피소드 관리 모달
 *
 * 큐레이션 수정 모달과 나눈 이유
 * - 그쪽은 작품(Anime) 단위 필드만 다룬다. 에피소드는 다른 테이블이고 화수마다 행이 따로 있어서,
 *   같은 폼에 넣으면 "무엇을 저장하는지"가 흐려진다.
 *
 * 저장 단위
 * - 화수 하나씩 저장한다. 한 번에 여러 화수를 저장하면 중간에 실패했을 때 어디까지 반영됐는지 알 수 없다.
 * - 바뀐 필드만 보낸다. 백엔드가 부분 수정이라 보내지 않은 값은 그대로 둔다.
 * - 빈 문자열은 보내지 않는다 — not-null 컬럼이라 백엔드가 400 으로 거절한다.
 *   (지우려는 게 아니라 실수로 비운 경우가 대부분이라, 화면에서 먼저 막는다)
 *
 * episodeNumber 는 편집 대상이 아니다. 시청 기록·진행률·댓글이 에피소드에 붙어 있어 화수를 바꾸면 어긋난다.
 */
export default function AnimeEpisodeManageModal({ animeId, animeTitle, onClose }: Props) {
  const [episodes, setEpisodes] = useState<AdminEpisode[] | null>(null);
  const [forms, setForms] = useState<Record<number, FormState>>({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [savedId, setSavedId] = useState<number | null>(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const list = await listEpisodesForAdmin(animeId);
        if (!alive) return;
        setEpisodes(list);
        setForms(Object.fromEntries(list.map((e) => [e.id, toForm(e)])));
      } catch (e) {
        if (alive) setError(e instanceof Error ? e.message : "불러오지 못했습니다.");
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; }; // 응답 도착 전에 닫으면 setState 하지 않는다
  }, [animeId]);

  const setField = <K extends keyof FormState>(episodeId: number, key: K, value: FormState[K]) =>
    setForms((prev) => ({ ...prev, [episodeId]: { ...prev[episodeId], [key]: value } }));

  /** 바뀐 값만 담는다. 빈 문자열은 백엔드가 400 이므로 아예 넣지 않는다. */
  const buildRequest = (episode: AdminEpisode): EpisodeUpdateRequest => {
    const form = forms[episode.id];
    const request: EpisodeUpdateRequest = {};
    if (!form) return request;

    TEXT_KEYS.forEach((key) => {
      const next = form[key].trim();
      if (next && next !== (episode[key] ?? "")) request[key] = next;
    });
    BOOLEAN_KEYS.forEach((key) => {
      if (form[key] !== episode[key]) request[key] = form[key];
    });

    return request;
  };

  const handleSave = async (episode: AdminEpisode) => {
    const request = buildRequest(episode);
    if (Object.keys(request).length === 0) {
      setError(`${episode.episodeNumber}화: 변경된 내용이 없습니다.`);
      return;
    }
    setSavingId(episode.id);
    setError(null);
    setSavedId(null);
    try {
      const updated = await updateEpisodeForAdmin(animeId, episode.id, request);
      // 저장된 값을 기준값으로 갱신해야 다음 "변경된 내용" 판정이 맞는다.
      setEpisodes((prev) => prev?.map((e) => (e.id === updated.id ? updated : e)) ?? prev);
      setForms((prev) => ({ ...prev, [updated.id]: toForm(updated) }));
      setSavedId(updated.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSavingId(null);
    }
  };

  const textField = (episode: AdminEpisode, label: string, key: typeof TEXT_KEYS[number]) => (
    <div className={styles.modalField}>
      <label className={styles.filterLabel}>{label}</label>
      <input
        className={styles.input}
        value={forms[episode.id]?.[key] ?? ""}
        onChange={(e) => setField(episode.id, key, e.target.value)}
        disabled={savingId === episode.id}
      />
    </div>
  );

  const checkbox = (episode: AdminEpisode, label: string, key: typeof BOOLEAN_KEYS[number], hint?: string) => (
    <label className={styles.checkLabel} title={hint}>
      <input
        type="checkbox"
        checked={forms[episode.id]?.[key] ?? false}
        onChange={(e) => setField(episode.id, key, e.target.checked)}
        disabled={savingId === episode.id}
      />
      {label}
    </label>
  );

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      {/* 모달 내부 클릭이 오버레이까지 올라가 창을 닫아버리지 않게 한다 */}
      <div className={styles.modal} onClick={(e) => e.stopPropagation()} style={{ maxWidth: 720 }}>
        <h3 className={styles.modalTitle}>에피소드 관리 — {animeTitle || `ID ${animeId}`}</h3>

        {loading ? (
          <p className={styles.modalHint}>불러오는 중...</p>
        ) : !episodes ? (
          <>
            <div className={`${styles.result} ${styles.resultErr}`}>{error ?? "불러오지 못했습니다."}</div>
            <div className={styles.modalActions}>
              <button className={styles.pagerBtn} onClick={onClose}>닫기</button>
            </div>
          </>
        ) : (
          <>
            <p className={styles.modalHint}>
              화수마다 따로 저장합니다. 값을 비워도 지워지지 않습니다(무시).
              <br />
              화수 번호는 시청 기록·댓글이 붙어 있어 수정할 수 없습니다.
            </p>

            {error && <div className={`${styles.result} ${styles.resultErr}`}>{error}</div>}

            {episodes.length === 0 ? (
              <p className={styles.modalHint} style={{ padding: "18px 0" }}>
                등록된 에피소드가 없습니다.
              </p>
            ) : (
              episodes.map((episode) => (
                <div
                  key={episode.id}
                  style={{ borderTop: "1px solid #2c2f38", paddingTop: 14, marginTop: 14 }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                    <strong>{episode.episodeNumber}화</strong>
                    <span style={{ color: "#6f7681", fontSize: 11 }}>ID {episode.id}</span>
                    <span className={`${styles.badge} ${episode.isReleased ? styles.badgeOn : styles.badgeOff}`}>
                      {episode.isReleased ? "공개" : "비공개"}
                    </span>
                    {savedId === episode.id && (
                      <span style={{ color: "#4ade80", fontSize: 11 }}>저장됨</span>
                    )}
                  </div>

                  {textField(episode, "제목", "title")}
                  {textField(episode, "영상 URL", "videoUrl")}
                  {textField(episode, "썸네일 URL", "thumbnailUrl")}

                  <div className={styles.checkGrid} style={{ marginTop: 6 }}>
                    {checkbox(episode, "사용자에게 노출", "isActive", "끄면 목록에서 사라집니다")}
                    {checkbox(episode, "공개", "isReleased", "끄면 재생할 수 없습니다")}
                  </div>

                  <div className={styles.modalActions} style={{ marginTop: 10 }}>
                    <button
                      className={styles.button}
                      onClick={() => handleSave(episode)}
                      disabled={savingId !== null}
                    >
                      {savingId === episode.id ? "저장 중..." : `${episode.episodeNumber}화 저장`}
                    </button>
                  </div>
                </div>
              ))
            )}

            <div className={styles.modalActions} style={{ marginTop: 18 }}>
              <button className={styles.pagerBtn} onClick={onClose} disabled={savingId !== null}>
                닫기
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
