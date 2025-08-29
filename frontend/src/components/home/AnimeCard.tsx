// import Image from "next/image"; // 임시로 주석 처리
import styles from "./AnimeCard.module.css";

type AnimeCardProps = {
  aniId: number;
  title: string;
  posterUrl: string;
  rating?: number | null;
  badge?: string;
  episode?: string;
  onClick?: () => void;
};

/**
 * 애니메이션 카드 컴포넌트
 * 포스터, 제목, 평점 등을 표시하는 개별 애니메이션 카드
 */
export default function AnimeCard({ 
  aniId, 
  title, 
  posterUrl, 
  rating, 
  badge, 
  episode,
  onClick
}: AnimeCardProps) {
  const handleClick = () => {
    if (onClick) {
      onClick();
    }
  };

  return (
    <div className={styles.animeCardContainer} onClick={handleClick}>
      {/* 포스터 이미지 */}
      <div className={styles.animeCardPoster}>
        <img 
          src={posterUrl || "https://placehold.co/300x400/ff69b4/ffffff?text=LAFTEL+ONLY"} 
          alt={title}
        />
      </div>

      {/* 제목 및 정보 */}
      <div className={styles.animeCardInfo}>
        <h3 className={styles.animeCardTitle}>
          {title}
        </h3>
        
        {/* 평점 표시 제거 - 제목만 깔끔하게 표시 */}
      </div>
    </div>
  );
}
