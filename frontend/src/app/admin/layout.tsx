"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/AuthContext";
import styles from "./admin.module.css";

/**
 * 관리자 영역 공통 레이아웃 + 접근 가드
 *
 * - 실제 권한 차단은 백엔드(SecurityConfig: /api/admin/** → ROLE_ADMIN)가 수행한다.
 * - 여기서는 UX 차원에서 비관리자를 홈으로 돌려보내고, 관리자 네비게이션을 제공한다.
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { isInitialized, isAuthenticated, isAdmin } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isInitialized) return; // 인증 초기화 전에는 판단 보류
    if (!isAuthenticated || !isAdmin) {
      router.replace("/"); // 비로그인/비관리자는 홈으로
    }
  }, [isInitialized, isAuthenticated, isAdmin, router]);

  // 초기화 전 또는 권한 없는 상태에서는 내용 노출 금지
  if (!isInitialized) {
    return <div className={styles.adminGate}>로딩 중...</div>;
  }
  if (!isAuthenticated || !isAdmin) {
    return <div className={styles.adminGate}>접근 권한이 없습니다.</div>;
  }

  return (
    <div className={styles.adminShell}>
      <aside className={styles.adminSidebar}>
        <div className={styles.adminBrand}>OTT Admin</div>
        <nav className={styles.adminNav}>
          <Link href="/admin" className={styles.adminNavLink}>대시보드</Link>
          <Link href="/admin/anime" className={styles.adminNavLink}>애니 카탈로그/동기화</Link>
        </nav>
        <Link href="/" className={styles.adminBackLink}>← 서비스로 돌아가기</Link>
      </aside>
      <main className={styles.adminMain}>{children}</main>
    </div>
  );
}
