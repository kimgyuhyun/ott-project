"use client";
import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Image from "next/image";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { getUserProfile, getUserWantList, getUserStats, getUserRecentAnime, getUserBingeList, hideFromRecent, removeFromWantList, deleteFromBinge, getMyRatings, getMyReviews, getMyComments } from "@/lib/api/user";
import { toggleReviewLike } from "@/lib/api/reviews";
import { toggleEpisodeCommentLike } from "@/lib/api/episodeComments";
import { toggleCommentLike as toggleReviewCommentLike } from "@/lib/api/comments";
import styles from "./mypage.module.css";

type TabType = 'recent' | 'want' | 'purchased' | 'binge';
type ViewMode = 'archive' | 'activity';
type ActivityTab = 'ratings' | 'reviews' | 'comments';

interface Anime {
  id: number;
  title: string;
  posterUrl: string;
  rating: number;
  status: string;
  type: string;
  year: number;
  genres: string[];
  studios: string[];
  tags: string[];
  synopsis: string;
  fullSynopsis: string;
  episodeCount: number;
  duration: number;
  ageRating: string;
  createdAt: string;
  updatedAt: string;
}

interface UserProfile {
  id: number;
  username: string;
  email: string;
  nickname: string;
  avatarUrl: string;
  profileImage?: string;
  createdAt: string;
  updatedAt: string;
}

interface WatchHistoryItem {
  id?: number;
  animeId: number; // RecentAnimeWatchDto에서 오는 필드
  anime?: Anime; // 기존 호환성을 위한 선택적 필드
  episodeId: number;
  episodeNumber: number;
  positionSec?: number; // RecentAnimeWatchDto에서 오는 필드
  durationSec?: number; // RecentAnimeWatchDto에서 오는 필드
  progress?: number;
  duration?: number;
  watchedAt?: string;
  updatedAt?: string; // RecentAnimeWatchDto에서 오는 필드
  aniId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
}

interface WantListItem {
  id: number;
  anime: Anime;
  addedAt: string;
  aniId?: number; // 추가된 속성
  animeId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
}

interface BingeListItem {
  id: number;
  anime: Anime;
  addedAt: string;
  aniId?: number; // 추가된 속성
  animeId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
}

interface Rating {
  id: number;
  anime: Anime;
  rating: number;
  createdAt: string;
  updatedAt: string;
  animeId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
  score?: number; // 추가된 속성
}

interface Review {
  id: number;
  anime: Anime;
  content: string;
  rating: number;
  likes: number;
  isLiked: boolean;
  createdAt: string;
  updatedAt: string;
  animeId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
  score?: number; // 추가된 속성
  reviewId?: number; // 추가된 속성
  likeCount?: number; // 추가된 속성
}

interface Comment {
  id: number;
  content: string;
  likes: number;
  isLiked: boolean;
  createdAt: string;
  updatedAt: string;
  anime?: Anime;
  episodeId?: number;
  episodeNumber?: number;
  commentId?: number; // 추가된 속성
  animeId?: number; // 추가된 속성
  title?: string; // 추가된 속성
  posterUrl?: string; // 추가된 속성
  likeCount?: number; // 추가된 속성
  userProfileImage?: string; // 추가된 속성
  tagLabel?: string; // 추가된 속성
  episodeTitle?: string; // 추가된 속성
  targetType?: string; // 추가된 속성
  targetId?: number; // 추가된 속성
  episodeThumbUrl?: string; // 추가된 속성
}

interface UserStats {
  totalWatchTime: number;
  totalEpisodesWatched: number;
  totalAnimeWatched: number;
  averageRating: number;
  totalReviews: number;
  totalComments: number;
  joinDate: string;
  lastActiveDate: string;
  ratingCount?: number; // 추가된 속성
  reviewCount?: number; // 추가된 속성
  commentCount?: number; // 추가된 속성
}

/**
 * 마이페이지
 * 프로필 정보, 활동 통계, 보관함 탭 포함
 */
function MyPageContent() {
  const searchParams = useSearchParams();
  
  const formatRelativeTime = (isoLike?: string) => {
    if (!isoLike) return '';
    const t = new Date(isoLike).getTime();
    if (isNaN(t)) return '';
    const diff = Date.now() - t;
    const sec = Math.floor(diff / 1000);
    if (sec < 60) return '방금 전';
    const min = Math.floor(sec / 60);
    if (min < 60) return `${min}분 전`;
    const hour = Math.floor(min / 60);
    if (hour < 24) return `${hour}시간 전`;
    const day = Math.floor(hour / 24);
    if (day < 30) return `${day}일 전`;
    const mon = Math.floor(day / 30);
    if (mon < 12) return mon === 1 ? '한달 전' : `${mon}개월 전`;
    const yr = Math.floor(mon / 12);
    return `${yr}년 전`;
  };
  const [activeTab, setActiveTab] = useState<TabType>('recent');
  const [viewMode, setViewMode] = useState<ViewMode>('archive');
  const [activityTab, setActivityTab] = useState<ActivityTab>('ratings');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<Anime | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [watchHistory, setWatchHistory] = useState<WatchHistoryItem[]>([]);
  const [wantList, setWantList] = useState<WantListItem[]>([]);
  const [bingeList, setBingeList] = useState<BingeListItem[]>([]);
  const [myRatings, setMyRatings] = useState<Rating[] | null>(null);
  const [myReviews, setMyReviews] = useState<Review[] | null>(null);
  const [myComments, setMyComments] = useState<Comment[] | null>(null);
  const [userStats, setUserStats] = useState<UserStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isDeleteMode, setIsDeleteMode] = useState(false);
  const [selectedAnimeIds, setSelectedAnimeIds] = useState<Set<number>>(new Set());
  const [likedReviews, setLikedReviews] = useState<Set<number>>(new Set());
  const [likedComments, setLikedComments] = useState<Set<string>>(new Set());
  const [commentSort, setCommentSort] = useState<'latest'|'likes'|'oldest'>('latest');

  // 멤버십 상태
  const { userMembership, isLoading: isLoadingMembership } = useMembershipData();
  
  // URL 파라미터 처리
  useEffect(() => {
    const tab = searchParams.get('tab');
    const activityTab = searchParams.get('activityTab');
    
    if (tab === 'activity') {
      setViewMode('activity');
      if (activityTab === 'ratings' || activityTab === 'reviews' || activityTab === 'comments') {
        setActivityTab(activityTab as ActivityTab);
      }
    }
  }, [searchParams]);
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
        const [profileData, historyData, wantListData, bingeListData, statsData] = await Promise.all([
          getUserProfile().catch(e => { if ((e as Error & { status?: number })?.status === 401) return null; throw e; }),
          getUserRecentAnime().catch(e => { if ((e as Error & { status?: number })?.status === 401) return { items: [] }; throw e; }),
          getUserWantList().catch(e => { if ((e as Error & { status?: number })?.status === 401) return { items: [] }; throw e; }),
          getUserBingeList().catch(e => { if ((e as Error & { status?: number })?.status === 401) return []; throw e; }),
          getUserStats().catch(e => { if ((e as Error & { status?: number })?.status === 401) return null; throw e; })
        ]);
        
        console.log('🔍 마이페이지 데이터 로드 결과:');
        console.log('프로필:', profileData);
        console.log('시청 기록:', historyData);
        console.log('보고싶다 목록:', wantListData);
        console.log('정주행 목록:', bingeListData);
        console.log('통계:', statsData);
        
        setUserProfile(profileData as UserProfile | null);
        
        // 시청 기록에 애니메이션 제목 추가
        const watchHistoryList = ((historyData as { items?: WatchHistoryItem[] })?.items) || (Array.isArray(historyData) ? historyData as WatchHistoryItem[] : []) || [];
        
        // 시청 기록 상세 로그
        console.log('🔍 시청 기록 상세:', watchHistoryList.map((item: WatchHistoryItem) => ({
          animeId: item.animeId || item.anime?.id,
          episodeNumber: item.episodeNumber,
          episodeId: item.episodeId,
          progress: item.progress,
          watchedAt: item.watchedAt || item.updatedAt
        })));
        const enrichedWatchHistory = await Promise.all(
          watchHistoryList.map(async (item: WatchHistoryItem) => {
            try {
              // 애니메이션 ID가 유효한지 먼저 확인 (animeId 우선, 없으면 anime.id 사용)
              const animeId = item.animeId || item.anime?.id;
              if (!animeId || animeId <= 0) {
                console.log(`유효하지 않은 애니메이션 ID: ${animeId}. 원본 데이터로 대체합니다.`);
                return {
                  ...item,
                  aniId: animeId,
                  title: item.anime?.title || '제목 없음',
                  posterUrl: item.anime?.posterUrl || undefined,
                  episodeNumber: item.episodeNumber
                };
              }

              const { getAnimeDetail } = await import('@/lib/api/anime');
              const animeDetail = await getAnimeDetail(animeId) as Anime;
              
              // animeDetail이 null이거나 유효하지 않은 경우 처리
              if (!animeDetail) {
                console.log(`애니메이션 상세 정보를 찾을 수 없습니다 (ID: ${animeId}). 원본 데이터로 대체합니다.`);
                return {
                  ...item,
                  aniId: animeId,
                  title: item.anime?.title || '제목 없음',
                  posterUrl: item.anime?.posterUrl || undefined,
                  episodeNumber: item.episodeNumber
                };
              }
              
              return {
                ...item,
                aniId: animeId,
                title: animeDetail.title || item.anime?.title || '제목 없음',
                posterUrl: animeDetail.posterUrl || item.anime?.posterUrl || undefined,
                episodeNumber: item.episodeNumber
              };
            } catch (e) {
              const animeId = item.animeId || item.anime?.id;
              console.log(`애니메이션 상세 조회 실패 (ID: ${animeId}):`, e instanceof Error ? e.message : 'Unknown error');
              // 원본 데이터에서 정보를 가져와서 fallback 처리
              return {
                ...item,
                aniId: animeId,
                title: item.anime?.title || '제목 없음',
                posterUrl: item.anime?.posterUrl || undefined,
                episodeNumber: item.episodeNumber
              };
            }
          })
        );
        
        setWatchHistory(enrichedWatchHistory);
        
        // 보고싶다 목록 - 백엔드에서 이미 필요한 정보 제공
        const wantListItems = ((wantListData as { items?: WantListItem[] })?.items) || (Array.isArray(wantListData) ? wantListData as WantListItem[] : []) || [];
        const enrichedWantList = wantListItems.map((item: WantListItem) => ({
          ...item,
          aniId: item.aniId || item.animeId || item.id,
          title: item.title || '제목 없음',
          posterUrl: item.posterUrl || undefined
        }));
        
        setWantList(enrichedWantList);
        setBingeList(Array.isArray(bingeListData) ? bingeListData as BingeListItem[] : []);
        setUserStats(statsData as UserStats | null);
        
        // 탭별 카운트 업데이트
        tabs[0].count = enrichedWatchHistory.length;
        tabs[1].count = enrichedWantList.length;
        tabs[2].count = 0; // 구매한 작품은 별도 API 필요
        tabs[3].count = Array.isArray(bingeListData) ? (bingeListData as BingeListItem[]).length : 0;
        
      } catch (err) {
        console.error('사용자 데이터 로드 실패:', err);
        setError('사용자 데이터를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    loadUserData();
  }, []);

  // 활동 탭 지연 로딩 및 캐싱
  useEffect(() => {
    const loadActivity = async () => {
      try {
        if (viewMode !== 'activity') return;
        if (activityTab === 'ratings') {
          if (myRatings == null) {
            const data = await getMyRatings().catch((e: Error & { status?: number })=>{ if (e?.status===401) return []; throw e; });
            setMyRatings(Array.isArray(data) ? data as Rating[] : []);
          }
        } else if (activityTab === 'reviews') {
          if (myReviews == null) {
            const data = await getMyReviews().catch((e: Error & { status?: number })=>{ if (e?.status===401) return []; throw e; });
            setMyReviews(Array.isArray(data) ? data as Review[] : []);
          }
        } else if (activityTab === 'comments') {
          if (myComments == null) {
            const data = await getMyComments().catch((e: Error & { status?: number })=>{ if (e?.status===401) return []; throw e; });
            setMyComments(Array.isArray(data) ? data as Comment[] : []);
          }
        }
      } catch (e) {
        console.error('활동 목록 로딩 실패:', e);
      }
    };
    loadActivity();
  }, [viewMode, activityTab]);

  // 삭제 모드 토글
  const toggleDeleteMode = () => {
    setIsDeleteMode(!isDeleteMode);
    setSelectedAnimeIds(new Set());
  };

  // 애니메이션 선택/해제
  const toggleAnimeSelection = (id: number) => {
    setSelectedAnimeIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  // 전체선택/해제
  const handleSelectAll = () => {
    if (viewMode === 'archive') {
      if (activeTab === 'recent') {
        if (selectedAnimeIds.size === watchHistory.length) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set(watchHistory.map(anime => anime.aniId || 0));
          setSelectedAnimeIds(allIds);
        }
      } else if (activeTab === 'want') {
        if (selectedAnimeIds.size === wantList.length) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set(wantList.map(anime => anime.aniId || 0));
          setSelectedAnimeIds(allIds);
        }
      } else if (activeTab === 'binge') {
        if (selectedAnimeIds.size === bingeList.length) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set(bingeList.map(anime => anime.aniId || 0));
          setSelectedAnimeIds(allIds);
        }
      }
    } else if (viewMode === 'activity') {
      if (activityTab === 'ratings') {
        const ratingsLength = myRatings?.length || 0;
        if (selectedAnimeIds.size === ratingsLength) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set((myRatings || []).map(rating => rating.animeId || rating.anime?.id).filter((id): id is number => id !== undefined));
          setSelectedAnimeIds(allIds);
        }
      } else if (activityTab === 'reviews') {
        const reviewsLength = myReviews?.length || 0;
        if (selectedAnimeIds.size === reviewsLength) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set((myReviews || []).map(review => review.animeId || review.anime?.id).filter((id): id is number => id !== undefined));
          setSelectedAnimeIds(allIds);
        }
      } else if (activityTab === 'comments') {
        const commentsLength = myComments?.length || 0;
        if (selectedAnimeIds.size === commentsLength) {
          setSelectedAnimeIds(new Set());
        } else {
          const allIds = new Set((myComments || []).map(comment => comment.commentId || comment.id).filter((id): id is number => id !== undefined));
          setSelectedAnimeIds(allIds);
        }
      }
    }
  };

  // 선택된 애니메이션들 삭제
  const deleteSelectedAnime = async () => {
    if (selectedAnimeIds.size === 0) return;
    
    try {
      if (activeTab === 'recent') {
        // 최근본 탭: 백엔드 API 호출하여 숨김 처리
        const deletePromises = Array.from(selectedAnimeIds).map(aniId => 
          hideFromRecent(aniId || 0).catch(err => {
            console.error(`애니메이션 ${aniId} 숨김 처리 실패:`, err);
          })
        );
        await Promise.all(deletePromises);
        
        // 프론트엔드 state 업데이트
        setWatchHistory(prev => prev.filter(anime => !selectedAnimeIds.has(anime.aniId || 0)));
      } else if (activeTab === 'want') {
        // 보고싶다 탭: 찜 취소 API 호출하여 실제 DB에서 삭제
        const deletePromises = Array.from(selectedAnimeIds).map(aniId => 
          removeFromWantList(aniId || 0).catch(err => {
            console.error(`애니메이션 ${aniId} 찜 취소 실패:`, err);
          })
        );
        await Promise.all(deletePromises);
        
        // 프론트엔드 state 업데이트
        setWantList(prev => prev.filter(anime => !selectedAnimeIds.has(anime.aniId || 0)));
      } else if (activeTab === 'binge') {
        // 정주행 탭: 시청 기록 완전 삭제 API 호출
        const deletePromises = Array.from(selectedAnimeIds).map(aniId => 
          deleteFromBinge(aniId || 0).catch(err => {
            console.error(`애니메이션 ${aniId} 정주행 삭제 실패:`, err);
          })
        );
        await Promise.all(deletePromises);
        
        // 프론트엔드 state 업데이트
        setBingeList(prev => prev.filter(anime => !selectedAnimeIds.has(anime.aniId || 0)));
      } else if (viewMode === 'activity') {
        if (activityTab === 'ratings') {
          // 별점 삭제: 프론트엔드에서만 제거 (실제 삭제 API는 없음)
          setMyRatings(prev => prev?.filter(rating => !selectedAnimeIds.has(rating.animeId || rating.anime?.id || 0)) || []);
        } else if (activityTab === 'reviews') {
          // 리뷰 삭제: 프론트엔드에서만 제거 (실제 삭제 API는 없음)
          setMyReviews(prev => prev?.filter(review => !selectedAnimeIds.has(review.animeId || review.anime?.id || 0)) || []);
        } else if (activityTab === 'comments') {
          // 댓글 삭제: 프론트엔드에서만 제거 (실제 삭제 API는 없음)
          setMyComments(prev => prev?.filter(comment => !selectedAnimeIds.has(comment.commentId || comment.id || 0)) || []);
        }
      }
      
      setIsDeleteMode(false);
      setSelectedAnimeIds(new Set());
    } catch (error) {
      console.error('삭제 처리 중 오류:', error);
    }
  };

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = async (anime: { 
    aniId?: number; 
    id?: number; 
    animeId?: number; 
    episodeId?: number; 
    positionSec?: number; 
    commentId?: number; 
    title?: string; 
    posterUrl?: string;
  }) => {
    // 댓글 탭 + 삭제 모드에서는 commentId로 토글
    if (isDeleteMode && viewMode === 'activity' && activityTab === 'comments') {
      const cid = Number(anime?.commentId);
      if (cid) {
        toggleAnimeSelection(cid);
      }
      return;
    }

    const aniId = (anime?.aniId ?? anime?.id ?? anime?.animeId) || 0;
    
    // 삭제 모드일 때는 선택/해제만 (기본: aniId)
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
        const id = aniId || 0;
      if (id) {
        const { getAnimeDetail } = await import('@/lib/api/anime');
        const detail = await getAnimeDetail(id);
        setSelectedAnime(detail as Anime);
      } else {
        setSelectedAnime(anime as Anime);
      }
    } catch (e) {
      console.warn('상세 조회 실패, 목록 데이터로 대체합니다.', e);
      setSelectedAnime(anime as Anime);
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
                      <Image 
                        src={userProfile.profileImage} 
                        alt="프로필 이미지"
                        width={80}
                        height={80}
                        className={styles.profileImage}
                      />
                    ) : (
                      <Image 
                        src="/icons/default-avatar.png"
                        alt="기본 프로필"
                        width={80}
                        height={80}
                        className={styles.profileImage}
                      />
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
                  <div
                    className={styles.statItem}
                    role="button"
                    tabIndex={0}
                    onClick={() => { setViewMode('activity'); setActivityTab('ratings'); setIsDeleteMode(false); setSelectedAnimeIds(new Set()); }}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (e.currentTarget as HTMLDivElement).click(); } }}
                  >
                    <div className={styles.statNumber}>
                      {userStats?.ratingCount || 0}
                    </div>
                    <div className={styles.statLabel}>별점</div>
                  </div>
                  <div
                    className={styles.statItem}
                    role="button"
                    tabIndex={0}
                    onClick={() => { setViewMode('activity'); setActivityTab('reviews'); setIsDeleteMode(false); setSelectedAnimeIds(new Set()); }}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (e.currentTarget as HTMLDivElement).click(); } }}
                  >
                    <div className={styles.statNumber}>
                      {userStats?.reviewCount || 0}
                    </div>
                    <div className={styles.statLabel}>리뷰</div>
                  </div>
                  <div
                    className={styles.statItem}
                    role="button"
                    tabIndex={0}
                    onClick={() => { setViewMode('activity'); setActivityTab('comments'); setIsDeleteMode(false); setSelectedAnimeIds(new Set()); }}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (e.currentTarget as HTMLDivElement).click(); } }}
                  >
                    <div className={styles.statNumber}>
                      {userStats?.commentCount || 0}
                    </div>
                    <div className={styles.statLabel}>댓글</div>
                  </div>
                </div>

                {/* 보관함 버튼 */}
                <button className={styles.archiveButton} onClick={() => { setViewMode('archive'); setIsDeleteMode(false); setSelectedAnimeIds(new Set()); }}>
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

            {/* 오른쪽 메인 콘텐츠 - 보관함/활동 */}
            <div className={styles.mainContentArea}>
              {viewMode === 'archive' ? (
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
                  
                  {activeTab === 'binge' && bingeList.length > 0 && (
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
                          {watchHistory.map((anime: WatchHistoryItem & { aniId?: number; title?: string; posterUrl?: string; episodeNumber?: number }, idx: number) => {
                            const aniId = (anime?.aniId ?? anime?.anime?.id) || 0;
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
                          {wantList.map((anime: WantListItem & { aniId?: number; title?: string; posterUrl?: string }, idx: number) => {
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
                      {!isDeleteMode ? (
                        <h3 className={styles.tabTitle}>작품 ({bingeList.length})</h3>
                      ) : (
                        <div className={styles.selectAllContainer}>
                          <input 
                            type="checkbox" 
                            checked={selectedAnimeIds.size === bingeList.length && bingeList.length > 0}
                            onChange={handleSelectAll}
                            className={styles.selectAllCheckbox}
                          />
                          <label className={styles.selectAllLabel}>
                            전체선택 ({selectedAnimeIds.size})
                          </label>
                        </div>
                      )}
                      {Array.isArray(bingeList) && bingeList.length > 0 ? (
                        <div className={styles.animeGrid}>
                          {bingeList.map((anime: BingeListItem & { aniId?: number; title?: string; posterUrl?: string }, idx: number) => {
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
                          정주행 완료한 작품이 없습니다
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
              ) : (
                <div className={styles.tabContainer}>
                  <div className={styles.tabMenu}>
                    <div className={styles.tabButtons}>
                      {(['ratings','reviews','comments'] as const).map((tab: 'ratings'|'reviews'|'comments') => (
                        <button
                          key={tab}
                          onClick={() => { setActivityTab(tab); setIsDeleteMode(false); setSelectedAnimeIds(new Set()); }}
                          className={`${styles.tabButton} ${activityTab === tab ? styles.tabButtonActive : ''}`}
                        >
                          {tab === 'ratings' ? '별점' : tab === 'reviews' ? '리뷰' : '댓글'}
                        </button>
                      ))}
                    </div>
                    
                    {/* 삭제 버튼 - 활동 탭에서 데이터가 있을 때만 표시 */}
                    {activityTab === 'ratings' && myRatings && myRatings.length > 0 && (
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
                    
                    {activityTab === 'reviews' && myReviews && myReviews.length > 0 && (
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
                    
                    {activityTab === 'comments' && myComments && myComments.length > 0 && (
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

                  <div className={styles.tabContent}>
                    {activityTab === 'ratings' && (
                      <div>
                        {!isDeleteMode ? (
                          <h3 className={styles.tabTitle}>내 별점 ({(myRatings?.length ?? 0)})</h3>
                        ) : (
                          <div className={styles.selectAllContainer}>
                            <input 
                              type="checkbox" 
                              checked={selectedAnimeIds.size === (myRatings?.length || 0) && (myRatings?.length || 0) > 0}
                              onChange={handleSelectAll}
                              className={styles.selectAllCheckbox}
                            />
                            <label className={styles.selectAllLabel}>
                              전체선택 ({selectedAnimeIds.size})
                            </label>
                          </div>
                        )}
                        {Array.isArray(myRatings) && myRatings.length > 0 ? (
                          <div className={styles.animeGrid}>
                            {myRatings.map((item: Rating, idx: number) => {
                              const aniId = item.animeId || item.anime?.id || 0;
                              const isSelected = selectedAnimeIds.has(aniId);
                              
                              return (
                                <div 
                                  key={`${aniId ?? 'rating'}-${idx}`}
                                  className={`${styles.animeItem} ${isDeleteMode ? styles.selectable : ''} ${isSelected ? styles.selected : ''}`}
                                  onClick={() => handleAnimeClick({ aniId: aniId, animeId: aniId, title: item.title, posterUrl: item.posterUrl })}
                                >
                                  {isDeleteMode && isSelected && (
                                    <div className={styles.selectionIndicator}>
                                      ✓
                                    </div>
                                  )}
                                <div 
                                  className={styles.animePoster}
                                  style={{
                                    backgroundImage: item.posterUrl ? `url(${item.posterUrl})` : 'none',
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center',
                                    backgroundColor: item.posterUrl ? 'transparent' : '#323232'
                                  }}
                                ></div>
                                <p className={styles.animeTitle}>{item.title}</p>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
                                  <svg width="18" height="18" viewBox="0 0 24 24" fill="#6C63FF" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
                                  </svg>
                                  <span style={{ color: '#7C6BFF', fontWeight: 700 }}>{(item.score ?? 0).toFixed(1)}</span>
                                </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className={styles.emptyState}>활동 없음</div>
                        )}
                      </div>
                    )}

                    {activityTab === 'reviews' && (
                      <div>
                        {!isDeleteMode ? (
                          <h3 className={styles.tabTitle}>리뷰 ({(myReviews?.length ?? 0)})</h3>
                        ) : (
                          <div className={styles.selectAllContainer}>
                            <input 
                              type="checkbox" 
                              checked={selectedAnimeIds.size === (myReviews?.length || 0) && (myReviews?.length || 0) > 0}
                              onChange={handleSelectAll}
                              className={styles.selectAllCheckbox}
                            />
                            <label className={styles.selectAllLabel}>
                              전체선택 ({selectedAnimeIds.size})
                            </label>
                          </div>
                        )}
                        {Array.isArray(myReviews) && myReviews.length > 0 ? (
                          <div className={styles.reviewList}>
                            {myReviews.map((item: Review, idx: number) => {
                              const aniId = item.animeId || item.anime?.id || 0;
                              const isSelected = selectedAnimeIds.has(aniId);
                              
                              return (
                                <div
                                  key={`${item.reviewId ?? 'review'}-${idx}`}
                                  className={`${styles.reviewItemButton} ${isDeleteMode ? styles.selectable : ''} ${isSelected ? styles.selected : ''}`}
                                  onClick={() => handleAnimeClick({ aniId: aniId, animeId: aniId, title: item.title, posterUrl: item.posterUrl })}
                                >
                                  {/* 리뷰 카드에서는 체크 배지를 포스터 위에만 표시 */}
                                <div className={styles.reviewHeader}>
                                  <div>
                                    <h3 className={styles.reviewTitle}>{item.title}</h3>
                                    <div className={styles.reviewStarRow}>
                                      {[0,1,2,3,4].map((i) => (
                                        <svg key={i} width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                          <path fillRule="evenodd" clipRule="evenodd" d="M8.092 20.746c-1.273.699-2.346-.114-2.103-1.596l.731-4.45a.37.37 0 0 0-.05-.16l-3.095-3.151c-1.03-1.049-.622-2.365.803-2.581l4.278-.65a.34.34 0 0 0 .131-.099L10.7 4.011c.637-1.348 1.962-1.348 2.6 0l1.913 4.048a.346.346 0 0 0 .13.1l4.28.649c1.423.216 1.833 1.531.803 2.58L17.33 14.54a.37.37 0 0 0-.05.16l.73 4.45c.244 1.481-.828 2.295-2.103 1.596l-3.826-2.101a.329.329 0 0 0-.162 0l-3.827 2.1Z" fill="#816BFF" />
                                        </svg>
                                      ))}
                                      <span className={styles.reviewScore}>{(item.score ?? 0).toFixed(1)}</span>
                                    </div>
                                  </div>
                                </div>
                                <div className={styles.reviewBody}>
                                  <div className={styles.reviewContentBox}>
                                    <p className={styles.reviewExcerpt}>{(item.content || '').slice(0, 80)}{(item.content || '').length > 80 ? '…' : ''}</p>
                                    <div className={styles.reviewFooter}>
                                      <div className={styles.reviewMeta}>{formatRelativeTime(item.updatedAt || item.createdAt)}</div>
                                      <button
                                        type="button"
                                        className={styles.likeButton}
                                        onClick={(e) => {
                                          e.preventDefault();
                                          e.stopPropagation();
                                          const willLike = !likedReviews.has(Number(item.reviewId));
                                          setLikedReviews(prev => {
                                            const next = new Set(prev);
                                            const id = Number(item.reviewId);
                                            if (next.has(id)) next.delete(id); else next.add(id);
                                            return next;
                                          });
                                          // optimistic count update
                                          setMyReviews(prev => Array.isArray(prev) ? prev.map(r => r.reviewId === item.reviewId ? { ...r, likeCount: Math.max(0, (r.likeCount ?? 0) + (willLike ? 1 : -1)) } : r) : prev);
                                          toggleReviewLike(Number(item.animeId), Number(item.reviewId)).catch((err: Error)=>{
                                            console.error('리뷰 좋아요 토글 실패', err);
                                            // rollback on failure
                                            setLikedReviews(prev => { const n = new Set(prev); const id = Number(item.reviewId); if (willLike) n.delete(id); else n.add(id); return n; });
                                            setMyReviews(prev => Array.isArray(prev) ? prev.map(r => r.reviewId === item.reviewId ? { ...r, likeCount: Math.max(0, (r.likeCount ?? 0) + (willLike ? -1 : 1)) } : r) : prev);
                                          });
                                        }}
                                      >
                                        <svg className={styles.likeIcon} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                          <path fillRule="evenodd" clipRule="evenodd" d="M10.405 3.588A1 1 0 0 1 11.316 3c.907 0 1.78.354 2.426.99a3.38 3.38 0 0 1 1.013 2.41v2.2h3.596a2.648 2.648 0 0 1 2 .888 2.593 2.593 0 0 1 .619 2.104l-1.122 7.2a.596.596 0 0 1-.901 1.594 2.642 2.642 0 0 1-1.718.614H5.626a2.64 2.64 0 0 1-1.851-.756A2.587 2.587 0 0 1 3 18.4v-5.6c0-.695.28-1.358.775-1.844a2.64 2.64 0 0 1 1.85-.756H7.42l2.986-6.612ZM7.065 12.2h-1.44a.64.64 0 0 0-.447.181A.587.587 0 0 0 5 12.8v5.6c0 .154.062.305.178.419a.639.639 0 0 0 .448.18h1.438V12.2Zm2 6.8h8.18a.642.642 0 0 0 .419-.148.594.594 0 0 0 .207-.364l1.122-7.2a.578.578 0 0 0-.141-.476.649.649 0 0 0-.485-.212h-4.612a1 1 0 0 1-1-1V6.4c0-.366-.148-.72-.416-.984a1.441 1.441 0 0 0-.433-.293l-2.842 6.292V19Z" fill={likedReviews.has(Number(item.reviewId)) ? '#8b5cf6' : 'currentColor'} />
                                        </svg>
                                        <span>좋아요 {item.likeCount ?? 0}</span>
                                      </button>
                                    </div>
                                  </div>
                                  <div className={styles.reviewPosterWrap}>
                                    {isDeleteMode && isSelected && (
                                      <div className={styles.thumbCheckOverlay}>✓</div>
                                    )}
                                    <Image className={styles.reviewPoster} src={item.posterUrl || "/placeholder-anime.jpg"} alt={item.title || "포스터"} width={120} height={160} />
                                  </div>
                                </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className={styles.emptyState}>활동 없음</div>
                        )}
                      </div>
                    )}

                    {activityTab === 'comments' && (
                      <div>
                        <div className={styles.commentListHeader}>
                          {!isDeleteMode ? (
                            <h3 className={styles.tabTitle} style={{ marginBottom: 0 }}>댓글 ({(myComments?.length ?? 0)})</h3>
                          ) : (
                            <div className={styles.selectAllContainer}>
                              <input 
                                type="checkbox" 
                                checked={selectedAnimeIds.size === (myComments?.length || 0) && (myComments?.length || 0) > 0}
                                onChange={handleSelectAll}
                                className={styles.selectAllCheckbox}
                              />
                              <label className={styles.selectAllLabel}>
                                전체선택 ({selectedAnimeIds.size})
                              </label>
                            </div>
                          )}
                          <select
                            aria-label="댓글 정렬"
                            className={styles.commentSortSelect}
                            value={commentSort}
                            onChange={(e)=> setCommentSort(e.target.value as 'latest'|'likes'|'oldest')}
                          >
                            <option value="latest">최신 순</option>
                            <option value="likes">좋아요 순</option>
                            <option value="oldest">오래된 순</option>
                          </select>
                        </div>
                        {Array.isArray(myComments) && myComments.length > 0 ? (()=>{
                          const sorted = [...myComments].sort((a: Comment & { likeCount?: number }, b: Comment & { likeCount?: number })=>{
                            if (commentSort === 'latest') return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
                            if (commentSort === 'likes') return (b.likeCount||0) - (a.likeCount||0);
                            return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                          });
                          console.log('[mypage:comments] sorted', commentSort, { count: sorted.length });
                          return (
                            <div className={styles.reviewList} role="list" aria-label="내 댓글 목록">
                              {sorted.map((item: Comment, idx: number) => {
                                const commentId = item.commentId || item.id || 0;
                                const isSelected = selectedAnimeIds.has(commentId);
                                
                                return (
                                  <div
                                    role="listitem"
                                    key={`${item.commentId ?? 'comment'}-${idx}`}
                                    className={`${styles.reviewItemButton} ${styles.commentItem} ${isDeleteMode ? styles.selectable : ''} ${isSelected ? styles.selected : ''}`}
                                    onClick={() => handleAnimeClick({ aniId: item.animeId || item.anime?.id || 0, animeId: item.animeId || item.anime?.id || 0, commentId: commentId, title: item.title, posterUrl: item.posterUrl })}
                                  >
                                    {/* 댓글 섹션에서는 카드 전체 체크 오버레이를 렌더링하지 않음 (썸네일 위에만 표시) */}
                                  <Image
                                    className={styles.commentAvatar}
                                    src={item.userProfileImage || '/icons/default-avatar.png'}
                                    alt="사용자 아바타"
                                    width={40}
                                    height={40}
                                  />
                                  <div className={styles.commentTopRow}>
                                    <div className={styles.commentTitleBox}>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                        {item.tagLabel ? (
                                          <span className={styles.commentTagBadge}>{item.tagLabel}</span>
                                        ) : null}
                                        <span className={styles.commentAniTitle}>{item.title}</span>
                                      </div>
                                      {(item.targetType === 'EPISODE' && (item.episodeTitle || item.title)) ? (
                                        <span
                                          className={styles.commentEpisodeTitle}
                                          aria-label={`에피소드로 이동: ${item.episodeTitle || item.title}`}
                                        >
                                          {item.episodeTitle || item.title}
                                        </span>
                                      ) : null}
                                    </div>
                                  </div>
                                  <div className={styles.commentMiddleRow}>
                                    <p className={styles.commentBodyText}>{item.content || ''}</p>
                                  </div>
                                  <div className={styles.commentBottomRow}>
                                    <div className={styles.reviewMeta}>{formatRelativeTime(item.createdAt)}</div>
                                    <button
                                      type="button"
                                      className={styles.likeButton}
                                      onClick={(e)=>{
                                        e.preventDefault(); e.stopPropagation();
                                        const key = `${item.targetType}-${item.commentId}`;
                                        const willLike = !likedComments.has(key);
                                        setLikedComments(prev => { const n = new Set(prev); n.has(key)? n.delete(key) : n.add(key); return n; });
                                        setMyComments(prev => Array.isArray(prev) ? prev.map(c => c.commentId === item.commentId ? { ...c, likeCount: Math.max(0, (c.likeCount ?? 0) + (willLike ? 1 : -1)) } : c) : prev);
                                        const revert = () => {
                                          setLikedComments(prev => { const n = new Set(prev); if (willLike) n.delete(key); else n.add(key); return n; });
                                          setMyComments(prev => Array.isArray(prev) ? prev.map(c => c.commentId === item.commentId ? { ...c, likeCount: Math.max(0, (c.likeCount ?? 0) + (willLike ? -1 : 1)) } : c) : prev);
                                        };
                                        if (item.targetType === 'REVIEW') {
                                          toggleReviewCommentLike(Number(item.targetId), Number(item.commentId)).catch(err=>{ console.error('리뷰 댓글 좋아요 실패', err); revert(); });
                                        } else {
                                          toggleEpisodeCommentLike(Number(item.targetId), Number(item.commentId)).catch((err: Error)=>{ console.error('에피소드 댓글 좋아요 실패', err); revert(); });
                                        }
                                      }}
                                    >
                                      <svg className={styles.likeIcon} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path fillRule="evenodd" clipRule="evenodd" d="M10.405 3.588A1 1 0 0 1 11.316 3c.907 0 1.78.354 2.426.99a3.38 3.38 0 0 1 1.013 2.41v2.2h3.596a2.648 2.648 0 0 1 2 .888 2.593 2.593 0 0 1 .619 2.104l-1.122 7.2a.596.596 0 0 1-.901 1.594 2.642 2.642 0 0 1-1.718.614H5.626a2.64 2.64 0 0 1-1.851-.756A2.587 2.587 0 0 1 3 18.4v-5.6c0-.695.28-1.358.775-1.844a2.64 2.64 0 0 1 1.85-.756H7.42l2.986-6.612ZM7.065 12.2h-1.44a.64.64 0 0 0-.447.181A.587.587 0 0 0 5 12.8v5.6c0 .154.062.305.178.419a.639.639 0 0 0 .448.18h1.438V12.2Zm2 6.8h8.18a.642.642 0 0 0 .419-.148.594.594 0 0 0 .207-.364l1.122-7.2a.578.578 0 0 0-.141-.476.649.649 0 0 0-.485-.212h-4.612a1 1 0 0 1-1-1V6.4c0-.366-.148-.72-.416-.984a1.441 1.441 0 0 0-.433-.293l-2.842 6.292V19Z" fill={likedComments.has(`${item.targetType}-${item.commentId}`) ? '#8b5cf6' : 'currentColor'} />
                                      </svg>
                                      <span>좋아요 {item.likeCount ?? 0}</span>
                                    </button>
                                  </div>
                                  <div className={styles.commentEpisodeThumb}>
                                    {isDeleteMode && isSelected && (
                                      <div className={styles.thumbCheckOverlay}>✓</div>
                                    )}
                                    <img src={item.episodeThumbUrl || item.posterUrl || '/icons/default-avatar.png'} alt={item.episodeTitle || item.title} />
                                  </div>
                                  </div>
                                );
                              })}
                            </div>
                          );
                        })() : (
                          <div className={styles.emptyState}>활동 없음</div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              )}
            
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

export default function MyPage() {
  return (
    <Suspense fallback={<div className={styles.mypageContainer}><div className={styles.loadingContainer}><div className={styles.loadingText}>로딩 중...</div></div></div>}>
      <MyPageContent />
    </Suspense>
  );
}
