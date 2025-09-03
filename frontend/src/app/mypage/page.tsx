"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { getUserProfile, getUserWatchHistory, getUserWantList, getUserStats } from "@/lib/api/user";
import styles from "./mypage.module.css";

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
  const [wantList, setWantList] = useState<any[]>([]);
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
        const [profileData, historyData, wantListData, statsData] = await Promise.all([
          getUserProfile(),
          getUserWatchHistory(),
          getUserWantList(),
          getUserStats()
        ]);
        
        setUserProfile(profileData);
        setWatchHistory((historyData as any).content || historyData || []);
        setWantList((wantListData as any).content || wantListData || []);
        setUserStats(statsData);
        
        // 탭별 카운트 업데이트
        tabs[0].count = (historyData as any).content?.length || 0;
        tabs[1].count = (wantListData as any).content?.length || 0;
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
      <div className={styles.mypageContainer}>
        <div className={styles.loadingContainer}>
          <div className={styles.loadingText}>로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.mypageContainer}>
        <div className={styles.errorContainer}>
          <div className={styles.errorText}>{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.mypageContainer}>
      <Header />
      
      <main className={styles.mainContent} style={{ paddingTop: '4rem' }}>
        <div className={styles.contentWrapper} style={{ maxWidth: '80rem', margin: '0 auto', display: 'flex', gap: '1.5rem', padding: '0 1rem', paddingTop: '1.5rem', paddingBottom: '1.5rem' }}>
          {/* 왼쪽 사이드바 - 프로필 정보 */}
          <div className={styles.sidebar}>
            {/* 프로필 정보 섹션 */}
            <div className={styles.profileSection} style={{ backgroundColor: '#000000', borderRadius: '0.5rem', padding: '1.25rem', marginBottom: '1rem', boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.5)', border: '1px solid #323232' }}>
              <h2 className={styles.profileTitle} style={{ fontSize: '1rem', fontWeight: 600, color: '#F7F7F7', marginBottom: '0.75rem' }}>프로필 정보</h2>
              
              {/* 프로필 이미지 및 기본 정보 */}
              <div className={styles.profileInfo}>
                <div className={styles.profileImageContainer}>
                  {/* 프로필 이미지 */}
                  {userProfile?.profileImage ? (
                    <img 
                      src={userProfile.profileImage} 
                      alt="프로필 이미지"
                      className={styles.profileImage}
                    />
                  ) : (
                    <span className={styles.profileDefaultIcon}>😈</span>
                  )}
                </div>
                <h3 className={styles.profileName}>
                  {userProfile?.username || '사용자'}
                </h3>
                <p className={styles.profileLevel}>Lv.0 베이비</p>
              </div>

              {/* 프로필 선택 버튼 */}
              <button className={styles.profileSelectButton} style={{ marginBottom: '2rem' }}>
                프로필 선택
              </button>

              {/* 활동 통계 - 가로 일렬 배치로 변경 */}
              <div className={styles.statsContainer} style={{ marginBottom: '2rem', borderTop: 'none', borderBottom: 'none' }}>
                <div className={styles.statItem}>
                  <div className={styles.statNumber}>
                    {userStats?.ratingCount || 0}
                  </div>
                  <div className={styles.statLabel}>별점</div>
                </div>
                <div className={styles.statItem}>
                  <div className={styles.statNumber}>
                    {userStats?.reviewCount || 0}
                  </div>
                  <div className={styles.statLabel}>리뷰</div>
                </div>
                <div className={styles.statItem}>
                  <div className={styles.statNumber}>
                    {userStats?.commentCount || 0}
                  </div>
                  <div className={styles.statLabel}>댓글</div>
                </div>
              </div>

              {/* 보관함 버튼 */}
              <button className={styles.archiveButton}>
                <svg className={styles.archiveIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span>보관함</span>
              </button>
            </div>

            {/* 멤버십 시작하기 배너 - 크기 줄임 */}
            <div className={styles.membershipBanner}>
              <div className={styles.bannerIcon}>
                {/* 귀여운 캐릭터 이미지 (플레이스홀더) */}
                <span className={styles.bannerIconText}>🌟</span>
              </div>
              <h3 className={styles.bannerTitle}>멤버십 시작하기</h3>
              <p className={styles.bannerText}>
                한일 동시방영 신작부터<br />
                역대 인기애니까지 무제한
              </p>
            </div>
          </div>

          {/* 오른쪽 메인 콘텐츠 - 보관함 */}
          <div className={styles.mainContentArea} style={{ flex: 1, minWidth: 0 }}>
            {/* 탭 메뉴 */}
            <div className={styles.tabContainer}>
              <div className={styles.tabMenu}>
                {tabs.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`${styles.tabButton} ${
                      activeTab === tab.id ? styles.tabButtonActive : ''
                    }`}
                  >
                    {tab.label}
                    {tab.count > 0 && (
                      <span className={styles.tabCount}>
                        {tab.count}
                      </span>
                    )}
                  </button>
                ))}
              </div>

              {/* 탭별 콘텐츠 */}
              <div className={styles.tabContent} style={{ padding: '15rem 5rem' }}>
                {activeTab === 'recent' && (
                  <div>
                    {watchHistory.length > 0 ? (
                      <div className={styles.animeGrid}>
                        {watchHistory.map((anime: any) => (
                          <div 
                            key={anime.id} 
                            className={styles.animeItem}
                            onClick={() => handleAnimeClick(anime)}
                          >
                            <div className={styles.animePoster}></div>
                            <p className={styles.animeTitle}>{anime.title}</p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className={styles.emptyState}>
                        최근 본 작품이 없습니다
                      </div>
                    )}
                  </div>
                )}

                {activeTab === 'want' && (
                  <div>
                    <h3 className={styles.tabTitle}>보고싶은 작품</h3>
                    {wantList.length > 0 ? (
                      <div className={styles.animeGrid}>
                        {wantList.map((anime: any) => (
                          <div 
                            key={anime.id} 
                            className={styles.animeItem}
                            onClick={() => handleAnimeClick(anime)}
                          >
                            <div className={styles.animePoster}></div>
                            <p className={styles.animeTitle}>{anime.title}</p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className={styles.emptyState}>
                        보고싶은 작품이 없습니다
                      </div>
                    )}
                  </div>
                )}

                {activeTab === 'purchased' && (
                  <div className={styles.emptyState}>
                    구매한 작품이 없습니다
                  </div>
                )}

                {activeTab === 'binge' && (
                  <div className={styles.emptyState}>
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