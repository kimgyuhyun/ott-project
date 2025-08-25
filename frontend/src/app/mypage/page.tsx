"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { getUserProfile, getUserWatchHistory, getUserFavorites, getUserStats } from "@/lib/api/user";

type TabType = 'recent' | 'want' | 'purchased' | 'binge';

/**
 * 마이페이지
 * 프로필 정보, 활동 통계, 보관함 탭 포함
 */
export default function MyPage() {
  const [activeTab, setActiveTab] = useState<TabType>('recent');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  const [userProfile, setUserProfile] = useState<any>(null);
  const [watchHistory, setWatchHistory] = useState<any[]>([]);
  const [favorites, setFavorites] = useState<any[]>([]);
  const [userStats, setUserStats] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const tabs = [
    { id: 'recent' as TabType, label: '최근 본', count: 0 },
    { id: 'want' as TabType, label: '보고싶다', count: 0 },
    { id: 'purchased' as TabType, label: '구매한', count: 0 },
    { id: 'binge' as TabType, label: '정주행', count: 0 }
  ];

  // 사용자 데이터 로드
  useEffect(() => {
    const loadUserData = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        // 병렬로 여러 API 호출
        const [profileData, historyData, favoritesData, statsData] = await Promise.all([
          getUserProfile(),
          getUserWatchHistory(),
          getUserFavorites(),
          getUserStats()
        ]);
        
        setUserProfile(profileData);
        setWatchHistory((historyData as any).content || historyData || []);
        setFavorites((favoritesData as any).content || favoritesData || []);
        setUserStats(statsData);
        
        // 탭별 카운트 업데이트
        tabs[0].count = (historyData as any).content?.length || 0;
        tabs[1].count = (favoritesData as any).content?.length || 0;
        tabs[2].count = 0; // 구매한 작품은 별도 API 필요
        tabs[3].count = 0; // 정주행은 별도 API 필요
        
      } catch (err) {
        console.error('사용자 데이터 로드 실패:', err);
        setError('사용자 데이터를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    loadUserData();
  }, []);

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-red-600">{error}</div>
      </div>
    );
  }

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
                  {/* 프로필 이미지 */}
                  {userProfile?.profileImage ? (
                    <img 
                      src={userProfile.profileImage} 
                      alt="프로필 이미지"
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <span className="text-white text-2xl">😈</span>
                  )}
                </div>
                <h3 className="text-xl font-bold text-gray-800 mb-1">
                  {userProfile?.username || '사용자'}
                </h3>
                <p className="text-green-500 font-medium">Lv.0 베이비</p>
              </div>

              {/* 프로필 선택 버튼 */}
              <button className="w-full py-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg text-sm transition-colors mb-6">
                프로필 선택
              </button>

              {/* 활동 통계 - 가로 일렬 배치로 변경 */}
              <div className="flex justify-between items-center mb-6">
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">
                    {userStats?.ratingCount || 0}
                  </div>
                  <div className="text-gray-600 text-xs">별점</div>
                </div>
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">
                    {userStats?.reviewCount || 0}
                  </div>
                  <div className="text-gray-600 text-xs">리뷰</div>
                </div>
                <div className="text-center">
                  <div className="text-gray-800 font-semibold text-lg">
                    {userStats?.commentCount || 0}
                  </div>
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
            {/* 탭 메뉴 */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
              <div className="flex border-b border-gray-200">
                {tabs.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`flex-1 px-6 py-4 text-sm font-medium transition-colors ${
                      activeTab === tab.id
                        ? 'text-purple-600 border-b-2 border-purple-600'
                        : 'text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    {tab.label}
                    {tab.count > 0 && (
                      <span className="ml-2 bg-gray-200 text-gray-600 px-2 py-1 rounded-full text-xs">
                        {tab.count}
                      </span>
                    )}
                  </button>
                ))}
              </div>

              {/* 탭별 콘텐츠 */}
              <div className="p-6">
                {activeTab === 'recent' && (
                  <div>
                    <h3 className="text-lg font-semibold text-gray-800 mb-4">최근 본 작품</h3>
                    {watchHistory.length > 0 ? (
                      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                        {watchHistory.map((anime: any) => (
                          <div 
                            key={anime.id} 
                            className="cursor-pointer hover:scale-105 transition-transform"
                            onClick={() => handleAnimeClick(anime)}
                          >
                            <div className="w-full aspect-[3/4] bg-gray-200 rounded-lg mb-2"></div>
                            <p className="text-sm text-gray-800 truncate">{anime.title}</p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-8 text-gray-500">
                        최근 본 작품이 없습니다
                      </div>
                    )}
                  </div>
                )}

                {activeTab === 'want' && (
                  <div>
                    <h3 className="text-lg font-semibold text-gray-800 mb-4">보고싶은 작품</h3>
                    {favorites.length > 0 ? (
                      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                        {favorites.map((anime: any) => (
                          <div 
                            key={anime.id} 
                            className="cursor-pointer hover:scale-105 transition-transform"
                            onClick={() => handleAnimeClick(anime)}
                          >
                            <div className="w-full aspect-[3/4] bg-gray-200 rounded-lg mb-2"></div>
                            <p className="text-sm text-gray-800 truncate">{anime.title}</p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-8 text-gray-500">
                        보고싶은 작품이 없습니다
                      </div>
                    )}
                  </div>
                )}

                {activeTab === 'purchased' && (
                  <div className="text-center py-8 text-gray-500">
                    구매한 작품이 없습니다
                  </div>
                )}

                {activeTab === 'binge' && (
                  <div className="text-center py-8 text-gray-500">
                    정주행 중인 작품이 없습니다
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* 애니메이션 상세 모달 */}
      {isModalOpen && selectedAnime && (
        <AnimeDetailModal
          anime={selectedAnime}
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </div>
  );
}
