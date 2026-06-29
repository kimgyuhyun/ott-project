"use client";
import Link from "next/link";
import styles from "./admin.module.css";

/**
 * 관리자 대시보드 홈
 * - 관리 영역으로 가는 진입점(카드 네비게이션)
 */
export default function AdminHome() {
  return (
    <div>
      <h1 className={styles.pageTitle}>관리자 대시보드</h1>
      <p className={styles.pageSubtitle}>운영 작업 영역입니다. ROLE_ADMIN 계정만 접근할 수 있습니다.</p>

      <div className={styles.cardGrid}>
        <Link href="/admin/anime" className={styles.card}>
          <h2 className={styles.cardTitle}>애니 카탈로그 / 동기화</h2>
          <p className={styles.cardDesc}>Jikan API로 애니메이션을 수집·등록하고, 등록된 카탈로그를 조회합니다.</p>
        </Link>
      </div>
    </div>
  );
}
