"use client";
import { useCallback, useEffect, useState } from "react";
import {
  syncAnime,
  syncPopularAnime,
  enhanceAllAnime,
  searchAnimeForCuration,
} from "@/lib/api/admin";
import type { AdminAnimeItem, AnimeCurationSearchCondition } from "@/lib/api/admin";
import AnimeCurationEditModal from "@/components/admin/AnimeCurationEditModal";
import AnimeEpisodeManageModal from "@/components/admin/AnimeEpisodeManageModal";
import AnimeBulkCurationModal from "@/components/admin/AnimeBulkCurationModal";
import styles from "../admin.module.css";

type ResultState = { ok: boolean; text: string } | null;

const PAGE_SIZE = 20;

/**
 * 필터의 3상태 선택값.
 * ""(전체)는 조건을 걸지 않음을 뜻하며, 백엔드는 파라미터 부재를 그렇게 읽는다.
 */
type TriState = "" | "true" | "false";

const EMPTY_FILTERS = {
  titleKeyword: "",
  status: "" as AnimeCurationSearchCondition["status"] | "",
  year: "",
  isActive: "" as TriState,
  curated: "" as TriState,
  syncOrigin: "" as AnimeCurationSearchCondition["syncOrigin"] | "",
  isExclusive: "" as TriState,
  isPopular: "" as TriState,
  isNew: "" as TriState,
};

type Filters = typeof EMPTY_FILTERS;

/** 폼의 문자열 상태를 백엔드 조건 객체로 옮긴다. 빈 값은 조건에서 제외한다. */
function toCondition(filters: Filters): AnimeCurationSearchCondition {
  const condition: AnimeCurationSearchCondition = {};
  if (filters.titleKeyword.trim()) condition.titleKeyword = filters.titleKeyword.trim();
  if (filters.status) condition.status = filters.status;
  if (filters.year.trim()) condition.year = Number(filters.year);
  if (filters.syncOrigin) condition.syncOrigin = filters.syncOrigin;
  if (filters.isActive) condition.isActive = filters.isActive === "true";
  if (filters.curated) condition.curated = filters.curated === "true";
  if (filters.isExclusive) condition.isExclusive = filters.isExclusive === "true";
  if (filters.isPopular) condition.isPopular = filters.isPopular === "true";
  if (filters.isNew) condition.isNew = filters.isNew === "true";
  return condition;
}

/**
 * 애니 카탈로그/큐레이션 관리 화면
 * - 큐레이션 검색(조건 자유 조합), 단일 동기화(MAL ID), 인기 일괄 동기화, TMDB 보강
 *
 * 목록이 사용자향 /api/anime 가 아니라 /api/admin/anime/search 를 쓰는 이유
 * - 공개 목록 쿼리는 WHERE is_active = TRUE 가 하드코딩돼 있어 비활성 작품이 아예 안 보인다.
 *   그 목록으로는 한 번 내린 작품을 다시 찾아 되돌릴 수 없다.
 * - isActive/curated/malId 처럼 운영자가 판단 근거로 삼는 값은 관리자 응답에만 있다.
 */
export default function AdminAnimePage() {
  // 단일 동기화 상태
  const [malId, setMalId] = useState("");
  const [singleLoading, setSingleLoading] = useState(false);
  const [singleResult, setSingleResult] = useState<ResultState>(null);

  // 일괄 동기화 상태
  const [bulkLimit, setBulkLimit] = useState("50");
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkResult, setBulkResult] = useState<ResultState>(null);

  // TMDB 보강 상태
  const [enhanceLoading, setEnhanceLoading] = useState(false);
  const [enhanceResult, setEnhanceResult] = useState<ResultState>(null);

  // 큐레이션 목록 상태
  const [items, setItems] = useState<AdminAnimeItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  // 필터: 입력 중인 값(filters)과 실제로 조회에 쓰인 값(appliedFilters)을 분리한다.
  // 입력할 때마다 조회하면 타이핑 한 글자마다 요청이 나가고, 페이지 이동 시 어떤 조건으로
  // 보고 있었는지가 흔들린다.
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<Filters>(EMPTY_FILTERS);

  const loadCatalog = useCallback(async (p: number, applied: Filters) => {
    setListLoading(true);
    setListError(null);
    try {
      const res = await searchAnimeForCuration(toCondition(applied), p, PAGE_SIZE);
      setItems(res.items);
      setTotal(res.total);
    } catch (e) {
      console.error("큐레이션 목록 조회 실패:", e);
      setItems([]);
      setTotal(0);
      setListError(e instanceof Error ? e.message : "목록을 불러오지 못했습니다.");
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCatalog(page, appliedFilters);
  }, [page, appliedFilters, loadCatalog]);

  const handleSearch = () => {
    setPage(0); // 조건이 바뀌면 첫 페이지부터 — 3페이지를 보던 중 조건을 바꾸면 빈 화면이 된다
    setAppliedFilters(filters);
  };

  const handleResetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setPage(0);
    setAppliedFilters(EMPTY_FILTERS);
  };

  const setFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  // 편집 모달 대상 ID. null 이면 닫힘.
  // 항목 객체가 아니라 ID 만 들고 모달이 상세를 따로 받는다 — 줄거리는 목록 응답에 없다.
  const [editing, setEditing] = useState<number | null>(null);
  // 에피소드 모달은 제목까지 필요하다(헤더에 표시). 목록 항목에서 그대로 넘긴다.
  const [managingEpisodesOf, setManagingEpisodesOf] = useState<{ id: number; title: string } | null>(null);

  const handleEditSaved = () => {
    setEditing(null);
    loadCatalog(page, appliedFilters); // 보던 조건/페이지를 유지한 채 값만 갱신
  };

  // 벌크 모달
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkResultText, setBulkResultText] = useState<ResultState>(null);

  // 벌크는 '검색에 실제로 적용된' 조건을 쓴다. 입력 중인 필터를 쓰면
  // 운영자가 눈으로 본 목록과 수정 대상이 어긋난다.
  const appliedCondition = toCondition(appliedFilters);
  const hasAppliedCondition = Object.keys(appliedCondition).length > 0;

  const handleBulkApplied = (affectedCount: number) => {
    setBulkOpen(false);
    setBulkResultText({ ok: true, text: `${affectedCount.toLocaleString()}건을 일괄 수정했습니다.` });
    loadCatalog(page, appliedFilters);
  };

  const handleSingleSync = async () => {
    const id = Number(malId);
    if (!id || id <= 0) {
      setSingleResult({ ok: false, text: "유효한 MAL ID를 입력하세요." });
      return;
    }
    setSingleLoading(true);
    setSingleResult(null);
    try {
      const res = await syncAnime(id);
      setSingleResult({ ok: res.success, text: res.message });
      if (res.success) { setPage(0); loadCatalog(0, appliedFilters); }
    } catch (e) {
      setSingleResult({ ok: false, text: e instanceof Error ? e.message : "동기화 실패" });
    } finally {
      setSingleLoading(false);
    }
  };

  const handleBulkSync = async () => {
    const limit = Number(bulkLimit);
    if (!limit || limit <= 0) {
      setBulkResult({ ok: false, text: "1 이상의 수집 개수를 입력하세요." });
      return;
    }
    if (!window.confirm(`인기 애니메이션 ${limit}개를 일괄 수집합니다. 시간이 걸릴 수 있어요. 진행할까요?`)) return;
    setBulkLoading(true);
    setBulkResult(null);
    try {
      const res = await syncPopularAnime(limit);
      const stats = res.statistics ? `\n${JSON.stringify(res.statistics, null, 2)}` : "";
      setBulkResult({ ok: res.success, text: `${res.message}${stats}` });
      if (res.success) { setPage(0); loadCatalog(0, appliedFilters); }
    } catch (e) {
      setBulkResult({ ok: false, text: e instanceof Error ? e.message : "일괄 동기화 실패" });
    } finally {
      setBulkLoading(false);
    }
  };

  const handleEnhanceAll = async () => {
    if (!window.confirm("한국어 정보가 없는 애니를 TMDB로 보강합니다. 서버에서 백그라운드로 진행됩니다. 시작할까요?")) return;
    setEnhanceLoading(true);
    setEnhanceResult(null);
    try {
      await enhanceAllAnime();
      // 백엔드가 @Async 라 완료가 아니라 "시작"만 응답한다. 진행/결과는 서버 로그에서 확인.
      setEnhanceResult({ ok: true, text: "보강 작업을 시작했습니다. 진행 상황은 서버 로그를 확인하세요. (완료 후 목록 새로고침)" });
    } catch (e) {
      setEnhanceResult({ ok: false, text: e instanceof Error ? e.message : "보강 시작 실패" });
    } finally {
      setEnhanceLoading(false);
    }
  };

  return (
    <div>
      <h1 className={styles.pageTitle}>애니 카탈로그 / 큐레이션</h1>
      <p className={styles.pageSubtitle}>
        Jikan/TMDB에서 수집한 애니를 검색하고 노출 여부·배지를 관리합니다. (19금 콘텐츠는 수집 시 자동 제외)
      </p>

      {/* 단일 동기화 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>단일 동기화</h2>
        <p className={styles.panelHint}>MyAnimeList ID(MAL ID)로 특정 작품 하나를 수집합니다.</p>
        <div className={styles.row}>
          <input
            className={styles.input}
            type="number"
            placeholder="MAL ID (예: 5114)"
            value={malId}
            onChange={(e) => setMalId(e.target.value)}
            disabled={singleLoading}
          />
          <button className={styles.button} onClick={handleSingleSync} disabled={singleLoading}>
            {singleLoading ? "동기화 중..." : "동기화"}
          </button>
        </div>
        {singleResult && (
          <div className={`${styles.result} ${singleResult.ok ? styles.resultOk : styles.resultErr}`}>
            {singleResult.text}
          </div>
        )}
      </section>

      {/* 인기 일괄 동기화 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>인기 애니 일괄 동기화</h2>
        <p className={styles.panelHint}>Jikan 인기 목록을 한 번에 수집합니다. (최대 5000개, 많을수록 오래 걸림)</p>
        <div className={styles.row}>
          <input
            className={styles.input}
            type="number"
            placeholder="수집 개수 (기본 50)"
            value={bulkLimit}
            onChange={(e) => setBulkLimit(e.target.value)}
            disabled={bulkLoading}
          />
          <button className={styles.button} onClick={handleBulkSync} disabled={bulkLoading}>
            {bulkLoading ? "수집 중..." : "일괄 동기화"}
          </button>
        </div>
        {bulkResult && (
          <div className={`${styles.result} ${bulkResult.ok ? styles.resultOk : styles.resultErr}`}>
            {bulkResult.text}
          </div>
        )}
      </section>

      {/* TMDB 보강 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>TMDB 데이터 보강</h2>
        <p className={styles.panelHint}>Jikan으로 수집한 데이터에 한국어 제목/줄거리 등을 TMDB에서 채웁니다. 백그라운드로 실행되며 즉시 반환됩니다.</p>
        <div className={styles.row}>
          <button className={styles.button} onClick={handleEnhanceAll} disabled={enhanceLoading}>
            {enhanceLoading ? "시작 중..." : "전체 보강 시작"}
          </button>
        </div>
        {enhanceResult && (
          <div className={`${styles.result} ${enhanceResult.ok ? styles.resultOk : styles.resultErr}`}>
            {enhanceResult.text}
          </div>
        )}
      </section>

      {/* 큐레이션 검색 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>애니 큐레이션</h2>
        <p className={styles.panelHint}>
          조건을 자유롭게 조합해 작품을 찾습니다. 조건을 비우면 전체가 나옵니다.
          사용자 화면과 달리 <strong>숨김(비활성) 작품도 보입니다</strong>.
        </p>

        <div className={styles.filterGrid}>
          <div className={styles.filterField}>
            <label className={styles.filterLabel}>제목 (한/영/일 통합)</label>
            <input
              className={styles.input}
              type="text"
              placeholder="예: 귀멸"
              value={filters.titleKeyword}
              onChange={(e) => setFilter("titleKeyword", e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
            />
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>방영 상태</label>
            <select
              className={styles.select}
              value={filters.status ?? ""}
              onChange={(e) => setFilter("status", e.target.value as Filters["status"])}
            >
              <option value="">전체</option>
              <option value="ONGOING">방영중</option>
              <option value="COMPLETED">완결</option>
              <option value="UPCOMING">방영예정</option>
              <option value="HIATUS">방영중단</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>연도</label>
            <input
              className={styles.input}
              type="number"
              placeholder="예: 2026"
              value={filters.year}
              onChange={(e) => setFilter("year", e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
            />
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>노출 여부</label>
            <select
              className={styles.select}
              value={filters.isActive}
              onChange={(e) => setFilter("isActive", e.target.value as TriState)}
            >
              <option value="">전체</option>
              <option value="true">노출 중</option>
              <option value="false">숨김</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>큐레이션 여부</label>
            <select
              className={styles.select}
              value={filters.curated}
              onChange={(e) => setFilter("curated", e.target.value as TriState)}
            >
              <option value="">전체</option>
              <option value="true">운영자 수정함</option>
              <option value="false">자동 수집 그대로</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>유입 경로</label>
            <select
              className={styles.select}
              value={filters.syncOrigin ?? ""}
              onChange={(e) => setFilter("syncOrigin", e.target.value as Filters["syncOrigin"])}
            >
              <option value="">전체</option>
              <option value="JIKAN">Jikan 동기화</option>
              <option value="MANUAL">수동 등록</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>독점</label>
            <select
              className={styles.select}
              value={filters.isExclusive}
              onChange={(e) => setFilter("isExclusive", e.target.value as TriState)}
            >
              <option value="">전체</option>
              <option value="true">켜짐</option>
              <option value="false">꺼짐</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>인기</label>
            <select
              className={styles.select}
              value={filters.isPopular}
              onChange={(e) => setFilter("isPopular", e.target.value as TriState)}
            >
              <option value="">전체</option>
              <option value="true">켜짐</option>
              <option value="false">꺼짐</option>
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.filterLabel}>신작</label>
            <select
              className={styles.select}
              value={filters.isNew}
              onChange={(e) => setFilter("isNew", e.target.value as TriState)}
            >
              <option value="">전체</option>
              <option value="true">켜짐</option>
              <option value="false">꺼짐</option>
            </select>
          </div>
        </div>

        <div className={styles.row}>
          <button className={styles.button} onClick={handleSearch} disabled={listLoading}>
            {listLoading ? "검색 중..." : "검색"}
          </button>
          <button className={styles.pagerBtn} onClick={handleResetFilters} disabled={listLoading}>
            조건 초기화
          </button>
          <span style={{ color: "#9aa0aa", fontSize: 13 }}>총 {total.toLocaleString()}건</span>
          <button
            className={styles.pagerBtn}
            style={{ marginLeft: "auto" }}
            onClick={() => { setBulkResultText(null); setBulkOpen(true); }}
            // 조건 없는 벌크는 백엔드가 400 으로 거부한다(=전체 수정 방지). 버튼 단계에서 미리 막는다.
            disabled={listLoading || !hasAppliedCondition || total === 0}
            title={hasAppliedCondition ? undefined : "먼저 검색 조건을 걸어야 합니다"}
          >
            이 조건으로 일괄 수정
          </button>
        </div>

        {listError && <div className={`${styles.result} ${styles.resultErr}`}>{listError}</div>}
        {bulkResultText && (
          <div className={`${styles.result} ${bulkResultText.ok ? styles.resultOk : styles.resultErr}`}>
            {bulkResultText.text}
          </div>
        )}

        <div className={styles.tableWrap} style={{ marginTop: 14 }}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: 56 }}>포스터</th>
                <th style={{ width: 60 }}>ID</th>
                <th>제목</th>
                <th style={{ width: 100 }}>상태</th>
                <th style={{ width: 70 }}>연도</th>
                <th style={{ width: 200 }}>큐레이션</th>
                <th style={{ width: 90 }}>유입</th>
                <th style={{ width: 70 }}></th>
              </tr>
            </thead>
            <tbody>
              {listLoading ? (
                <tr><td colSpan={8} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>불러오는 중...</td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={8} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>조건에 맞는 작품이 없습니다.</td></tr>
              ) : (
                items.map((it) => (
                  <tr key={it.id} className={it.isActive ? undefined : styles.rowInactive}>
                    <td>
                      {/* 외부 포스터 URL이라 next/image 대신 img 사용 */}
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img className={styles.thumb} src={it.posterUrl || "/placeholder-anime.jpg"} alt={it.title || ""} />
                    </td>
                    <td>{it.id}</td>
                    <td>
                      {it.title || <span style={{ color: "#9aa0aa" }}>(한국어 제목 없음)</span>}
                      {it.titleEn && <div style={{ color: "#9aa0aa", fontSize: 11 }}>{it.titleEn}</div>}
                    </td>
                    <td>{it.status}</td>
                    <td>{it.year ?? "-"}</td>
                    <td>
                      {/* 숨김은 "사용자에게 안 보인다"는 뜻이라 가장 먼저 눈에 띄어야 한다 */}
                      {!it.isActive && <span className={`${styles.badge} ${styles.badgeOff}`}>숨김</span>}
                      {it.curated && <span className={`${styles.badge} ${styles.badgeCurated}`}>큐레이션</span>}
                      {it.isExclusive && <span className={`${styles.badge} ${styles.badgeOn}`}>독점</span>}
                      {it.isPopular && <span className={`${styles.badge} ${styles.badgeOn}`}>인기</span>}
                      {it.isNew && <span className={`${styles.badge} ${styles.badgeOn}`}>신작</span>}
                      {it.isCompleted && <span className={`${styles.badge} ${styles.badgeOn}`}>완결</span>}
                      {it.isSubtitle && <span className={`${styles.badge} ${styles.badgeOn}`}>자막</span>}
                      {it.isDub && <span className={`${styles.badge} ${styles.badgeOn}`}>더빙</span>}
                      {it.isSimulcast && <span className={`${styles.badge} ${styles.badgeOn}`}>동시</span>}
                    </td>
                    <td style={{ color: "#9aa0aa" }}>{it.syncOrigin === "JIKAN" ? "Jikan" : "수동"}</td>
                    <td>
                      <div style={{ display: "flex", gap: 6 }}>
                        <button className={styles.pagerBtn} onClick={() => setEditing(it.id)}>수정</button>
                        {/* 작품 필드와 에피소드는 저장 대상이 달라 모달을 나눈다 */}
                        <button
                          className={styles.pagerBtn}
                          onClick={() => setManagingEpisodesOf({ id: it.id, title: it.title ?? "" })}
                        >
                          화수
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className={styles.pager}>
          <button
            className={styles.pagerBtn}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0 || listLoading}
          >
            이전
          </button>
          {/* total 을 주는 엔드포인트라 마지막 페이지를 실제로 알 수 있다(이전에는 추측했다) */}
          <span style={{ color: "#9aa0aa", fontSize: 13 }}>{page + 1} / {totalPages} 페이지</span>
          <button
            className={styles.pagerBtn}
            onClick={() => setPage((p) => p + 1)}
            disabled={page >= totalPages - 1 || listLoading}
          >
            다음
          </button>
        </div>
      </section>

      {editing !== null && (
        <AnimeCurationEditModal
          animeId={editing}
          onClose={() => setEditing(null)}
          onSaved={handleEditSaved}
        />
      )}

      {managingEpisodesOf !== null && (
        <AnimeEpisodeManageModal
          animeId={managingEpisodesOf.id}
          animeTitle={managingEpisodesOf.title}
          onClose={() => setManagingEpisodesOf(null)}
        />
      )}

      {bulkOpen && (
        <AnimeBulkCurationModal
          condition={appliedCondition}
          matchedCount={total}
          onClose={() => setBulkOpen(false)}
          onApplied={handleBulkApplied}
        />
      )}
    </div>
  );
}
