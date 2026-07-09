"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getDailyStats,
  getRecentAuthEvents,
  rebuildDailyStats,
  type AuthEvent,
  type DailyStats,
} from "@/lib/api/admin";
import styles from "../admin.module.css";

type ResultState = { ok: boolean; text: string } | null;

// 이벤트 유형별 한글 라벨
const EVENT_LABEL: Record<AuthEvent["eventType"], string> = {
  LOGIN_SUCCESS: "로그인 성공",
  LOGIN_FAIL: "로그인 실패",
  LOGOUT: "로그아웃",
  SESSION_EXPIRED: "세션 만료",
  WITHDRAW: "회원 탈퇴",
};

// 발생 시각 표기(KST, 초까지)
function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", { hour12: false });
}

/**
 * 관리자 통계 / 감사 로그 화면
 * - 최근 N일 일일 통계(스냅샷) 조회 + 기간 합계 KPI
 * - 특정 일자 스냅샷 수동 재집계
 * - 최근 인증 이벤트 100건(감사 로그) 조회
 */
export default function AdminStatsPage() {
  // 일일 통계 상태
  const [days, setDays] = useState(30);
  const [daily, setDaily] = useState<DailyStats[]>([]);
  const [dailyLoading, setDailyLoading] = useState(false);

  // 재집계 상태
  const [rebuildDate, setRebuildDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [rebuildLoading, setRebuildLoading] = useState(false);
  const [rebuildResult, setRebuildResult] = useState<ResultState>(null);

  // 감사 로그 상태
  const [events, setEvents] = useState<AuthEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(false);

  const loadDaily = useCallback(async (d: number) => {
    setDailyLoading(true);
    try {
      const list = await getDailyStats(d);
      setDaily(Array.isArray(list) ? list : []);
    } catch (e) {
      console.error("일일 통계 조회 실패:", e);
      setDaily([]);
    } finally {
      setDailyLoading(false);
    }
  }, []);

  const loadEvents = useCallback(async () => {
    setEventsLoading(true);
    try {
      const list = await getRecentAuthEvents();
      setEvents(Array.isArray(list) ? list : []);
    } catch (e) {
      console.error("인증 이벤트 조회 실패:", e);
      setEvents([]);
    } finally {
      setEventsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDaily(days);
  }, [days, loadDaily]);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  // 기간 합계 KPI (최신 스냅샷 순회 합산)
  const totals = useMemo(() => {
    return daily.reduce(
      (acc, s) => {
        acc.loginSuccess += s.loginSuccessCount;
        acc.loginFail += s.loginFailCount;
        acc.logout += s.logoutCount;
        acc.signup += s.signupCount;
        return acc;
      },
      { loginSuccess: 0, loginFail: 0, logout: 0, signup: 0 }
    );
  }, [daily]);

  // 기간 내 최근일 DAU (가장 마지막 스냅샷)
  const latestDau = daily.length > 0 ? daily[daily.length - 1].activeUserCount : 0;

  const handleRebuild = async () => {
    if (!rebuildDate) {
      setRebuildResult({ ok: false, text: "재집계할 일자를 선택하세요." });
      return;
    }
    setRebuildLoading(true);
    setRebuildResult(null);
    try {
      const s = await rebuildDailyStats(rebuildDate);
      setRebuildResult({
        ok: true,
        text: `${s.statDate} 재집계 완료 — 로그인성공 ${s.loginSuccessCount} / 실패 ${s.loginFailCount} / 로그아웃 ${s.logoutCount} / 가입 ${s.signupCount} / DAU ${s.activeUserCount}`,
      });
      loadDaily(days); // 목록 갱신
    } catch (e) {
      setRebuildResult({ ok: false, text: e instanceof Error ? e.message : "재집계 실패" });
    } finally {
      setRebuildLoading(false);
    }
  };

  return (
    <div>
      <h1 className={styles.pageTitle}>통계 / 감사 로그</h1>
      <p className={styles.pageSubtitle}>인증 이벤트 기반 일일 통계 스냅샷과 최근 접속 감사 로그입니다.</p>

      {/* 기간 합계 KPI */}
      <section className={styles.panel}>
        <div className={styles.row} style={{ justifyContent: "space-between", alignItems: "center" }}>
          <h2 className={styles.panelTitle} style={{ margin: 0 }}>최근 {days}일 요약</h2>
          <select
            className={styles.input}
            style={{ maxWidth: 140 }}
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            disabled={dailyLoading}
          >
            <option value={7}>최근 7일</option>
            <option value={14}>최근 14일</option>
            <option value={30}>최근 30일</option>
            <option value={90}>최근 90일</option>
          </select>
        </div>
        <div className={styles.statGrid}>
          <div className={styles.statCard}>
            <div className={styles.statValue}>{totals.loginSuccess.toLocaleString()}</div>
            <div className={styles.statLabel}>로그인 성공</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statValue}>{totals.loginFail.toLocaleString()}</div>
            <div className={styles.statLabel}>로그인 실패</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statValue}>{totals.signup.toLocaleString()}</div>
            <div className={styles.statLabel}>신규 가입</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statValue}>{totals.logout.toLocaleString()}</div>
            <div className={styles.statLabel}>로그아웃</div>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statValue}>{latestDau.toLocaleString()}</div>
            <div className={styles.statLabel}>최근일 DAU</div>
          </div>
        </div>
      </section>

      {/* 일일 통계 테이블 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>일별 통계</h2>
        <p className={styles.panelHint}>매일 새벽 스냅샷으로 집계됩니다. 최신 일자가 위로 오도록 표시합니다.</p>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: 120 }}>일자</th>
                <th style={{ width: 100 }}>로그인 성공</th>
                <th style={{ width: 100 }}>로그인 실패</th>
                <th style={{ width: 90 }}>로그아웃</th>
                <th style={{ width: 90 }}>신규 가입</th>
                <th style={{ width: 80 }}>DAU</th>
              </tr>
            </thead>
            <tbody>
              {dailyLoading ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>불러오는 중...</td></tr>
              ) : daily.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>집계된 통계가 없습니다.</td></tr>
              ) : (
                [...daily].reverse().map((s) => (
                  <tr key={s.id}>
                    <td>{s.statDate}</td>
                    <td>{s.loginSuccessCount.toLocaleString()}</td>
                    <td>{s.loginFailCount.toLocaleString()}</td>
                    <td>{s.logoutCount.toLocaleString()}</td>
                    <td>{s.signupCount.toLocaleString()}</td>
                    <td>{s.activeUserCount.toLocaleString()}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* 수동 재집계 */}
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>스냅샷 재집계</h2>
        <p className={styles.panelHint}>특정 일자의 통계를 즉시 다시 계산합니다(멱등, 백필/검증용).</p>
        <div className={styles.row}>
          <input
            className={styles.input}
            type="date"
            value={rebuildDate}
            onChange={(e) => setRebuildDate(e.target.value)}
            disabled={rebuildLoading}
          />
          <button className={styles.button} onClick={handleRebuild} disabled={rebuildLoading}>
            {rebuildLoading ? "재집계 중..." : "재집계"}
          </button>
        </div>
        {rebuildResult && (
          <div className={`${styles.result} ${rebuildResult.ok ? styles.resultOk : styles.resultErr}`}>
            {rebuildResult.text}
          </div>
        )}
      </section>

      {/* 감사 로그 */}
      <section className={styles.panel}>
        <div className={styles.row} style={{ justifyContent: "space-between", alignItems: "center" }}>
          <h2 className={styles.panelTitle} style={{ margin: 0 }}>최근 인증 이벤트</h2>
          <button className={styles.pagerBtn} onClick={loadEvents} disabled={eventsLoading}>
            {eventsLoading ? "새로고침 중..." : "새로고침"}
          </button>
        </div>
        <p className={styles.panelHint}>최근 100건을 최신순으로 표시합니다. (접속 IP/제공자 포함)</p>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: 160 }}>발생 시각</th>
                <th style={{ width: 100 }}>유형</th>
                <th>이메일</th>
                <th style={{ width: 80 }}>제공자</th>
                <th style={{ width: 130 }}>IP</th>
                <th>실패 사유</th>
              </tr>
            </thead>
            <tbody>
              {eventsLoading ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>불러오는 중...</td></tr>
              ) : events.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>기록된 이벤트가 없습니다.</td></tr>
              ) : (
                events.map((ev) => (
                  <tr key={ev.id}>
                    <td>{formatTime(ev.occurredAt)}</td>
                    <td>{EVENT_LABEL[ev.eventType] ?? ev.eventType}</td>
                    <td>{ev.email || "-"}</td>
                    <td>{ev.provider || "-"}</td>
                    <td>{ev.ipAddress || "-"}</td>
                    <td>{ev.failReason || "-"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
