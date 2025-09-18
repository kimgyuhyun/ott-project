"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import ReviewList from "@/components/reviews/ReviewList";
import { getAnimeDetail, listAnime } from "@/lib/api/anime";
import AnimeCard from "@/components/home/AnimeCard";
import { getAnimeWatchHistory } from "@/lib/api/user";
import { toggleFavorite, isFavorited } from "@/lib/api/favorites";
import { deleteFromBinge } from "@/lib/api/user";
import { Anime, Episode, User } from "@/types/common";
import styles from "./AnimeDetailModal.module.css";
import AnimeFullInfoModal from "@/components/anime/AnimeFullInfoModal";


interface AnimeDetailModalProps {
  anime: Anime;
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 애니메이션 상세 정보 모달
 * 평점, 제목, 장르, 액션 버튼, 시놉시스, 탭 메뉴, 에피소드 목록 포함
 */
export default function AnimeDetailModal({ anime, isOpen, onClose }: AnimeDetailModalProps) {
  type ExtendedAnime = Anime & {
    aniId?: number | string;
    episodes?: Episode[];
    fullSynopsis?: string;
    synopsis?: string;
    badges?: string[];
    isDub?: boolean;
    isSubtitle?: boolean;
    titleJp?: string;
    titleEn?: string;
    backdropUrl?: string;
    posterUrl?: string;
    imageUrl?: string;
    thumbnail?: string;
    posterImage?: string;
    ageRating?: string;
    type?: string;
    animeStatus?: 'COMPLETED' | 'ONGOING' | 'UPCOMING' | 'CANCELLED' | string;
    rating?: number;
    genres?: Array<string | { id?: number; name?: string }>;
  };
  type WatchHistory = {
    episodeId: number;
    episodeNumber: number;
    positionSec: number;
    duration?: number;
    completed: boolean;
    watchedAt?: string | number | Date;
  } | null;
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');
  const [detail, setDetail] = useState<ExtendedAnime>(anime as ExtendedAnime);
  const [watchHistory, setWatchHistory] = useState<WatchHistory>(null);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [isFavoritedState, setIsFavoritedState] = useState<boolean>(false);
  const [isLoadingFavorite, setIsLoadingFavorite] = useState(false);
  const [currentRating, setCurrentRating] = useState<number | null>(null); // 실시간 평점 상태
  const [similarAnimes, setSimilarAnimes] = useState<Anime[]>([]);
  const [isLoadingSimilar, setIsLoadingSimilar] = useState(false);
  const [showFullSynopsis, setShowFullSynopsis] = useState<boolean>(false);
  const MAX_SYNOPSIS_CHARS = 180;
  const [isFullInfoOpen, setIsFullInfoOpen] = useState<boolean>(false);
  const [isDropdownOpen, setIsDropdownOpen] = useState<boolean>(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<boolean>(false);

  // 평점 변경 콜백 함수
  const handleRatingChange = (newRating: number) => {
    setCurrentRating(newRating);
    // detail 객체의 rating도 업데이트
    setDetail((prev: ExtendedAnime) => ({ ...prev, rating: newRating }));
  };

  // 시청 기록 초기화 핸들러
  const handleDeleteWatchHistory = async () => {
    try {
      console.log('🗑️ 시청 기록 초기화 시작 - aniId:', (detail as any)?.aniId);
      await deleteFromBinge(Number((detail as any)?.aniId ?? (detail as any)?.id));
      console.log('🗑️ 시청 기록 초기화 완료');
      
      // 시청 기록 상태 초기화
      setWatchHistory(null);
      setShowDeleteConfirm(false);
      setIsDropdownOpen(false);
      
      alert('시청 기록이 초기화되었습니다.');
    } catch (error) {
      console.error('시청 기록 초기화 실패:', error);
      alert('시청 기록 초기화에 실패했습니다.');
    }
  };

  useEffect(() => {
    setDetail(anime as ExtendedAnime);
    // 초기 평점 설정
    if (anime?.rating) {
      setCurrentRating(anime.rating);
    }
  }, [anime]);

  useEffect(() => {
    if (!isOpen) return;
    const id = (anime as any)?.aniId ?? (anime as any)?.id;
    const needsFetch = !Array.isArray((anime as any)?.genres) || (anime as any).genres.length === 0 || !Array.isArray((anime as any)?.episodes);
    if (id && needsFetch) {
      getAnimeDetail(Number(id))
        .then((d) => setDetail((prev: ExtendedAnime) => ({ ...prev, ...(d as Partial<ExtendedAnime>) })))
        .catch(() => {});
    }
  }, [isOpen, anime]);

  // 비슷한 작품 로드
  useEffect(() => {
    if (activeTab === 'similar' && similarAnimes.length === 0) {
      loadSimilarAnimes();
    }
  }, [activeTab]);

  const loadSimilarAnimes = async () => {
    setIsLoadingSimilar(true);
    try {
      // 현재 작품과 장르가 겹치는 작품 목록을 조회
      const genreIds: number[] = Array.isArray(detail?.genres)
        ? (detail.genres as any[])
            .map((g: { id: number } | number) => Number(typeof g === 'object' ? g?.id : g))
            .filter((v: number) => Number.isFinite(v))
        : [];

      if (genreIds.length === 0) {
        console.log('⚠️ 비슷한 작품 로드: 장르 정보 없음');
        setSimilarAnimes([]);
        return;
      }

      const response: any = await listAnime({ genreIds, sort: 'rating', page: 0, size: 30 });
      const rawItems: ExtendedAnime[] = Array.isArray(response?.items)
        ? (response.items as ExtendedAnime[])
        : (Array.isArray(response) ? (response as ExtendedAnime[]) : []);

      const baseId = Number((detail as any)?.aniId ?? (detail as any)?.id);
      const filtered = rawItems.filter((a: ExtendedAnime) => Number((a as any)?.aniId ?? (a as any)?.id) !== baseId);

      // 중복 제거 (aniId 기준)
      const seen = new Set<number>();
      const unique = filtered.filter((a: ExtendedAnime) => {
        const id = Number((a as any)?.aniId ?? (a as any)?.id);
        if (!Number.isFinite(id) || seen.has(id)) return false;
        seen.add(id);
        return true;
      });

      const limited = unique.slice(0, 6);
      console.log('📦 비슷한 작품 로드 결과:', limited.length, '(장르 기반)');
      setSimilarAnimes(limited);
    } catch (error) {
      console.error('비슷한 작품 로드 실패:', error);
      setSimilarAnimes([]);
    } finally {
      setIsLoadingSimilar(false);
    }
  };

  // 사용자의 시청 기록 가져오기
  useEffect(() => {
    if (!isOpen || !(detail as any)?.aniId) return;
    
    console.log('🔍 시청 기록 조회 시작 - animeId:', (detail as any).aniId);
    setIsLoadingHistory(true);
    getAnimeWatchHistory(Number((detail as any).aniId))
      .then((history: any) => {
        console.log('🔍 시청 기록 조회 결과:', history);
        setWatchHistory(history as WatchHistory);
      })
      .catch((error) => {
        console.error('시청 기록 조회 실패:', error);
        setWatchHistory(null);
      })
      .finally(() => {
        setIsLoadingHistory(false);
      });
  }, [isOpen, detail?.aniId]);

  // 보고싶다 상태 확인
  useEffect(() => {
    if (!isOpen || !(detail as any)?.aniId) return;
    
    isFavorited(Number((detail as any).aniId))
      .then((favorited) => {
        setIsFavoritedState(favorited);
      })
      .catch((error) => {
        console.error('보고싶다 상태 조회 실패:', error);
        setIsFavoritedState(false);
      });
  }, [isOpen, detail?.aniId]);

  // 라프텔 방식: 모달 열 때 CSS 동적 주입
  useEffect(() => {
    if (isOpen) {
      // html 태그에 data-theme="light" 추가
      document.documentElement.setAttribute('data-theme', 'light');
      
      // body에 overflow: hidden !important 적용
      document.body.style.overflow = 'hidden';
      document.body.style.setProperty('overflow', 'hidden', 'important');
    } else {
      // 모달 닫을 때 원래 상태로 복원
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    }

    // 컴포넌트 언마운트 시 정리
    return () => {
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    };
  }, [isOpen]);

  // 디버깅: anime 객체 확인
  console.log('🔍 AnimeDetailModal - anime 객체:', detail);
  console.log('🔍 AnimeDetailModal - anime.aniId:', (detail as any)?.aniId);
  console.log('🔍 AnimeDetailModal - anime 타입:', typeof detail);
  console.log('🔍 장르 정보:', (detail as any)?.genres);
  console.log('🔍 평점 정보:', detail?.rating);
  console.log('🔍 관람등급:', detail?.ageRating);
  console.log('🔍 줄거리:', (detail as any)?.fullSynopsis || (detail as any)?.synopsis);
  console.log('🔍 에피소드:', (detail as any)?.episodes);
  console.log('🔍 시청 기록 상태:', {
    watchHistory,
    isLoadingHistory,
    hasWatchHistory: !!watchHistory,
    isCompleted: (watchHistory as any)?.completed,
    episodeNumber: (watchHistory as any)?.episodeNumber,
    positionSec: (watchHistory as any)?.positionSec,
    shouldShowContinue: !isLoadingHistory && !!watchHistory && !(watchHistory as any).completed,
    shouldShowPlay: !isLoadingHistory && (!watchHistory || (watchHistory as any).completed)
  });

  if (!isOpen) return null;

  const tabs: { id: 'episodes' | 'reviews' | 'shop' | 'similar'; label: string; count: number | null }[] = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: null },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = Array.isArray((detail as any)?.episodes) ? ((detail as any).episodes as Episode[]) : [];
  const getFallbackEpisodeThumb = (episodeNumber?: number) => {
    const n = Number(episodeNumber);
    if (n === 1) return 'https://placehold.co/120x80/111827/ffffff?text=EP1+Thumbnail';
    if (n === 2) return 'https://placehold.co/120x80/1f2937/ffffff?text=EP2+Thumbnail';
    return 'https://placehold.co/120x80/374151/ffffff?text=Episode';
  };

  return (
    <div className={styles.animeDetailModalOverlay}>
      {/* 배경 오버레이 */}
      <div 
        className={styles.animeDetailModalBackdrop}
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div className={`${styles.animeDetailModalContainer} ${isFullInfoOpen ? styles.dimTabs : ''}`}>
        {/* 점3개 메뉴 버튼 - X버튼 왼쪽 */}
        <div className={styles.menuButtonContainer}>
          <button
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className={styles.menuButton}
            aria-label="메뉴"
          >
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
            </svg>
          </button>
          
          {/* 드롭다운 메뉴 */}
          {isDropdownOpen && (
            <div className={styles.dropdownMenu}>
              <button
                onClick={() => {
                  setShowDeleteConfirm(true);
                  setIsDropdownOpen(false);
                }}
                className={styles.dropdownItem}
              >
                시청 기록 초기화
              </button>
            </div>
          )}
        </div>

        {/* 닫기 버튼 - 상단 오른쪽 */}
        <button
          onClick={onClose}
          className={styles.animeDetailModalCloseButton}
          aria-label="닫기"
        >
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 상단 정보 섹션 */}
        <div className={styles.topInfoSection}>
          {/* 배경 이미지: DB의 backdropUrl을 우선 사용, 없으면 다크 배경만 */}
          <div className={styles.backgroundImage}>
            <div className={styles.backgroundContainer}>
              {detail?.backdropUrl ? (
                <div
                  className={styles.characterImage}
                  style={{ backgroundImage: `url(${detail.backdropUrl})` }}
                />
              ) : (
                <div className={`${styles.characterImage} ${styles.noBackdrop}`} />
              )}
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div className={styles.smallPoster}>
            <div className={styles.posterContainer}>
              <img 
                src={detail?.posterUrl || "https://placehold.co/96x128/ff69b4/ffffff?text=LAFTEL+ONLY"} 
                alt={`${(detail?.title || detail?.titleEn || detail?.titleJp || '애니메이션')} 포스터`}
                className={styles.posterImage}
              />
            </div>
          </div>

          {/* 상단 정보 오버레이 */}
          <div className={styles.topInfoOverlay}>
              {/* 평점 및 배지 - 왼쪽 상단 */}
              <div className={styles.ratingSection}>
                <div className={styles.ratingContainer}>
                  <span className={styles.ratingStar}>★</span>
                  <span className={styles.ratingValue}>
                    {typeof currentRating === 'number' ? currentRating.toFixed(1) : 'N/A'}
                  </span>
                </div>
                <span className={styles.ratingBadge}>
                  {Array.isArray((detail as any)?.badges) ? (detail as any).badges[0] : 'ONLY'}
                </span>
              </div>

              {/* 애니메이션 제목 */}
              <h1 className={styles.animeTitle}>
                {(() => {
                  // 더빙과 자막 여부 확인
                  const isDub = (detail as any)?.isDub === true;
                  const isSubtitle = (detail as any)?.isSubtitle === true;
                  
                  let prefix = '';
                  if (isDub && isSubtitle) {
                    // 둘 다 true인 경우 자막으로 표시
                    prefix = '(자막) ';
                  } else if (isDub) {
                    prefix = '(더빙) ';
                  } else if (isSubtitle) {
                    prefix = '(자막) ';
                  }
                  
                  const title = (detail as any)?.title || (detail as any)?.titleEn || (detail as any)?.titleJp || '제목 없음';
                  return `${prefix}${title}`;
                })()}
              </h1>

              {/* 장르 및 정보 */}
              <div className={styles.genreSection}>
                {Array.isArray((detail as any)?.genres) && (detail as any).genres.length > 0 ? (
                  ((detail as any).genres as Array<string | { name?: string }>).slice(0, 6).map((g: any, idx: number) => (
                    <span key={idx} className={styles.genreTag}>
                      {typeof g === 'string' ? g : (g?.name || '')}
                    </span>
                  ))
                ) : (
                  <span className={styles.genreTag}>장르 정보 없음</span>
                )}
                
                {/* 애니메이션 타입·상태 */}
                <span className={styles.typeStatusBadge}>
                  {(detail as any)?.type || 'TV'}·{(detail as any)?.animeStatus === 'COMPLETED' ? '완결' : 
                   (detail as any)?.animeStatus === 'ONGOING' ? '방영중' : 
                   (detail as any)?.animeStatus === 'UPCOMING' ? '예정' : 
                   (detail as any)?.animeStatus === 'CANCELLED' ? '중단' : '완결'}
                </span>
                
                {/* 관람등급 */}
                <div className={styles.ageRatingBadge}>
                  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="9" fill="#E9B62F" stroke="#FFFFFF" strokeWidth="2" />
                    <text x="10" y="10" textAnchor="middle" dominantBaseline="central" fill="#000" fontSize="7" fontWeight="700">
                      {(() => {
                        const rating = detail?.ageRating;
                        if (rating === '전체 이용가') return 'ALL';
                        if (rating === '15세이상') return '15';
                        if (rating === '12세이상') return '12';
                        if (rating === '19세이상') return '19';
                        if (rating === 'ALL') return 'ALL';
                        return 'ALL';
                      })()}
                    </text>
                  </svg>
                </div>
                
              </div>

              {/* 액션 버튼들 */}
              <div className={styles.animeDetailModalActionButtons}>
                {/* 로딩 중일 때 */}
                {isLoadingHistory && (
                  <div className={styles.loadingMessage}>시청 기록을 불러오는 중...</div>
                )}
                
                {/* 이어보기 버튼 - 시청 기록이 있고 완료되지 않은 경우 */}
                {!isLoadingHistory && watchHistory && !watchHistory.completed && (
                  <div className={styles.playButtonContainer}>
                    <button 
                      onClick={() => {
                        console.log('🎬 이어보기 버튼 클릭:', {
                          episodeId: (watchHistory as any).episodeId,
                          animeId: (detail as any)?.aniId,
                          positionSec: (watchHistory as any).positionSec,
                          episodeNumber: (watchHistory as any).episodeNumber
                        });
                        // 이어보기: 마지막으로 본 에피소드부터 재생
                        const position = (watchHistory as any).positionSec > 0 ? `&position=${(watchHistory as any).positionSec}` : '';
                        const url = `/player?episodeId=${(watchHistory as any).episodeId}&animeId=${(detail as any)?.aniId}${position}`;
                        console.log('🔗 이동할 URL:', url);
                        router.push(url);
                        onClose();
                      }}
                      className={styles.playButton}
                    >
                      <div className={styles.playButtonIcon}>
                        <svg fill="currentColor" viewBox="0 0 24 24">
                          <path d="M8 5v14l11-7z"/>
                        </svg>
                      </div>
                      <span className={styles.playButtonText}>{watchHistory.episodeNumber}화 이어보기</span>
                    </button>
                  </div>
                )}
                
                {/* 처음보기 또는 완료된 경우 보러가기 버튼 */}
                {!isLoadingHistory && (!watchHistory || watchHistory.completed) && (
                  <div className={styles.playButtonContainer}>
                    <button 
                      onClick={() => {
                        console.log('🎬 재생하기 버튼 클릭:', {
                          watchHistory,
                          hasWatchHistory: !!watchHistory,
                          isCompleted: (watchHistory as any)?.completed,
                          animeId: (detail as any)?.aniId
                        });
                        
                        // 시청 기록이 있지만 완료된 경우: 다음 에피소드부터 시작
                        // 시청 기록이 없는 경우: 1화부터 시작
                        let nextEpisodeId = 1;
                        if (watchHistory && (watchHistory as any).completed) {
                          // 완료된 경우 다음 에피소드
                          nextEpisodeId = (watchHistory as any).episodeNumber + 1;
                        }
                        
                        const url = `/player?episodeId=${nextEpisodeId}&animeId=${(detail as any)?.aniId}`;
                        console.log('🔗 이동할 URL:', url);
                        router.push(url);
                        onClose();
                      }}
                      className={styles.playButton}
                    >
                      <div className={styles.playButtonIcon}>
                        <svg fill="currentColor" viewBox="0 0 24 24">
                          <path d="M8 5v14l11-7z"/>
                        </svg>
                      </div>
                      <span className={styles.playButtonText}>
                        {watchHistory && (watchHistory as any).completed 
                          ? `${(watchHistory as any).episodeNumber + 1}화 재생하기`
                          : '1화 재생하기'
                        }
                      </span>
                    </button>
                  </div>
                )}
                
                {/* 보고싶다 버튼 */}
                <div className={styles.favoriteButtonContainer}>
                  <button 
                    onClick={async () => {
                      if (isLoadingFavorite) return;
                      
                      try {
                        setIsLoadingFavorite(true);
                        const newState = await toggleFavorite(Number((detail as any)?.aniId));
                        setIsFavoritedState(newState);
                        console.log('보고싶다 토글 완료:', newState);
                      } catch (error) {
                        console.error('보고싶다 토글 실패:', error);
                        alert('보고싶다 기능을 사용할 수 없습니다.');
                      } finally {
                        setIsLoadingFavorite(false);
                      }
                    }}
                    disabled={isLoadingFavorite}
                    className={`${styles.favoriteButton} ${isFavoritedState ? styles.favorited : ''}`}
                  >
                    <div className={styles.favoriteButtonContent}>
                      {isFavoritedState ? (
                        <svg 
                          className={styles.checkIcon} 
                          fill="currentColor" 
                          viewBox="0 0 24 24"
                        >
                          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                        </svg>
                      ) : (
                        <span className={styles.plusIcon}>+</span>
                      )}
                      <span className={styles.favoriteButtonText}>
                        {isFavoritedState ? '보관중' : '보고싶다'}
                      </span>
                    </div>
                  </button>
                  <div className={styles.favoriteTooltip}>
                    {isFavoritedState ? '보관함에서 제거' : '보관함에 추가'}
                  </div>
                </div>
                
                {/* 공유 버튼 */}
                <button className={`${styles.animeDetailModalActionButton} ${styles.animeDetailModalActionButtonSecondary}`}>
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.367 2.684 3 3 0 00-5.367-2.684z" />
                  </svg>
                  <span>공유</span>
                </button>
              </div>

              {/* 줄거리 */}
              <div className={styles.synopsisSection}>
                {(() => {
                  const raw = (((detail as any)?.fullSynopsis ?? (detail as any)?.synopsis ?? "")).toString().trim();
                  const isLong = raw.length > MAX_SYNOPSIS_CHARS;
                  const text = showFullSynopsis || !isLong ? raw : `${raw.slice(0, MAX_SYNOPSIS_CHARS)}…`;
                  return (
                    <div className={styles.synopsisInlineRow}>
                      <span className={styles.synopsisText}>{text || "줄거리 정보가 없습니다."}</span>
                      {isLong && (
                        <button
                          type="button"
                          className={styles.synopsisToggle}
                          onClick={() => {
                            if (!showFullSynopsis) {
                              // 처음 '더보기' 누르면 별도 전체 정보 모달을 띄움
                              setIsFullInfoOpen(true);
                            } else {
                              setShowFullSynopsis(false);
                            }
                          }}
                          aria-expanded={showFullSynopsis}
                        >
                          {showFullSynopsis ? '접기' : '더보기'}
                        </button>
                      )}
                    </div>
                  );
                })()}
              </div>
            {/* 전체 작품 정보 모달 */}
            <AnimeFullInfoModal isOpen={isFullInfoOpen} onClose={() => setIsFullInfoOpen(false)} detail={detail} />
          </div>
        </div>

        {/* 시청 기록 초기화 확인 모달 */}
        {showDeleteConfirm && (
          <div className={styles.confirmModalOverlay}>
            <div className={styles.confirmModal}>
              <h3 className={styles.confirmModalTitle}>시청 기록 초기화</h3>
              <p className={styles.confirmModalMessage}>
                이 작품의 모든 시청 기록이 완전히 삭제됩니다.<br/>
                정말로 초기화하시겠습니까?
              </p>
              <div className={styles.confirmModalButtons}>
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className={styles.confirmModalCancel}
                >
                  취소
                </button>
                <button
                  onClick={handleDeleteWatchHistory}
                  className={styles.confirmModalConfirm}
                >
                  확인
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 탭 메뉴 */}
        <div className={styles.tabMenu}>
          <div className={styles.tabContainer}>
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`${styles.tabButton} ${activeTab === tab.id ? styles.active : ''}`}
              >
                <span className={styles.tabLabel}>{tab.label}</span>
                {tab.count !== null && (
                  <span className={styles.tabCount}>({tab.count})</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* 탭 콘텐츠 */}
        <div className={styles.tabContent}>
          {activeTab === 'episodes' && (
            <div className={styles.episodesSection}>
              <h3 className={styles.episodesTitle}>에피소드 목록</h3>
              <div className={styles.episodesList}>
                {episodes.length > 0 ? (
                  episodes.map((episode: Episode) => (
                  <div 
                    key={episode.id} 
                    className={styles.episodeItem}
                    onClick={() => {
                      // 플레이어 페이지로 이동 (현재 탭에서)
                      router.push(`/player?episodeId=${episode.id}&animeId=${detail?.aniId ?? detail?.id}`);
                      onClose(); // 모달 닫기
                    }}
                    style={{ cursor: 'pointer' }}
                  >
                    <div className={styles.episodeThumbnail}>
                      <img 
                        src={episode.thumbnailUrl || getFallbackEpisodeThumb(episode.episodeNumber)} 
                        alt={episode.title}
                        className={styles.episodeThumbnailImage}
                      />
                    </div>
                    <div className={styles.episodeInfo}>
                      <div className={styles.episodeHeader}>
                        <h4 className={styles.episodeTitle}>
                          {episode.episodeNumber}화
                        </h4>
                        <div className={styles.episodeMeta}>
                          <span>{episode.duration ? `${episode.duration}분` : ''}</span>
                          <span>{episode.createdAt ? String(episode.createdAt).slice(0,10) : ''}</span>
                        </div>
                      </div>
                      <p className={styles.episodeDescription}>
                        {episode.description || ''}
                      </p>
                    </div>
                  </div>
                ))
                ) : (
                  <div className={styles.emptyState}>에피소드 정보가 없습니다.</div>
                )}
              </div>
            </div>
          )}

          {/* 리뷰 탭: ReviewList 항상 마운트되도록 렌더링, 탭 아닐 때는 hidden 처리 */}
          <div className={styles.reviewsSection} style={{ display: activeTab === 'reviews' ? 'block' : 'none' }}>
            {detail?.aniId ? (
              <ReviewList 
                key={detail?.aniId ?? detail?.id} 
                animeId={(detail?.aniId ?? detail?.id) as number} 
                onRatingChange={handleRatingChange}
              />
            ) : (
              <div className={styles.reviewsError}>
                <p className={styles.reviewsErrorMessage}>⚠️ 애니메이션 ID를 찾을 수 없습니다.</p>
                <p className={styles.reviewsErrorDetails}>
                  anime 객체: {JSON.stringify(detail, null, 2)}
                </p>
              </div>
            )}
          </div>

          {activeTab === 'shop' && (
            <div className={styles.shopSection}>
              상점 기능은 준비 중입니다
            </div>
          )}

          {activeTab === 'similar' && (
            <div className={styles.similarSection}>
              {isLoadingSimilar ? (
                <div className={styles.loadingContainer}>
                  비슷한 작품을 불러오는 중...
                </div>
              ) : similarAnimes.length > 0 ? (
                <div className={styles.similarGrid}>
                  {similarAnimes.map((anime: Anime, index: number) => {
                    const a = anime as unknown as ExtendedAnime;
                    const itemId = Number((a as any)?.aniId ?? (a as any)?.id ?? index);
                    const title = (a as any)?.title || (a as any)?.titleEn || (a as any)?.titleJp || '제목 없음';
                    const posterUrl =
                      (a as any)?.posterUrl ||
                      (a as any)?.imageUrl ||
                      (a as any)?.thumbnail ||
                      (a as any)?.posterImage ||
                      '/icons/default-avatar.svg';

                    return (
                      <AnimeCard
                        key={`${itemId}-${title}`}
                        aniId={itemId}
                        title={title}
                        posterUrl={posterUrl}
                        rating={typeof (a as any)?.rating === 'number' ? (a as any).rating : null}
                        badge={Array.isArray((a as any)?.badges) ? (a as any).badges[0] : undefined}
                        onClick={() => {
                          onClose();
                          router.push(`/player?animeId=${itemId}`);
                        }}
                      />
                    );
                  })}
                </div>
              ) : (
                <div className={styles.emptyState}>
                  추천할 작품이 없습니다.
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
