"use client";
import styles from "./Footer.module.css";

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.container}>
        <div className={styles.brand}>LAPUTAL</div>

        <div className={styles.companyBlock}>
          <div className={styles.blockTitle}>(주)라퓨타사업자 정보</div>
          <ul className={styles.infoList}>
            <li>상호 : 주식회사 라퓨타 / 대표 : 김규현</li>
            <li>주소 : 경원대로 1366 7층 더조은컴퓨터아카데미 인천캠퍼스(부평동 534-48)</li>
            <li>사업자등록번호 : 231-43-8250 / 통신판매번호 : 제 2025-인천부평-7011호</li>
            <li>이메일 : kgh9806@naver.com / 대표전화 : 1644-1477</li>
          </ul>
        </div>

        <nav className={styles.links} aria-label="footer links">
          <a href="#">회사소개</a>
          <a href="#">고객센터</a>
          <a href="#">공지사항</a>
          <a href="#">이용약관</a>
          <a href="#">청소년보호정책</a>
          <a href="#" className={styles.emphasis}>개인정보 처리방침</a>
          <a href="#">저작권 표기</a>
        </nav>
      </div>
    </footer>
  );
}


