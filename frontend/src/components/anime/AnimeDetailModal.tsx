"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import ReviewList from "@/components/reviews/ReviewList";
import { getAnimeDetail, listAnime } from "@/lib/api/anime";
import AnimeCard from "@/components/home/AnimeCard";
import { getAnimeWatchHistory } from "@/lib/api/user";
import { toggleFavorite, isFavorited } from "@/lib/api/favorites";
import styles from "./AnimeDetailModal.module.css";
import AnimeFullInfoModal from "@/components/anime/AnimeFullInfoModal";


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
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');
  const [detail, setDetail] = useState<any>(anime);
  const [watchHistory, setWatchHistory] = useState<any>(null);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [isFavoritedState, setIsFavoritedState] = useState<boolean>(false);
  const [isLoadingFavorite, setIsLoadingFavorite] = useState(false);
  const [currentRating, setCurrentRating] = useState<number | null>(null); // 실시간 평점 상태
  const [similarAnimes, setSimilarAnimes] = useState<any[]>([]);
  const [isLoadingSimilar, setIsLoadingSimilar] = useState(false);
  const [showFullSynopsis, setShowFullSynopsis] = useState<boolean>(false);
  const MAX_SYNOPSIS_CHARS = 180;
  const [isFullInfoOpen, setIsFullInfoOpen] = useState<boolean>(false);

  // 평점 변경 콜백 함수
  const handleRatingChange = (newRating: number) => {
    setCurrentRating(newRating);
    // detail 객체의 rating도 업데이트
    setDetail((prev: any) => ({ ...prev, rating: newRating }));
  };

  useEffect(() => {
    setDetail(anime);
    // 초기 평점 설정
    if (anime?.rating) {
      setCurrentRating(anime.rating);
    }
  }, [anime]);

  useEffect(() => {
    if (!isOpen) return;
    const id = anime?.aniId ?? anime?.id;
    const needsFetch = !Array.isArray(anime?.genres) || anime.genres.length === 0 || !Array.isArray(anime?.episodes);
    if (id && needsFetch) {
      getAnimeDetail(Number(id))
        .then((d) => setDetail((prev: any) => ({ ...prev, ...(d as any) })))
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
            .map((g: any) => Number(g?.id ?? g))
            .filter((v: any) => Number.isFinite(v))
        : [];

      if (genreIds.length === 0) {
        console.log('⚠️ 비슷한 작품 로드: 장르 정보 없음');
        setSimilarAnimes([]);
        return;
      }

      const response: any = await listAnime({ genreIds, sort: 'rating', page: 0, size: 30 });
      const rawItems: any[] = Array.isArray(response?.items)
        ? response.items
        : (Array.isArray(response) ? response : []);

      const baseId = Number(detail?.aniId ?? detail?.id);
      const filtered = rawItems.filter((a: any) => Number(a?.aniId ?? a?.id) !== baseId);

      // 중복 제거 (aniId 기준)
      const seen = new Set<number>();
      const unique = filtered.filter((a: any) => {
        const id = Number(a?.aniId ?? a?.id);
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
    if (!isOpen || !detail?.aniId) return;
    
    console.log('🔍 시청 기록 조회 시작 - animeId:', detail.aniId);
    setIsLoadingHistory(true);
    getAnimeWatchHistory(detail.aniId)
      .then((history) => {
        console.log('🔍 시청 기록 조회 결과:', history);
        setWatchHistory(history);
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
    if (!isOpen || !detail?.aniId) return;
    
    isFavorited(detail.aniId)
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
  console.log('🔍 AnimeDetailModal - anime.aniId:', detail?.aniId);
  console.log('🔍 AnimeDetailModal - anime 타입:', typeof detail);
  console.log('🔍 시청 기록 상태:', {
    watchHistory,
    isLoadingHistory,
    hasWatchHistory: !!watchHistory,
    isCompleted: watchHistory?.completed,
    episodeNumber: watchHistory?.episodeNumber,
    positionSec: watchHistory?.positionSec,
    shouldShowContinue: !isLoadingHistory && watchHistory && !watchHistory.completed,
    shouldShowPlay: !isLoadingHistory && (!watchHistory || watchHistory.completed)
  });

  if (!isOpen) return null;

  const tabs: { id: 'episodes' | 'reviews' | 'shop' | 'similar'; label: string; count: number | null }[] = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: null },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = Array.isArray(detail?.episodes) ? detail.episodes : [];

  return (
    <div className={styles.animeDetailModalOverlay}>
      {/* 배경 오버레이 */}
      <div 
        className={styles.animeDetailModalBackdrop}
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div className={`${styles.animeDetailModalContainer} ${isFullInfoOpen ? styles.dimTabs : ''}`}>
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
                {detail?.badges?.[0] || 'ONLY'}
              </span>
            </div>

            {/* 애니메이션 제목 */}
            <h1 className={styles.animeTitle}>
              {detail?.title || detail?.titleEn || detail?.titleJp || '제목 없음'}
            </h1>

            {/* 장르 및 정보 */}
            <div className={styles.genreSection}>
              {Array.isArray(detail?.genres) && detail.genres.length > 0 ? (
                detail.genres.slice(0, 6).map((g: any, idx: number) => (
                  <span key={idx} className={styles.genreTag}>
                    {g?.name || g}
                  </span>
                ))
              ) : (
                <span className={styles.genreTag}>장르 정보 없음</span>
              )}
              <span className={styles.episodeCount}>
                {(detail?.totalEpisodes ?? detail?.episodeCount ?? '정보 없음')}화
              </span>
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
                        episodeId: watchHistory.episodeId,
                        animeId: detail?.aniId,
                        positionSec: watchHistory.positionSec,
                        episodeNumber: watchHistory.episodeNumber
                      });
                      // 이어보기: 마지막으로 본 에피소드부터 재생
                      const position = watchHistory.positionSec > 0 ? `&position=${watchHistory.positionSec}` : '';
                      const url = `/player?episodeId=${watchHistory.episodeId}&animeId=${detail?.aniId}${position}`;
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
                        isCompleted: watchHistory?.completed,
                        animeId: detail?.aniId
                      });
                      
                      // 시청 기록이 있지만 완료된 경우: 다음 에피소드부터 시작
                      // 시청 기록이 없는 경우: 1화부터 시작
                      let nextEpisodeId = 1;
                      if (watchHistory && watchHistory.completed) {
                        // 완료된 경우 다음 에피소드
                        nextEpisodeId = watchHistory.episodeNumber + 1;
                      }
                      
                      const url = `/player?episodeId=${nextEpisodeId}&animeId=${detail?.aniId}`;
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
                      {watchHistory && watchHistory.completed 
                        ? `${watchHistory.episodeNumber + 1}화 재생하기`
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
                      const newState = await toggleFavorite(detail?.aniId);
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

            {/* 시놉시스 */}
            <div className={styles.synopsisSection}>
              <h3 className={styles.synopsisTitle}>시놉시스</h3>
              {(() => {
                const raw = (detail?.fullSynopsis ?? detail?.synopsis ?? "").toString().trim();
                const isLong = raw.length > MAX_SYNOPSIS_CHARS;
                const text = showFullSynopsis || !isLong ? raw : `${raw.slice(0, MAX_SYNOPSIS_CHARS)}…`;
                return (
                  <div className={styles.synopsisInlineRow}>
                    <span className={styles.synopsisText}>{text || "시놉시스 정보가 없습니다."}</span>
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
                  episodes.map((episode: any) => (
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
                        src={episode.thumbnailUrl || "https://placehold.co/120x80/999/ffffff?text=Episode"} 
                        alt={episode.title}
                        className={styles.episodeThumbnailImage}
                      />
                    </div>
                    <div className={styles.episodeInfo}>
                      <div className={styles.episodeHeader}>
                        <h4 className={styles.episodeTitle}>
                          {episode.title}
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
                  {similarAnimes.map((anime: any, index: number) => {
                    const itemId = Number(anime?.aniId ?? anime?.id ?? index);
                    const title = anime?.title || anime?.titleEn || anime?.titleJp || '제목 없음';
                    const posterUrl =
                      anime?.posterUrl ||
                      anime?.imageUrl ||
                      anime?.thumbnail ||
                      anime?.posterImage ||
                      '/icons/default-avatar.svg';

                    return (
                      <AnimeCard
                        key={`${itemId}-${title}`}
                        aniId={itemId}
                        title={title}
                        posterUrl={posterUrl}
                        rating={typeof anime?.rating === 'number' ? anime.rating : null}
                        badge={Array.isArray(anime?.badges) ? anime.badges[0] : undefined}
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
