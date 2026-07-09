"use client";
import { useCallback, useEffect, useState } from "react";
import { syncAnime, syncPopularAnime } from "@/lib/api/admin";
import { getAnimeList } from "@/lib/api/anime";
import type { AnimeListItem } from "@/types/anime";
import styles from "../admin.module.css";

type ResultState = { ok: boolean; text: string } | null;

const PAGE_SIZE = 20;

/**
 * 애니 카탈로그/동기화 관리 화면
 * - 단일 동기화(MAL ID), 인기 일괄 동기화, 등록된 카탈로그 목록 조회
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

  // 카탈로그 목록 상태
  const [items, setItems] = useState<AnimeListItem[]>([]);
  const [page, setPage] = useState(0);
  const [listLoading, setListLoading] = useState(false);

  const loadCatalog = useCallback(async (p: number) => {
    setListLoading(true);
    try {
      const raw = await getAnimeList(p, PAGE_SIZE, "id");
      setItems(raw.items);
    } catch (e) {
      console.error("카탈로그 조회 실패:", e);
      setItems([]);
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCatalog(page);
  }, [page, loadCatalog]);

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
      if (res.success) { setPage(0); loadCatalog(0); }
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
      if (res.success) { setPage(0); loadCatalog(0); }
    } catch (e) {
      setBulkResult({ ok: false, text: e instanceof Error ? e.message : "일괄 동기화 실패" });
    } finally {
      setBulkLoading(false);
    }
  };

  return (
    <div>
      <h1 className={styles.pageTitle}>애니 카탈로그 / 동기화</h1>
      <p className={styles.pageSubtitle}>Jikan API에서 애니메이션을 수집해 DB에 등록합니다. (19금 콘텐츠는 자동 제외)</p>

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

      {/* 카탈로그 목록 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>등록된 카탈로그</h2>
        <p className={styles.panelHint}>현재 DB에 등록된 애니메이션 목록입니다.</p>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: 56 }}>포스터</th>
                <th style={{ width: 70 }}>ID</th>
                <th>제목</th>
                <th style={{ width: 110 }}>타입</th>
                <th style={{ width: 110 }}>상태</th>
                <th style={{ width: 80 }}>연도</th>
              </tr>
            </thead>
            <tbody>
              {listLoading ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>불러오는 중...</td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>등록된 작품이 없습니다.</td></tr>
              ) : (
                items.map((it) => (
                  <tr key={it.aniId}>
                    <td>
                      {/* 외부 포스터 URL이라 next/image 대신 img 사용 */}
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img className={styles.thumb} src={it.posterUrl || "/placeholder-anime.jpg"} alt={it.title || ""} />
                    </td>
                    <td>{it.aniId}</td>
                    <td>{it.title || "-"}</td>
                    <td>{it.type || "-"}</td>
                    <td>{it.animeStatus || "-"}</td>
                    <td>{it.year || "-"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className={styles.pager}>
          <button className={styles.pagerBtn} onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0 || listLoading}>
            이전
          </button>
          <span style={{ color: "#9aa0aa", fontSize: 13 }}>{page + 1} 페이지</span>
          <button className={styles.pagerBtn} onClick={() => setPage((p) => p + 1)} disabled={items.length < PAGE_SIZE || listLoading}>
            다음
          </button>
        </div>
      </section>
    </div>
  );
}
