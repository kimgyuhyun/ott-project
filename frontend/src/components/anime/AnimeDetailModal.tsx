"use client";
import { useState } from "react";
import ReviewList from "@/components/reviews/ReviewList";

interface AnimeDetailModalProps {
  anime: any;
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 애니메이션 상세 정보 모달
 * 평점, 제목, 장르, 액션 버튼, 시놉시스, 탭 메뉴, 에피소드 목록 포함
 */
export default function AnimeDetailModal({ anime, isOpen, onClose }: AnimeDetailModalProps) {
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');

  // 디버깅: anime 객체 확인
  console.log('🔍 AnimeDetailModal - anime 객체:', anime);
  console.log('🔍 AnimeDetailModal - anime.aniId:', anime?.aniId);
  console.log('🔍 AnimeDetailModal - anime 타입:', typeof anime);

  if (!isOpen) return null;

  const tabs: { id: 'episodes' | 'reviews' | 'shop' | 'similar'; label: string; count: number | null }[] = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: null },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = [
    {
      id: 1,
      title: "1화 2016년의 너에게",
      duration: "37분",
      date: "2025.07.18",
      thumbnail: "https://placehold.co/120x80/ff69b4/ffffff?text=Episode+1",
      description: "이건 깜짝 파티에도 쓰는 도구인데피....",
      fullDescription: "행복을 전파하기 위해 지구에 내려온 해피성인의 타코피는 인간 여자아이인 시즈카와 만났다. 위기인 가운데 시즈카가 자신을 구해주자, 타코피는 그녀의 미소를 되찾기 위해 수수께끼의 힘을 가진 해피 도구를 사용해 분주히 움직였다...."
    },
    {
      id: 2,
      title: "2화 타코피의 구제",
      duration: "21분",
      date: "2025.07.25",
      thumbnail: "https://placehold.co/120x80/ff69b4/ffffff?text=Episode+2",
      description: "타코피의 구제를 위한 새로운 모험이 시작된다.",
      fullDescription: "타코피의 구제를 위한 새로운 모험이 시작된다."
    }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 배경 오버레이 */}
      <div 
        className="absolute inset-0 bg-black/20 backdrop-blur-sm"
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 - 흰색 배경으로 변경 */}
      <div className="relative bg-white rounded-2xl max-w-6xl w-full mx-4 max-h-[90vh] overflow-y-auto shadow-2xl">
        {/* 닫기 버튼 - 상단 오른쪽 */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-600 hover:text-gray-800 transition-colors z-20"
          aria-label="닫기"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 상단 정보 섹션 */}
        <div className="relative p-6 pb-0">
          {/* 배경 이미지 */}
          <div className="absolute inset-0 z-0">
            <div className="w-full h-full bg-gradient-to-br from-blue-50 via-purple-50 to-pink-50 rounded-t-2xl">
              {/* 애니 캐릭터 이미지 (플레이스홀더) */}
              <div className="absolute inset-0 bg-cover bg-center bg-no-repeat opacity-30"
                   style={{
                     backgroundImage: 'url("https://placehold.co/800x400/ff69b4/ffffff?text=Anime+Character")'
                   }}>
              </div>
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div className="absolute top-1/2 right-6 transform -translate-y-1/2 z-10">
            <div className="w-24 h-32 bg-white rounded-lg overflow-hidden shadow-lg border-2 border-purple-200">
              <img 
                src={anime?.posterUrl || "https://placehold.co/96x128/ff69b4/ffffff?text=LAFTEL+ONLY"} 
                alt={`${anime?.title || '애니메이션'} 포스터`}
                className="w-full h-full object-cover"
              />
            </div>
          </div>

          {/* 상단 정보 오버레이 */}
          <div className="relative z-10 text-gray-800">
            {/* 평점 및 배지 - 왼쪽 상단 */}
            <div className="flex items-center space-x-3 mb-4">
              <div className="flex items-center space-x-1">
                <span className="text-yellow-500 text-lg">★</span>
                <span className="font-semibold text-lg text-gray-800">
                  {anime?.rating?.toFixed(1) || '4.7'}
                </span>
              </div>
              <span className="px-2 py-1 bg-red-500 text-white text-xs font-bold rounded">
                {anime?.badges?.[0] || 'ONLY'}
              </span>
            </div>

            {/* 애니메이션 제목 */}
            <h1 className="text-3xl font-bold text-gray-800 mb-4">
              {anime?.title || '타코피의 원죄'}
            </h1>

            {/* 장르 및 정보 */}
            <div className="flex flex-wrap items-center gap-3 mb-6">
              <span className="px-3 py-1 bg-gray-200 text-gray-700 rounded-full text-sm">
                {anime?.genres?.[0] || '개그'}
              </span>
              <span className="px-3 py-1 bg-gray-200 text-gray-700 rounded-full text-sm">
                {anime?.genres?.[1] || '판타지'}
              </span>
              <span className="text-gray-600 text-sm">
                {anime?.episodeCount || '12'}화 완결
              </span>
            </div>

            {/* 액션 버튼들 */}
            <div className="flex flex-wrap gap-3 mb-6">
              <button 
                                 onClick={() => {
                   // 플레이어 페이지로 이동
                   window.open(`/player?episodeId=1&animeId=${anime?.aniId}`, '_blank');
                 }}
                className="px-6 py-3 bg-purple-600 hover:bg-purple-700 text-white rounded-lg font-semibold transition-colors flex items-center space-x-2"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h1m4 0h1m-6 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>보러가기</span>
              </button>
              <button className="px-6 py-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg font-semibold transition-colors flex items-center space-x-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                <span>찜하기</span>
              </button>
              <button className="px-6 py-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg font-semibold transition-colors flex items-center space-x-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.367 2.684 3 3 0 00-5.367-2.684z" />
                </svg>
                <span>공유</span>
              </button>
            </div>

            {/* 시놉시스 */}
            <div className="mb-6">
              <h3 className="text-lg font-semibold text-gray-800 mb-3">시놉시스</h3>
              <p className="text-gray-600 leading-relaxed">
                {anime?.synopsis || "행복을 전파하기 위해 지구에 내려온 해피성인의 타코피는 인간 여자아이인 시즈카와 만났다. 위기인 가운데 시즈카가 자신을 구해주자, 타코피는 그녀의 미소를 되찾기 위해 수수께끼의 힘을 가진 해피 도구를 사용해 분주히 움직였다..."}
              </p>
            </div>
          </div>
        </div>

        {/* 탭 메뉴 */}
        <div className="border-b border-gray-200">
          <div className="flex space-x-8 px-6">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center space-x-2 py-4 px-1 border-b-2 transition-colors ${
                  activeTab === tab.id
                    ? 'border-purple-500 text-purple-600 font-semibold'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <span className="text-sm">{tab.label}</span>
                {tab.count !== null && (
                  <span className="text-sm text-gray-400">({tab.count})</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* 탭 콘텐츠 */}
        <div className="p-6">
          {activeTab === 'episodes' && (
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">에피소드 목록</h3>
              <div className="space-y-4">
                {episodes.map((episode) => (
                  <div key={episode.id} className="flex items-start space-x-4 p-4 bg-gray-50 rounded-lg">
                    <div className="flex-shrink-0">
                      <img 
                        src={episode.thumbnail} 
                        alt={episode.title}
                        className="w-20 h-14 object-cover rounded"
                      />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-sm font-medium text-gray-800 truncate">
                          {episode.title}
                        </h4>
                        <div className="flex items-center space-x-3 text-xs text-gray-500">
                          <span>{episode.duration}</span>
                          <span>{episode.date}</span>
                        </div>
                      </div>
                      <p className="text-sm text-gray-600 line-clamp-2">
                        {episode.description}
                      </p>
                    </div>
                    <button 
                      onClick={() => {
                        // 플레이어 페이지로 이동
                        window.open(`/player?episodeId=${episode.id}&animeId=${anime?.id}`, '_blank');
                      }}
                      className="flex-shrink-0 px-3 py-1 bg-purple-600 text-white text-xs rounded hover:bg-purple-700 transition-colors"
                    >
                      재생
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 리뷰 탭: ReviewList 항상 마운트되도록 렌더링, 탭 아닐 때는 hidden 처리 */}
          <div className={activeTab === 'reviews' ? '' : 'hidden'}>
            {anime?.aniId ? (
              <ReviewList key={anime.aniId} animeId={anime.aniId} />
            ) : (
              <div className="text-center py-12 text-red-500">
                <p>⚠️ 애니메이션 ID를 찾을 수 없습니다.</p>
                <p className="text-sm text-gray-500 mt-2">
                  anime 객체: {JSON.stringify(anime, null, 2)}
                </p>
              </div>
            )}
          </div>

          {activeTab === 'shop' && (
            <div className="text-center py-12 text-gray-500">
              상점 기능은 준비 중입니다
            </div>
          )}

          {activeTab === 'similar' && (
            <div className="text-center py-12 text-gray-500">
              비슷한 작품 기능은 준비 중입니다
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
