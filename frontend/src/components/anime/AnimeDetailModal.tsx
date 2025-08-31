"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import ReviewList from "@/components/reviews/ReviewList";
import { getAnimeDetail } from "@/lib/api/anime";
import { getAnimeWatchHistory } from "@/lib/api/user";
import styles from "./AnimeDetailModal.module.css";


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

  useEffect(() => {
    setDetail(anime);
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

  // 사용자의 시청 기록 가져오기
  useEffect(() => {
    if (!isOpen || !detail?.aniId) return;
    
    setIsLoadingHistory(true);
    getAnimeWatchHistory(detail.aniId)
      .then((history) => {
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
      <div className={styles.animeDetailModalContainer}>
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
          {/* 배경 이미지 */}
          <div className={styles.backgroundImage}>
            <div className={styles.backgroundContainer}>
              {/* 애니 캐릭터 이미지 (플레이스홀더) */}
              <div className={styles.characterImage}>
              </div>
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div className={styles.smallPoster}>
            <div className={styles.posterContainer}>
              <img 
                src={detail?.posterUrl || "https://placehold.co/96x128/ff69b4/ffffff?text=LAFTEL+ONLY"} 
                alt={`${detail?.title || '애니메이션'} 포스터`}
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
                  {typeof detail?.rating === 'number' ? detail.rating.toFixed(1) : 'N/A'}
                </span>
              </div>
              <span className={styles.ratingBadge}>
                {detail?.badges?.[0] || 'ONLY'}
              </span>
            </div>

            {/* 애니메이션 제목 */}
            <h1 className={styles.animeTitle}>
              {detail?.title || '제목 없음'}
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
              {/* 이어보기 버튼 - 시청 기록이 있을 때만 표시 */}
              {watchHistory && !watchHistory.completed && (
                <button 
                  onClick={() => {
                    // 이어보기: 마지막으로 본 에피소드부터 재생
                    const position = watchHistory.positionSec > 0 ? `&position=${watchHistory.positionSec}` : '';
                    router.push(`/player?episodeId=${watchHistory.episodeId}&animeId=${detail?.aniId}${position}`);
                    onClose();
                  }}
                  className={`${styles.animeDetailModalActionButton} ${styles.animeDetailModalActionButtonPrimary}`}
                >
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3l14 9-14 9V3z" />
                  </svg>
                  <span>{watchHistory.episodeNumber}화 이어보기</span>
                </button>
              )}
              
              {/* 처음보기 또는 완료된 경우 보러가기 버튼 */}
              {(!watchHistory || watchHistory.completed) && (
                <button 
                  onClick={() => {
                    // 시청 기록이 있지만 완료된 경우: 다음 에피소드부터 시작
                    // 시청 기록이 없는 경우: 1화부터 시작
                    const nextEpisodeId = watchHistory && watchHistory.completed 
                      ? (watchHistory.episodeNumber + 1) 
                      : 1;
                    router.push(`/player?episodeId=${nextEpisodeId}&animeId=${detail?.aniId}`);
                    onClose();
                  }}
                  className={`${styles.animeDetailModalActionButton} ${styles.animeDetailModalActionButtonPrimary}`}
                >
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h1m4 0h1m-6 4h1m-6 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>
                    {watchHistory && watchHistory.completed 
                      ? `${watchHistory.episodeNumber + 1}화 재생하기`
                      : '1화 재생하기'
                    }
                  </span>
                </button>
              )}
              
              {/* 보고싶다 버튼 */}
              <button className={`${styles.animeDetailModalActionButton} ${styles.animeDetailModalActionButtonSecondary}`}>
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                <span>보고싶다</span>
              </button>
              
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
              <p className={styles.synopsisText}>
                {detail?.synopsis || detail?.fullSynopsis || "시놉시스 정보가 없습니다."}
              </p>
            </div>
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
              <ReviewList key={detail?.aniId ?? detail?.id} animeId={(detail?.aniId ?? detail?.id) as number} />
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
              비슷한 작품 기능은 준비 중입니다
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
