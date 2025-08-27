// import Image from "next/image"; // 임시로 주석 처리

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
    <div 
      className="group cursor-pointer transition-transform hover:scale-105"
      onClick={handleClick}
    >
      {/* 포스터 이미지 */}
      <div className="relative aspect-[3/4] rounded-lg overflow-hidden bg-gray-200">
        <div className="w-full h-full bg-gray-200 rounded-lg"></div>
      </div>

      {/* 제목 및 정보 */}
      <div className="mt-3">
                 <h3 className="text-gray-800 font-medium text-sm line-clamp-2 leading-tight">
           {title}
         </h3>
        
                 {/* 평점 표시 제거 - 제목만 깔끔하게 표시 */}
      </div>
    </div>
  );
}
