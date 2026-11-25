import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import styles from "./layout.module.css";

// 헤더·푸터가 있는 화면의 공통 틀. 헤더는 이 틀 안의 첫 칸에 sticky 로 붙고,
// 페이지는 그 아래 .content 칸이 준 자리만 채운다(헤더 높이만큼 비켜 가는 여백을 두지 않는다).
export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className={styles.shell}>
      <Header />
      <div className={styles.content}>{children}</div>
      <Footer />
    </div>
  );
}
