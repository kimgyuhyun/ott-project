"use client";
import styles from "./Footer.module.css";

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.container}>
        <div className={styles.brand}>LAPUTAL</div>

        <div className={styles.companyBlock}>
          <div className={styles.blockTitle}>안내</div>
          <ul className={styles.infoList}>
            <li>개인 포트폴리오 프로젝트입니다.</li>
            <li>실제 운영 중인 서비스나 등록된 사업자가 아닙니다.</li>
          </ul>
        </div>

        <nav className={styles.links} aria-label="footer links">
          <a href="#">회사소개</a>
          <a href="#">고객센터</a>
          <a href="#">공지사항</a>
          <a href="#">이용약관</a>
          <a href="#">청소년보호정책</a>
          <a href="#" className={styles.emphasis}>
            개인정보 처리방침
          </a>
          <a href="#">저작권 표기</a>
        </nav>
      </div>
    </footer>
  );
}
