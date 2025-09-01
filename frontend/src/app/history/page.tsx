"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { getPaymentHistory, PaymentHistoryItem } from "@/lib/api/membership";

export default function HistoryPage() {
  const [items, setItems] = useState<PaymentHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const data = await getPaymentHistory();
        setItems(data || []);
      } catch (e: any) {
        setError(e?.message || "불러오기 실패");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <main style={{ maxWidth: 800, margin: "40px auto", padding: "0 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 16 }}>이용내역</h1>

      <div style={{ marginBottom: 16, color: "#777" }}>
        결제/구독 관련 내역을 임시로 표시합니다. (개발용)
      </div>

      {loading && <div>로딩 중...</div>}
      {error && <div style={{ color: "crimson" }}>{error}</div>}

      {!loading && !error && (
        <div style={{ border: "1px solid #e5e7eb", borderRadius: 8 }}>
          {items.length === 0 && (
            <div style={{ padding: 16, color: "#6b7280" }}>내역이 없습니다.</div>
          )}
          {items.map((it) => (
            <div
              key={it.id as any}
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 120px 160px 120px",
                gap: 12,
                alignItems: "center",
                padding: 12,
                borderBottom: "1px solid #f3f4f6",
              }}
            >
              <div style={{ fontWeight: 600 }}>{it.description || it.planName || it.provider || "-"}</div>
              <div style={{ textAlign: "right" }}>{typeof it.amount === "number" ? `${it.amount.toLocaleString()}원` : "-"}</div>
              <div style={{ color: "#6b7280" }}>{it.createdAt || it.paidAt || "-"}</div>
              <div style={{ textAlign: "right" }}>{it.status || "-"}</div>
            </div>
          ))}
        </div>
      )}

      <div style={{ marginTop: 24 }}>
        <Link href="/membership/guide" style={{ textDecoration: "none" }}>
          <button style={{
            padding: "10px 14px",
            borderRadius: 8,
            border: "1px solid #e5e7eb",
            background: "#f9fafb",
            cursor: "pointer"
          }}>멤버십 가이드 보기</button>
        </Link>
      </div>
    </main>
  );
}


