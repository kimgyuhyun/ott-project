"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { getUserProfile, getUserWatchHistory, getUserWantList, getUserStats, getUserRecentAnime } from "@/lib/api/user";
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
  const [isDeleteMode, setIsDeleteMode] = useState(false);
  const [selectedAnimeIds, setSelectedAnimeIds] = useState<Set<number>>(new Set());

  // 멤버십 상태
  const { userMembership, isLoading: isLoadingMembership } = useMembershipData();
  const getKoreanPlanName = (raw?: string | null) => {
    const s = String(raw ?? '').toLowerCase();
    if (s.includes('premium')) return '프리미엄';
    if (s.includes('basic')) return '베이직';
    return raw ?? '';
  };

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
          getUserProfile().catch(e => { if ((e as any)?.status === 401) return null; throw e; }),
          getUserRecentAnime().catch(e => { if ((e as any)?.status === 401) return { items: [] }; throw e; }),
          getUserWantList().catch(e => { if ((e as any)?.status === 401) return { items: [] }; throw e; }),
          getUserStats().catch(e => { if ((e as any)?.status === 401) return null; throw e; })
        ]);
        
        console.log('🔍 마이페이지 데이터 로드 결과:');
        console.log('프로필:', profileData);
        console.log('시청 기록:', historyData);
        console.log('보고싶다 목록:', wantListData);
        console.log('통계:', statsData);
        
        setUserProfile(profileData);
        
        // 시청 기록에 애니메이션 제목 추가
        const watchHistoryList = ((historyData as any)?.items as any[]) || (Array.isArray(historyData) ? historyData : []) || [];
        
        // 시청 기록 상세 로그
        console.log('🔍 시청 기록 상세:', watchHistoryList.map((item: any) => ({
          animeId: item.animeId,
          episodeNumber: item.episodeNumber,
          episodeId: item.episodeId,
          positionSec: item.positionSec,
          updatedAt: item.updatedAt
        })));
        const enrichedWatchHistory = await Promise.all(
          watchHistoryList.map(async (item: any) => {
            try {
              const { getAnimeDetail } = await import('@/lib/api/anime');
              const animeDetail = await getAnimeDetail(item.animeId);
              return {
                ...item,
                aniId: item.animeId,
                title: (animeDetail as any)?.title || '제목 없음',
                posterUrl: (animeDetail as any)?.posterUrl,
                episodeNumber: item.episodeNumber // 에피소드 번호 추가
              };
            } catch (e) {
              console.warn('애니메이션 상세 조회 실패:', e);
              return {
                ...item,
                aniId: item.animeId,
                title: '제목 없음',
                posterUrl: null,
                episodeNumber: item.episodeNumber // 에피소드 번호 추가
              };
            }
          })
        );
        
        setWatchHistory(enrichedWatchHistory);
        
        // 보고싶다 목록에 애니메이션 상세 정보 추가
        const wantListItems = ((wantListData as any)?.items as any[]) || (Array.isArray(wantListData) ? wantListData : []) || [];
        const enrichedWantList = await Promise.all(
          wantListItems.map(async (item: any) => {
            try {
              const { getAnimeDetail } = await import('@/lib/api/anime');
              const animeDetail = await getAnimeDetail(item.animeId || item.id);
              return {
                ...item,
                aniId: item.animeId || item.id,
                title: (animeDetail as any)?.title || item.title || '제목 없음',
                posterUrl: (animeDetail as any)?.posterUrl || item.posterUrl
              };
            } catch (e) {
              console.warn('애니메이션 상세 조회 실패:', e);
              return {
                ...item,
                aniId: item.animeId || item.id,
                title: item.title || '제목 없음',
                posterUrl: item.posterUrl || null
              };
            }
          })
        );
        
        setWantList(enrichedWantList);
        setUserStats(statsData);
        
        // 탭별 카운트 업데이트
        tabs[0].count = enrichedWatchHistory.length;
        tabs[1].count = enrichedWantList.length;
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

  // 삭제 모드 토글
  const toggleDeleteMode = () => {
    setIsDeleteMode(!isDeleteMode);
    setSelectedAnimeIds(new Set());
  };

  // 애니메이션 선택/해제
  const toggleAnimeSelection = (animeId: number) => {
    setSelectedAnimeIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(animeId)) {
        newSet.delete(animeId);
      } else {
        newSet.add(animeId);
      }
      return newSet;
    });
  };

  // 전체선택/해제
  const handleSelectAll = () => {
    if (activeTab === 'recent') {
      if (selectedAnimeIds.size === watchHistory.length) {
        setSelectedAnimeIds(new Set());
      } else {
        const allIds = new Set(watchHistory.map(anime => anime.aniId));
        setSelectedAnimeIds(allIds);
      }
    } else if (activeTab === 'want') {
      if (selectedAnimeIds.size === wantList.length) {
        setSelectedAnimeIds(new Set());
      } else {
        const allIds = new Set(wantList.map(anime => anime.aniId));
        setSelectedAnimeIds(allIds);
      }
    }
  };

  // 선택된 애니메이션들 삭제
  const deleteSelectedAnime = () => {
    if (selectedAnimeIds.size === 0) return;
    
    if (activeTab === 'recent') {
      setWatchHistory(prev => prev.filter(anime => !selectedAnimeIds.has(anime.aniId)));
    } else if (activeTab === 'want') {
      setWantList(prev => prev.filter(anime => !selectedAnimeIds.has(anime.aniId)));
    }
    
    setIsDeleteMode(false);
    setSelectedAnimeIds(new Set());
  };

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = async (anime: any) => {
    const aniId = anime?.aniId ?? anime?.id ?? anime?.animeId;
    
    // 삭제 모드일 때는 선택/해제만
    if (isDeleteMode) {
      toggleAnimeSelection(aniId);
      return;
    }
    
    // 일반 모드일 때는 플레이어로 이동
    const episodeId = anime?.episodeId;
    const position = typeof anime?.positionSec === 'number' && anime.positionSec > 0 ? anime.positionSec : 0;

    if (aniId && episodeId) {
      const posQuery = position > 0 ? `&position=${position}` : '';
      window.location.href = `/player?episodeId=${episodeId}&animeId=${aniId}${posQuery}`;
      return;
    }

    // 폴백: 상세 모달 표시
    try {
      const id = aniId;
      if (id) {
        const { getAnimeDetail } = await import('@/lib/api/anime');
        const detail = await getAnimeDetail(id);
        setSelectedAnime(detail);
      } else {
        setSelectedAnime(anime);
      }
    } catch (e) {
      console.warn('상세 조회 실패, 목록 데이터로 대체합니다.', e);
      setSelectedAnime(anime);
    } finally {
      setIsModalOpen(true);
    }
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
    <>
      <div className={styles.mypageContainer}>
        <Header />
        
        <main className={styles.mainContent}>
          <div className={styles.contentWrapper}>
            {/* 왼쪽 사이드바 - 프로필 정보 */}
            <div className={styles.sidebar}>
              {/* 프로필 정보 섹션 */}
              <div className={styles.profileSection}>
                <h2 className={styles.profileTitle}>프로필 정보</h2>
                
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
                <button className={styles.profileSelectButton}>
                  프로필 선택
                </button>

                {/* 활동 통계 - 가로 일렬 배치로 변경 */}
                <div className={styles.statsContainer}>
                  <div className={styles.statItem}>
                    <div className={styles.statNumber}>
                      {userStats?.wantCount || 0}
                    </div>
                    <div className={styles.statLabel}>보고싶다</div>
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

              {/* 멤버십 배너 - 구독 여부에 따라 문구 분기 */}
              {!isLoadingMembership && (
                <div className={styles.membershipBanner}>
                  <div className={styles.bannerIcon}>
                    <span className={styles.bannerIconText}>🌟</span>
                  </div>
                  {userMembership ? (
                    <>
                      <h3 className={styles.bannerTitle}>
                        {getKoreanPlanName(userMembership.planName || userMembership.planCode)} 멤버십 이용중입니다.
                      </h3>
                    </>
                  ) : (
                    <>
                      <h3 className={styles.bannerTitle}>멤버십 시작하기</h3>
                      <p className={styles.bannerText}>
                        한일 동시방영 신작부터<br />
                        역대 인기애니까지 무제한
                      </p>
                    </>
                  )}
                </div>
              )}
            </div>

            {/* 오른쪽 메인 콘텐츠 - 보관함 */}
            <div className={styles.mainContentArea}>
              {/* 탭 메뉴 */}
              <div className={styles.tabContainer}>
                <div className={styles.tabMenu}>
                  <div className={styles.tabButtons}>
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
                  
                  {/* 삭제 버튼 - 애니메이션이 있을 때만 표시 */}
                  {activeTab === 'recent' && watchHistory.length > 0 && (
                    <div className={styles.deleteButtonGroup}>
                      {!isDeleteMode ? (
                        <button 
                          className={styles.deleteButton}
                          onClick={toggleDeleteMode}
                        >
                          <svg className={styles.deleteIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                          <span>삭제</span>
                        </button>
                      ) : (
                        <>
                          <button 
                            className={styles.cancelButton}
                            onClick={toggleDeleteMode}
                          >
                            <span>취소</span>
                          </button>
                          <button 
                            className={styles.confirmDeleteButton}
                            onClick={deleteSelectedAnime}
                            disabled={selectedAnimeIds.size === 0}
                          >
                            <span>삭제 ({selectedAnimeIds.size})</span>
                          </button>
                        </>
                      )}
                    </div>
                  )}
                  
                  {activeTab === 'want' && wantList.length > 0 && (
                    <div className={styles.deleteButtonGroup}>
                      {!isDeleteMode ? (
                        <button 
                          className={styles.deleteButton}
                          onClick={toggleDeleteMode}
                        >
                          <svg className={styles.deleteIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                          <span>삭제</span>
                        </button>
                      ) : (
                        <>
                          <button 
                            className={styles.cancelButton}
                            onClick={toggleDeleteMode}
                          >
                            <span>취소</span>
                          </button>
                          <button 
                            className={styles.confirmDeleteButton}
                            onClick={deleteSelectedAnime}
                            disabled={selectedAnimeIds.size === 0}
                          >
                            <span>삭제 ({selectedAnimeIds.size})</span>
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </div>

                {/* 탭별 콘텐츠 */}
                <div className={styles.tabContent}>
                  {activeTab === 'recent' && (
                    <div>
                      {!isDeleteMode ? (
                        <h3 className={styles.tabTitle}>작품 ({watchHistory.length})</h3>
                      ) : (
                        <div className={styles.selectAllContainer}>
                          <input 
                            type="checkbox" 
                            checked={selectedAnimeIds.size === watchHistory.length && watchHistory.length > 0}
                            onChange={handleSelectAll}
                            className={styles.selectAllCheckbox}
                          />
                          <label className={styles.selectAllLabel}>
                            전체선택 ({selectedAnimeIds.size})
                          </label>
                        </div>
                      )}
                      {Array.isArray(watchHistory) && watchHistory.length > 0 ? (
                        <div className={styles.animeGrid}>
                          {watchHistory.map((anime: any, idx: number) => {
                            const aniId = anime?.aniId ?? anime?.id ?? anime?.animeId;
                            const isSelected = selectedAnimeIds.has(aniId);
                            
                            return (
                              <div 
                                key={`${aniId ?? 'item'}-${idx}`}
                                className={`${styles.animeItem} ${isDeleteMode ? styles.selectable : ''} ${isSelected ? styles.selected : ''}`}
                                onClick={() => handleAnimeClick(anime)}
                              >
                                {isDeleteMode && isSelected && (
                                  <div className={styles.selectionIndicator}>
                                    ✓
                                  </div>
                                )}
                                <div 
                                  className={styles.animePoster}
                                  style={{
                                    backgroundImage: anime.posterUrl ? `url(${anime.posterUrl})` : 'none',
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center',
                                    backgroundColor: anime.posterUrl ? 'transparent' : '#323232'
                                  }}
                                ></div>
                                <p className={styles.animeTitle}>{anime.title}</p>
                                {anime.episodeNumber && (
                                  <p className={styles.animeEpisode}>{anime.episodeNumber}화</p>
                                )}
                              </div>
                            );
                          })}
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
                      {!isDeleteMode ? (
                        <h3 className={styles.tabTitle}>작품 ({wantList.length})</h3>
                      ) : (
                        <div className={styles.selectAllContainer}>
                          <input 
                            type="checkbox" 
                            checked={selectedAnimeIds.size === wantList.length && wantList.length > 0}
                            onChange={handleSelectAll}
                            className={styles.selectAllCheckbox}
                          />
                          <label className={styles.selectAllLabel}>
                            전체선택 ({selectedAnimeIds.size})
                          </label>
                        </div>
                      )}
                      {Array.isArray(wantList) && wantList.length > 0 ? (
                        <div className={styles.animeGrid}>
                          {wantList.map((anime: any, idx: number) => {
                            const aniId = anime?.aniId ?? anime?.id ?? anime?.animeId;
                            const isSelected = selectedAnimeIds.has(aniId);
                            
                            return (
                              <div 
                                key={`${aniId ?? 'item'}-${idx}`}
                                className={`${styles.animeItem} ${isDeleteMode ? styles.selectable : ''} ${isSelected ? styles.selected : ''}`}
                                onClick={() => handleAnimeClick(anime)}
                              >
                                {isDeleteMode && isSelected && (
                                  <div className={styles.selectionIndicator}>
                                    ✓
                                  </div>
                                )}
                                <div 
                                  className={styles.animePoster}
                                  style={{
                                    backgroundImage: anime.posterUrl ? `url(${anime.posterUrl})` : 'none',
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center',
                                    backgroundColor: anime.posterUrl ? 'transparent' : '#323232'
                                  }}
                                ></div>
                                <p className={styles.animeTitle}>{anime.title}</p>
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        <div className={styles.emptyState}>
                          보고싶은 작품이 없습니다
                        </div>
                      )}
                    </div>
                  )}

                  {activeTab === 'purchased' && (
                    <div>
                      <h3 className={styles.tabTitle}>작품 (0)</h3>
                      <div className={styles.emptyState}>
                        구매한 작품이 없습니다
                      </div>
                    </div>
                  )}

                  {activeTab === 'binge' && (
                    <div>
                      <h3 className={styles.tabTitle}>작품 (0)</h3>
                      <div className={styles.emptyState}>
                        정주행 중인 작품이 없습니다
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>

      {/* 애니메이션 상세 모달 - mypageContainer 밖으로 이동 */}
      {isModalOpen && selectedAnime && (
        <AnimeDetailModal
          anime={selectedAnime}
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </>
  );
}