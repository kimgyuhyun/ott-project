"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { getPaymentHistory, PaymentHistoryItem } from "@/lib/api/membership";
import styles from "./history.module.css";

function formatDate(dateLike?: string): string {
  if (!dateLike) return "";
  const d = new Date(dateLike);
  if (isNaN(d.getTime())) return String(dateLike);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function mapPlanName(raw?: string, provider?: string): string {
  const s = String(raw || provider || "").toLowerCase();
  if (s.includes("premium")) return "프리미엄 멤버십";
  // 기본은 멤버십으로 표시
  return "멤버십";
}

function mapMethod(raw?: string, provider?: string): string {
  const s = String(raw || provider || "").toLowerCase();
  if (s.includes("kakao")) return "카카오페이";
  if (s.includes("danal") || s.includes("mobile")) return "다날(휴대폰)";
  if (s.includes("toss")) return "토스페이";
  if (s.includes("naver")) return "네이버페이";
  if (s.includes("card")) return "신용카드";
  return raw || provider || "";
}

export default function HistoryPage() {
  const [items, setItems] = useState<PaymentHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const data = await getPaymentHistory();
        setItems(Array.isArray(data) ? data : []);
      } catch (e: any) {
        setError(e?.message || "불러오기 실패");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <main className={styles.historyPage}>
      <h2 className={styles.title}>이용내역</h2>

      {loading && <div className={styles.stateText}>로딩 중...</div>}
      {error && <div className={`${styles.stateText} ${styles.error}`}>{error}</div>}

      {!loading && !error && (
        <div className={styles.listContainer}>
          {items.length === 0 && (
            <div className={styles.empty}>내역이 없습니다.</div>
          )}

          {items.map((it) => {
            const dateText = formatDate(it.createdAt || it.paidAt || "");
            const nameText = mapPlanName(it.planName || it.description, it.provider);
            const methodText = mapMethod(it.method, it.provider);
            const amountText = typeof it.amount === "number" ? `${it.amount.toLocaleString()}원` : "";
            return (
              <div key={String(it.id)} className={styles.itemRow}>
                <div className={styles.colLeft}>
                  {dateText && <span className={styles.colDate}>{dateText}</span>}
                  {nameText && <span className={styles.plan}>{nameText}</span>}
                </div>
                <div className={styles.colRight}>
                  {methodText && <span className={styles.method}>{methodText}</span>}
                  {amountText && <span className={styles.price}>{amountText}</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className={styles.footerActions}>
        <Link href="/membership/guide" className={styles.linkButton}>
          멤버십 가이드 보기
        </Link>
      </div>
    </main>
  );
}


