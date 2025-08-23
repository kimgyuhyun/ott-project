"use client";
import { useState } from "react";
import Header from "@/components/layout/Header";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";

type TabType = 'recent' | 'want' | 'purchased' | 'binge';

/**
 * 마이페이지
 * 프로필 정보, 활동 통계, 보관함 탭 포함
 */
export default function MyPage() {
  const [activeTab, setActiveTab] = useState<TabType>('recent');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);

  const tabs = [
    { id: 'recent' as TabType, label: '최근 본', count: 0 },
    { id: 'want' as TabType, label: '보고싶다', count: 0 },
    { id: 'purchased' as TabType, label: '구매한', count: 0 },
    { id: 'binge' as TabType, label: '정주행', count: 0 }
  ];

  // 애니 작품 데이터 (예시)
  const recentAnimes = []; // 빈 배열 (최근 본 작품 없음)

  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-6xl mx-auto flex gap-8 px-6 py-8">
          {/* 왼쪽 사이드바 - 프로필 정보 */}
          <div className="w-72 flex-shrink-0">
            {/* 프로필 정보 섹션 */}
            <div className="bg-white rounded-lg p-6 mb-6 shadow-sm border border-gray-200">
              <h2 className="text-lg font-semibold text-gray-800 mb-4">프로필 정보</h2>
              
              {/* 프로필 이미지 및 기본 정보 */}
              <div className="text-center mb-6">
                <div className="w-20 h-20 mx-auto mb-4 rounded-full overflow-hidden bg-purple-500 flex items-center justify-center">
                  {/* 귀여운 악마 캐릭터 이미지 (플레이스홀더) */}
                  <div className="w-full h-full bg-purple-500 flex items-center justify-center">
                    <span className="text-white text-2xl">😈</span>
                  </div>
                </div>
                <h3 className="text-xl font-bold text-gray-800 mb-1">김규현</h3>
                <p className="text-green-500 font-medium">Lv.0 베이비</p>
              </div>

              {/* 프로필 선택 버튼 */}
              <button className="w-full py-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg text-sm transition-colors mb-6">
                프로필 선택
              </button>

              {/* 활동 통계 - 가로 일렬 배치로 변경 */}
              <div className="flex justify-between items-center mb-6">
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">0</div>
                  <div className="text-gray-600 text-xs">별점</div>
                </div>
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">0</div>
                  <div className="text-gray-600 text-xs">리뷰</div>
                </div>
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">0</div>
                  <div className="text-gray-600 text-xs">댓글</div>
                </div>
              </div>

              {/* 보관함 버튼 */}
              <button className="w-full py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg text-sm transition-colors flex items-center justify-center space-x-2">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span>보관함</span>
              </button>
            </div>

            {/* 멤버십 시작하기 배너 - 크기 줄임 */}
            <div className="bg-gradient-to-r from-purple-500 to-purple-600 rounded-lg p-4 text-center">
              <div className="w-10 h-10 mx-auto mb-2 bg-white/20 rounded-full flex items-center justify-center">
                {/* 귀여운 캐릭터 이미지 (플레이스홀더) */}
                <span className="text-white text-sm">🌟</span>
              </div>
              <h3 className="text-base font-bold text-white mb-1">멤버십 시작하기</h3>
              <p className="text-white/90 text-xs leading-relaxed">
                한일 동시방영 신작부터<br />
                역대 인기애니까지 무제한
              </p>
            </div>
          </div>

          {/* 오른쪽 메인 콘텐츠 - 보관함 */}
          <div className="flex-1">
            <div className="bg-white rounded-lg p-6 shadow-sm border border-gray-200">
              {/* 보관함 헤더 */}
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-2xl font-bold text-gray-800">보관함</h2>
                <button className="flex items-center space-x-2 px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm transition-colors">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                  <span>삭제</span>
                </button>
              </div>

              {/* 탭 메뉴 */}
              <div className="border-b border-gray-200 mb-4">
                <div className="flex space-x-8">
                  {tabs.map((tab) => (
                    <button
                      key={tab.id}
                      onClick={() => setActiveTab(tab.id)}
                      className={`flex items-center space-x-2 py-3 px-1 border-b-2 transition-colors ${
                        activeTab === tab.id
                          ? 'border-purple-500 text-gray-800 font-semibold'
                          : 'border-transparent text-gray-500 hover:text-gray-700'
                      }`}
                    >
                      <span className="text-sm">{tab.label}</span>
                      <span className="text-sm text-gray-500">({tab.count})</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* 작품 수 표시 */}
              <div className="text-gray-600 text-sm mb-6">작품 (0)</div>

              {/* 탭 콘텐츠 */}
              <div className="min-h-[400px]">
                {activeTab === 'recent' && (
                  <div className="text-center py-16">
                    {/* 빈 상태 이미지 */}
                    <div className="w-32 h-32 mx-auto mb-6 bg-gray-200 rounded-full flex items-center justify-center">
                      <span className="text-gray-400 text-6xl">😴</span>
                    </div>
                    <p className="text-gray-500 text-lg">
                      최근 본 작품이 아직 없어요.
                    </p>
                  </div>
                )}

                {activeTab === 'want' && (
                  <div className="text-center py-16">
                    <div className="w-32 h-32 mx-auto mb-6 bg-gray-200 rounded-full flex items-center justify-center">
                      <span className="text-gray-400 text-6xl">💭</span>
                    </div>
                    <p className="text-gray-500 text-lg">
                      보고싶은 작품이 아직 없어요.
                    </p>
                  </div>
                )}

                {activeTab === 'purchased' && (
                  <div className="text-center py-16">
                    <div className="w-32 h-32 mx-auto mb-6 bg-gray-200 rounded-full flex items-center justify-center">
                      <span className="text-gray-400 text-6xl">🛒</span>
                    </div>
                    <p className="text-gray-500 text-lg">
                      구매한 작품이 아직 없어요.
                    </p>
                  </div>
                )}

                {activeTab === 'binge' && (
                  <div className="text-center py-16">
                    <div className="w-32 h-32 mx-auto mb-6 bg-gray-200 rounded-full flex items-center justify-center">
                      <span className="text-gray-400 text-6xl">📺</span>
                    </div>
                    <p className="text-gray-500 text-lg">
                      정주행 중인 작품이 아직 없어요.
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* 애니 상세 모달 */}
      {selectedAnime && (
        <AnimeDetailModal 
          isOpen={isModalOpen} 
          onClose={() => {
            setIsModalOpen(false);
            setSelectedAnime(null);
          }} 
        />
      )}
    </div>
  );
}
