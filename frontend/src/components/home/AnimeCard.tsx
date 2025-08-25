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
  return (
    <div 
      className="group cursor-pointer transition-transform hover:scale-105"
      onClick={onClick}
    >
      {/* 포스터 이미지 */}
      <div className="relative aspect-[3/4] rounded-lg overflow-hidden bg-gray-800">
        <img
          src={posterUrl}
          alt={title}
          className="w-full h-full object-cover group-hover:brightness-110 transition-all"
        />
        
        {/* 배지 (UP, 신작 등) */}
        {badge && (
          <div className="absolute top-2 left-2 bg-red-600 text-white text-xs font-bold px-2 py-1 rounded">
            {badge}
          </div>
        )}

        {/* 에피소드 정보 */}
        {episode && (
          <div className="absolute bottom-2 right-2 bg-black/70 text-white text-xs px-2 py-1 rounded">
            {episode}
          </div>
        )}

        {/* 호버 시 재생 버튼 */}
        <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
          <div className="w-12 h-12 bg-white/20 rounded-full flex items-center justify-center">
            <svg className="w-6 h-6 text-white ml-1" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 6.82v10.36c0 .79.87 1.27 1.54.84l8.14-5.18c.62-.39.62-1.29 0-1.68L9.54 5.98C8.87 5.55 8 6.03 8 6.82z"/>
            </svg>
          </div>
        </div>
      </div>

      {/* 제목 및 정보 */}
      <div className="mt-3">
        <h3 className="text-white font-medium text-sm line-clamp-2 leading-tight">
          {title}
        </h3>
        
        {/* 평점 */}
        {rating && (
          <div className="flex items-center mt-1">
            <span className="text-yellow-400 text-xs">★</span>
            <span className="text-gray-400 text-xs ml-1">
              {rating.toFixed(1)}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
