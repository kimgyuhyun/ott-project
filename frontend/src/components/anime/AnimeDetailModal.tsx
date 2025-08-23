"use client";
import { useState } from "react";

interface AnimeDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 애니메이션 상세 정보 모달
 * 평점, 제목, 장르, 액션 버튼, 시놉시스, 탭 메뉴, 에피소드 목록 포함
 */
export default function AnimeDetailModal({ isOpen, onClose }: AnimeDetailModalProps) {
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');

  if (!isOpen) return null;

  const tabs = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: 697 },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = [
    {
      id: 1,
      title: "1화 2016년의 너에게",
      duration: "37분",
      date: "2025.07.18",
      thumbnail: "https://via.placeholder.com/120x80/ff69b4/ffffff?text=Episode+1",
      description: "이건 깜짝 파티에도 쓰는 도구인데피....",
      fullDescription: "행복을 전파하기 위해 지구에 내려온 해피성인의 타코피는 인간 여자아이인 시즈카와 만났다. 위기인 가운데 시즈카가 자신을 구해주자, 타코피는 그녀의 미소를 되찾기 위해 수수께끼의 힘을 가진 해피 도구를 사용해 분주히 움직였다...."
    },
    {
      id: 2,
      title: "2화 타코피의 구제",
      duration: "21분",
      date: "2025.07.25",
      thumbnail: "https://via.placeholder.com/120x80/ff69b4/ffffff?text=Episode+2",
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
                     backgroundImage: 'url("https://via.placeholder.com/800x400/ff69b4/ffffff?text=Anime+Character")'
                   }}>
              </div>
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div className="absolute top-1/2 right-6 transform -translate-y-1/2 z-10">
            <div className="w-24 h-32 bg-white rounded-lg overflow-hidden shadow-lg border-2 border-purple-200">
              <img 
                src="https://via.placeholder.com/96x128/ff69b4/ffffff?text=LAFTEL+ONLY" 
                alt="타코피의 원죄 포스터"
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
                <span className="font-semibold text-lg text-gray-800">4.7</span>
              </div>
              <span className="px-2 py-1 bg-red-500 text-white text-xs font-bold rounded">
                ONLY
              </span>
            </div>

            {/* 제목 */}
            <h1 className="text-4xl md:text-5xl font-bold mb-3 text-gray-900">
              타코피의 원죄
            </h1>

            {/* 장르 및 등급 */}
            <p className="text-gray-600 mb-8 text-lg">
              드라마·SF | TVA·완결 19
            </p>

            {/* 중앙 일러스트 영역 */}
            <div className="relative mb-8">
              <div className="w-full h-64 bg-gradient-to-br from-purple-400 to-pink-400 rounded-lg flex items-center justify-center shadow-lg">
                {/* 애니 캐릭터 일러스트 (플레이스홀더) */}
                <div className="text-center text-white">
                  <div className="text-6xl mb-2">🎭</div>
                  <p className="text-lg">타코피와 시즈카</p>
                </div>
              </div>
              
              {/* 1화 재생하기 버튼 - 일러스트 위에 오버레이 */}
              <div className="absolute inset-0 flex items-center justify-center">
                <button className="flex items-center space-x-3 px-8 py-4 bg-red-500 hover:bg-red-600 text-white font-bold text-xl rounded-xl transition-colors shadow-2xl">
                  <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                  </svg>
                  <span>1화 재생하기</span>
                </button>
              </div>
            </div>

            {/* 액션 버튼들 */}
            <div className="flex flex-wrap items-center gap-4 mb-6">
              {/* 보고싶다 */}
              <button className="flex items-center space-x-2 px-6 py-3 bg-purple-100 hover:bg-purple-200 text-purple-700 font-semibold rounded-lg transition-colors border border-purple-200">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                <span>+ 보고싶다</span>
              </button>

              {/* 에피소드 구매 */}
              <button className="flex items-center space-x-2 px-6 py-3 bg-blue-100 hover:bg-blue-200 text-blue-700 font-semibold rounded-lg transition-colors border border-blue-200">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
                <span>에피소드 구매</span>
              </button>
            </div>
          </div>
        </div>

        {/* 시놉시스 */}
        <div className="px-6 py-6 border-b border-gray-200">
          <p className="text-gray-700 text-base leading-relaxed">
            행복을 전파하기 위해 지구에 내려온 해피성인의 타코피는 인간 여자아이인 시즈카와 만났다. 위기인 가운데 시즈카가 자신을 구해주자, 타코피는 그녀의 미소를 되찾기 위해 수수께끼의 힘을 가진 해피 도구를 사용해 분주히 움직였다....
            <button className="text-purple-600 hover:text-purple-700 ml-2 font-medium">
              더 보기
            </button>
          </p>
        </div>

        {/* 탭 메뉴 */}
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex space-x-8">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center space-x-2 py-2 px-1 border-b-2 transition-colors ${
                  activeTab === tab.id
                    ? 'border-purple-500 text-purple-600 font-semibold'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                <span>{tab.label}</span>
                {tab.count && (
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
              {/* 에피소드 섹션 헤더 */}
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-xl font-bold text-gray-800">타코피의 원죄</h3>
                <div className="flex items-center space-x-2 text-gray-500 text-sm">
                  <span>1화부터</span>
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
                  </svg>
                </div>
              </div>

              {/* 에피소드 목록 */}
              <div className="space-y-4">
                {episodes.map((episode) => (
                  <div key={episode.id} className="flex space-x-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
                    {/* 썸네일 */}
                    <div className="relative">
                      <img 
                        src={episode.thumbnail} 
                        alt={episode.title}
                        className="w-30 h-20 object-cover rounded"
                      />
                      <div className="absolute top-1 left-1">
                        <span className="px-2 py-1 bg-purple-500 text-white text-xs font-bold rounded">
                          멤버십
                        </span>
                      </div>
                    </div>

                    {/* 에피소드 정보 */}
                    <div className="flex-1">
                      <h4 className="text-gray-800 font-semibold mb-1">{episode.title}</h4>
                      <p className="text-gray-600 text-sm mb-2">
                        {episode.duration} · {episode.date}
                      </p>
                      <p className="text-gray-700 text-sm mb-2">
                        {episode.description}
                      </p>
                      <p className="text-gray-600 text-sm line-clamp-2">
                        {episode.fullDescription}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {activeTab === 'reviews' && (
            <div className="text-center py-8">
              <p className="text-gray-500">사용자 평 기능 준비 중입니다.</p>
            </div>
          )}

          {activeTab === 'shop' && (
            <div className="text-center py-8">
              <p className="text-gray-500">상점 기능 준비 중입니다.</p>
            </div>
          )}

          {activeTab === 'similar' && (
            <div className="text-center py-8">
              <p className="text-gray-500">비슷한 작품 기능 준비 중입니다.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
