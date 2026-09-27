"use client";
import { useEffect, useState } from "react";
import {
  getAnimeForCuration,
  getCurationConflict,
  updateAnimeCuration,
} from "@/lib/api/admin";
import type {
  AdminAnimeDetail,
  AnimeCurationUpdateRequest,
} from "@/lib/api/admin";
import { getErrorStatus } from "@/lib/errorMessage";
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

/** 충돌 화면에서 항목별로 고를 수 있는 필드와 표시 이름 */
const FIELD_LABELS: Record<keyof FormState, string> = {
  title: "한국어 제목",
  titleEn: "영어 제목",
  titleJp: "일본어 제목",
  synopsis: "줄거리",
  fullSynopsis: "전체 줄거리",
  posterUrl: "포스터 URL",
  backdropUrl: "배경 이미지 URL",
  isActive: "사용자에게 노출",
  isExclusive: "독점",
  isPopular: "인기",
  isNew: "신작",
  isCompleted: "완결",
  isSubtitle: "자막",
  isDub: "더빙",
  isSimulcast: "동시방영",
};

const CONFLICT_KEYS = Object.keys(FIELD_LABELS) as (keyof FormState)[];

/** 어느 쪽 값을 남길지. 기본은 서버 값이다 — 고르지 않고 저장해도 남의 수정이 사라지지 않는다. */
type Side = "mine" | "server";

const isContentKey = (
  key: keyof FormState,
): key is (typeof CONTENT_KEYS)[number] =>
  (CONTENT_KEYS as readonly string[]).includes(key);

/** 내 값과 서버 값이 다른 항목만 고르게 한다. 같은 항목은 고를 게 없다. */
function conflictingKeys(form: FormState, server: AdminAnimeDetail) {
  const serverForm = toForm(server);
  return CONFLICT_KEYS.filter((key) => {
    if (isContentKey(key)) {
      const mine = form[key].trim();
      return mine !== "" && mine !== serverForm[key]; // 비워둔 항목은 "변경 없음"이라 충돌이 아니다
    }
    return form[key] !== serverForm[key];
  });
}

function initialChoices(
  form: FormState,
  server: AdminAnimeDetail,
): Record<string, Side> {
  const choices: Record<string, Side> = {};
  conflictingKeys(form, server).forEach((key) => (choices[key] = "server"));
  return choices;
}

/** 값 하나를 화면에 보여줄 문자열로. 빈 값과 배지를 눈에 보이게 만든다. */
function display(value: string | boolean): string {
  if (typeof value === "boolean") return value ? "켬" : "끔";
  return value.trim() === "" ? "(비어 있음)" : value;
}

/** 보강(AnimeEnhancementService)이 덮어쓰는 필드. 이걸 바꾸면 백엔드가 curated 를 켠다. */
const CONTENT_KEYS = [
  "title",
  "titleEn",
  "titleJp",
  "synopsis",
  "fullSynopsis",
  "posterUrl",
  "backdropUrl",
] as const;

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
export default function AnimeCurationEditModal({
  animeId,
  onClose,
  onSaved,
}: Props) {
  const [original, setOriginal] = useState<AdminAnimeDetail | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 버전 충돌로 거절됐을 때의 서버 값과 항목별 선택. null 이면 충돌 없음.
  const [conflict, setConflict] = useState<{
    server: AdminAnimeDetail;
    choices: Record<string, Side>;
  } | null>(null);
  // 서버 값 없이 거절된 경우(커밋 시점 충돌). 최신 값 불러오기 버튼만 보여준다.
  const [needsReload, setNeedsReload] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const detail = await getAnimeForCuration(animeId);
        if (!alive) return;
        setOriginal(detail);
        setForm(toForm(detail));
      } catch (e) {
        if (alive)
          setError(e instanceof Error ? e.message : "불러오지 못했습니다.");
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    }; // 응답 도착 전에 닫으면 setState 하지 않는다
  }, [animeId]);

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));

  /**
   * 바뀐 값만 담은 요청을 만든다.
   * 문자열이 비어 있으면 보내지 않는다 — 빈 문자열은 null 이 아니라서, 백엔드가 "빈 값으로 바꿔달라"로 읽는다.
   */
  const buildRequest = (): Omit<AnimeCurationUpdateRequest, "version"> => {
    if (!form || !original) return {};
    const request: Omit<AnimeCurationUpdateRequest, "version"> = {};

    CONTENT_KEYS.forEach((key) => {
      const next = form[key].trim();
      if (next && next !== (original[key] ?? "")) request[key] = next;
    });

    const booleanKeys = [
      "isActive",
      "isExclusive",
      "isPopular",
      "isNew",
      "isCompleted",
      "isSubtitle",
      "isDub",
      "isSimulcast",
    ] as const;
    booleanKeys.forEach((key) => {
      if (form[key] !== original[key]) request[key] = form[key];
    });

    return request;
  };

  const willMarkCurated = () =>
    !!original &&
    !original.curated &&
    CONTENT_KEYS.some((key) => buildRequest()[key] !== undefined);

  const handleSave = async () => {
    const changes = buildRequest();
    if (!original || Object.keys(changes).length === 0) {
      setError("변경된 내용이 없습니다.");
      return;
    }
    setSaving(true);
    setError(null);
    setNeedsReload(false);
    try {
      // 폼을 채운 조회의 version 을 싣는다. 그 사이 다른 사람이 저장했으면 409 로 거절된다.
      const saved = await updateAnimeCuration(animeId, {
        ...changes,
        version: original.version,
      });
      // 저장 응답의 새 version 을 원본으로 삼는다 — 옛 version 으로 다시 저장하면 자기 자신과 충돌한다.
      setOriginal(saved);
      setForm(toForm(saved));
      onSaved();
    } catch (e) {
      const server = getCurationConflict(e);
      if (server) {
        // 서버 값을 받아 항목별로 고르게 한다. 입력하던 값은 form 에 그대로 두고 버리지 않는다.
        setConflict({ server, choices: initialChoices(form!, server) });
        setError(null);
      } else if (getErrorStatus(e) === 409) {
        // 커밋 시점 충돌이라 서버 값이 없다. 이 경우만 다시 불러오게 한다.
        setError(
          "다른 사람이 먼저 수정했습니다. 최신 값을 불러온 뒤 다시 시도해 주세요.",
        );
        setNeedsReload(true);
      } else {
        setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
      }
    } finally {
      setSaving(false);
    }
  };

  /** 충돌 화면에서 고른 값으로 다시 저장한다. 서버 값을 고른 항목은 요청에서 빠지므로 그대로 남는다. */
  const saveResolved = async () => {
    if (!conflict || !form) return;
    const server = conflict.server;
    const request: AnimeCurationUpdateRequest = { version: server.version };

    CONFLICT_KEYS.forEach((key) => {
      if (conflict.choices[key] !== "mine") return; // 서버 값 유지 = 안 보냄
      if (isContentKey(key)) {
        const mine = form[key].trim();
        if (mine) request[key] = mine; // 빈 문자열은 "지우기"가 아니라 "변경 없음"이라 보내지 않는다
      } else {
        request[key] = form[key];
      }
    });

    setSaving(true);
    setError(null);
    try {
      const saved = await updateAnimeCuration(animeId, request);
      setOriginal(saved);
      setForm(toForm(saved));
      setConflict(null);
      onSaved();
    } catch (e) {
      const server2 = getCurationConflict(e);
      if (server2) {
        // 고르는 사이에 또 바뀌었다. 새 서버 값으로 다시 고르게 한다.
        setConflict({
          server: server2,
          choices: initialChoices(form, server2),
        });
        setError("그 사이 또 바뀌었습니다. 다시 확인해 주세요.");
      } else {
        setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
      }
    } finally {
      setSaving(false);
    }
  };

  /** 커밋 시점 충돌 뒤 최신 값으로 폼을 다시 채운다. */
  const reloadLatest = async () => {
    setLoading(true);
    setError(null);
    setNeedsReload(false);
    setConflict(null);
    try {
      const detail = await getAnimeForCuration(animeId);
      setOriginal(detail);
      setForm(toForm(detail));
    } catch (e) {
      setError(e instanceof Error ? e.message : "불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const textField = (
    label: string,
    key: (typeof CONTENT_KEYS)[number],
    hint?: string,
  ) => (
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
        onChange={(e) =>
          setField(key, e.target.checked as FormState[typeof key])
        }
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
            <div className={`${styles.result} ${styles.resultErr}`}>
              {error ?? "불러오지 못했습니다."}
            </div>
            <div className={styles.modalActions}>
              <button className={styles.pagerBtn} onClick={onClose}>
                닫기
              </button>
            </div>
          </>
        ) : (
          <>
            <p className={styles.modalHint}>
              전달하지 않은 항목은 그대로 둡니다. 값을 비워도 지워지지
              않습니다(무시).
              {original.curated && (
                <>
                  <br />이 작품은 <strong>큐레이션 표시</strong> 상태라 TMDB
                  자동 보강 대상이 아닙니다 — 줄거리·이미지는 여기서만
                  채워집니다.
                </>
              )}
              {willMarkCurated() && (
                <>
                  <br />
                  콘텐츠를 바꾸면 <strong>큐레이션 표시</strong>가 켜져, 이후
                  TMDB 자동 보강이 이 작품을 건너뜁니다.
                </>
              )}
            </p>

            {textField("한국어 제목", "title")}
            {textField("영어 제목", "titleEn")}
            {textField("일본어 제목", "titleJp")}
            {textField("포스터 URL", "posterUrl")}
            {textField("배경 이미지 URL", "backdropUrl")}

            <div className={styles.modalField}>
              <label className={styles.filterLabel}>
                줄거리 (목록용, 500자)
              </label>
              <textarea
                className={styles.input}
                style={{
                  minHeight: 70,
                  resize: "vertical",
                  fontFamily: "inherit",
                }}
                value={form.synopsis}
                onChange={(e) => setField("synopsis", e.target.value)}
                disabled={saving}
              />
            </div>

            <div className={styles.modalField}>
              <label className={styles.filterLabel}>전체 줄거리 (상세용)</label>
              <textarea
                className={styles.input}
                style={{
                  minHeight: 110,
                  resize: "vertical",
                  fontFamily: "inherit",
                }}
                value={form.fullSynopsis}
                onChange={(e) => setField("fullSynopsis", e.target.value)}
                disabled={saving}
              />
            </div>

            <label
              className={styles.filterLabel}
              style={{ display: "block", marginTop: 6 }}
            >
              배지 / 노출
            </label>
            <div className={styles.checkGrid}>
              {checkbox(
                "사용자에게 노출",
                "isActive",
                "끄면 사용자 목록·검색에서 사라집니다",
              )}
              {checkbox("독점", "isExclusive")}
              {checkbox("인기", "isPopular")}
              {checkbox("신작", "isNew")}
              {checkbox("완결", "isCompleted")}
              {checkbox(
                "자막",
                "isSubtitle",
                "수집 시 항상 켜진 값이라 실제와 다를 수 있습니다",
              )}
              {checkbox(
                "더빙",
                "isDub",
                "수집 시 평점으로 추측된 값이라 실제와 다를 수 있습니다",
              )}
              {checkbox("동시방영", "isSimulcast")}
            </div>

            {error && (
              <div className={`${styles.result} ${styles.resultErr}`}>
                {error}
              </div>
            )}

            {conflict && (
              <div className={`${styles.result} ${styles.resultErr}`}>
                <strong>
                  저장하는 사이 다른 사람이 이 작품을 수정했습니다.
                </strong>
                <p style={{ margin: "6px 0 10px" }}>
                  항목마다 남길 값을 고르세요. 고르지 않은 항목은 서버 값이
                  그대로 남습니다. 내가 입력한 값은 지워지지 않았습니다.
                </p>
                {conflictingKeys(form, conflict.server).map((key) => {
                  const serverValue = toForm(conflict.server)[key];
                  const side = conflict.choices[key] ?? "server";
                  return (
                    <div key={key} style={{ marginBottom: 8 }}>
                      <div style={{ fontSize: 12, marginBottom: 2 }}>
                        {FIELD_LABELS[key]}
                      </div>
                      {(["mine", "server"] as Side[]).map((option) => (
                        <label
                          key={option}
                          style={{
                            display: "block",
                            fontSize: 12,
                            wordBreak: "break-all",
                          }}
                        >
                          <input
                            type="radio"
                            name={`conflict-${key}`}
                            checked={side === option}
                            disabled={saving}
                            onChange={() =>
                              setConflict((prev) =>
                                prev
                                  ? {
                                      ...prev,
                                      choices: {
                                        ...prev.choices,
                                        [key]: option,
                                      },
                                    }
                                  : prev,
                              )
                            }
                          />{" "}
                          {option === "mine" ? "내 값" : "서버 값"}:{" "}
                          {display(option === "mine" ? form[key] : serverValue)}
                        </label>
                      ))}
                    </div>
                  );
                })}
              </div>
            )}

            <div className={styles.modalActions}>
              <button
                className={styles.pagerBtn}
                onClick={onClose}
                disabled={saving}
              >
                취소
              </button>
              {needsReload && (
                <button
                  className={styles.pagerBtn}
                  onClick={reloadLatest}
                  disabled={saving}
                >
                  최신 값 불러오기
                </button>
              )}
              {conflict ? (
                <button
                  className={styles.button}
                  onClick={saveResolved}
                  disabled={saving}
                >
                  {saving ? "저장 중..." : "고른 값으로 저장"}
                </button>
              ) : (
                <button
                  className={styles.button}
                  onClick={handleSave}
                  disabled={saving}
                >
                  {saving ? "저장 중..." : "저장"}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
