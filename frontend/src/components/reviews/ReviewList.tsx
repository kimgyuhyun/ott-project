"use client";
import { useState, useEffect, useRef } from "react";
import { getAnimeReviews, createReview, toggleReviewLike, updateReview, deleteReview, isValidReviewResponse } from "@/lib/api/reviews";
import { createOrUpdateRating, getMyRating, getRatingStats } from "@/lib/api/rating";
import { createComment } from "@/lib/api/comments";
import Star from "@/components/ui/Star";
import { getCurrentUser } from "@/lib/api/auth";
import CommentList from "./CommentList";
import styles from "./ReviewList.module.css";

interface Review {
  id: number;
  userName: string;
  userProfileImage?: string;
  content: string;
  rating: number;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  createdAt?: string;
  updatedAt?: string;
  userId?: number;
}

interface ReviewListProps {
  animeId: number;
}

export default function ReviewList({ animeId }: ReviewListProps) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [editingReview, setEditingReview] = useState<Review | null>(null);
  const [newReview, setNewReview] = useState({ content: '' });
  const [sortBy, setSortBy] = useState('latest');
  const [expandedReviews, setExpandedReviews] = useState<Set<number>>(new Set());
  const [myRating, setMyRating] = useState<number | null>(null);
  const [showLoginRequired, setShowLoginRequired] = useState(false);
  const [showCommentForm, setShowCommentForm] = useState<number | null>(null);
  const halfKeys = ['1.0','1.5','2.0','2.5','3.0','3.5','4.0','4.5','5.0'] as const;
  const [ratingStats, setRatingStats] = useState<Record<string, number>>({ '1.0':0,'1.5':0,'2.0':0,'2.5':0,'3.0':0,'3.5':0,'4.0':0,'4.5':0,'5.0':0 });
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [averageFromApi, setAverageFromApi] = useState<number>(0);
  const [ratingLoading, setRatingLoading] = useState(false);
  const [ratingError, setRatingError] = useState<string | null>(null);
  const scrollYRef = useRef<number>(0);
  const [commentRefreshTrigger, setCommentRefreshTrigger] = useState(0);

  useEffect(() => {
    loadReviews();
    loadRatings();
  }, [animeId, sortBy]);

  // 사용자 정보는 리뷰 작성 시에만 필요하므로 별도로 로드
  useEffect(() => {
    loadCurrentUser();
  }, []);
  // animeId 변경 시 별점 통계 항상 로드
  useEffect(() => {
    console.log('🔄 loadRatings triggered by animeId change:', animeId);
    loadRatings();
  }, [animeId]);

  // 초기 마운트 시 스크롤 복원
  useEffect(() => {
    restoreScroll();
  }, []);

  const saveScroll = () => {
    if (typeof window !== 'undefined') {
      scrollYRef.current = window.scrollY;
    }
  };

  const restoreScroll = () => {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: scrollYRef.current, behavior: 'instant' as ScrollBehavior });
    }
  };

  const loadRatings = async () => {
    try {
      console.log('▶️ loadRatings start for animeId=', animeId);
      setRatingLoading(true);
      setRatingError(null);
      const [stats, mine] = await Promise.all([
        getRatingStats(animeId).catch(() => ({ distribution: { '1.0':0,'1.5':0,'2.0':0,'2.5':0,'3.0':0,'3.5':0,'4.0':0,'4.5':0,'5.0':0 }, average: 0 })),
        getMyRating(animeId).catch(() => null),
      ]);
      const distRaw = (stats as any)?.distribution || {};
      const dist: Record<string, number> = { '1.0':0,'1.5':0,'2.0':0,'2.5':0,'3.0':0,'3.5':0,'4.0':0,'4.5':0,'5.0':0 };
      Object.keys(distRaw).forEach(k => {
        const key = String(k);
        if (halfKeys.includes(key as any)) {
          const v = (distRaw as any)[k];
          dist[key] = typeof v === 'number' ? v : Number(v) || 0;
        }
      });
      const avg = (stats as any)?.average;
      setRatingStats(dist);
      setAverageFromApi(typeof avg === 'number' ? avg : 0);
      if (typeof mine === 'number' && mine > 0) setMyRating(mine as number);
      const values = halfKeys.map(k => dist[k]);
      const totalFromDist = values.reduce((a,b)=>a+b,0);
      const maxCount = Math.max(...values, 0);
      const pcts = halfKeys.map(s => {
        const c = dist[s] ?? 0;
        return maxCount > 0 ? Math.round((c / maxCount) * 100) : 0;
      });
      console.log('✅ loadRatings done: dist=', dist, 'avg=', avg, 'mine=', mine, 'maxCount=', maxCount, 'totalFromDist=', totalFromDist, 'pcts=', pcts);
    } catch (e) {
      console.log('평점 정보 로드 실패:', e);
      setRatingError('분포를 불러오지 못했습니다.');
    } finally { setRatingLoading(false); }
  };

  const loadCurrentUser = async () => {
    try {
      const user = await getCurrentUser();
      setCurrentUser(user);
    } catch (error) {
      console.log('사용자 정보 로드 실패 (리뷰 조회에는 영향 없음):', error);
      // 사용자 정보 로드 실패해도 리뷰 조회는 계속 진행
    }
  };

  const loadReviews = async () => {
    try {
      setIsLoading(true);
      saveScroll();
      console.log('🔍 리뷰 로드 시작 - animeId:', animeId, 'sortBy:', sortBy);
      
      const data = await getAnimeReviews(animeId, sortBy);
      console.log('📡 API 응답 데이터:', data);
      console.log('📊 데이터 타입:', typeof data);
      console.log('🔑 데이터 키들:', data ? Object.keys(data) : 'null');
      
      // 백엔드 응답 구조에 맞춰 정확히 파싱
      let reviewsData: Review[] = [];
      
      if (data && typeof data === 'object') {
        if ('content' in data && Array.isArray(data.content)) {
          console.log('✅ PagedResponse 구조로 파싱됨');
          reviewsData = data.content;
        } else if ('items' in data && Array.isArray(data.items)) {
          console.log('✅ Items 구조로 파싱됨');
          reviewsData = data.items;
        } else if (Array.isArray(data)) {
          console.log('✅ 직접 배열로 파싱됨');
          reviewsData = data;
        } else {
          console.warn('⚠️ 예상치 못한 데이터 구조:', data);
          reviewsData = [];
        }
      } else {
        console.warn('⚠️ 데이터가 null이거나 undefined');
        reviewsData = [];
      }
      
      console.log('🎯 최종 파싱된 리뷰 데이터:', reviewsData);
      console.log('📝 리뷰 개수:', reviewsData.length);
      
      // 첫 번째 리뷰의 날짜 필드 확인
      if (reviewsData.length > 0) {
        const firstReview = reviewsData[0];
        console.log('📅 첫 번째 리뷰 날짜 정보:', {
          createdAt: firstReview.createdAt,
          updatedAt: firstReview.updatedAt,
          hasCreatedAt: 'createdAt' in firstReview,
          hasUpdatedAt: 'updatedAt' in firstReview
        });
      }
      
      setReviews(reviewsData);
      
      // 사용자 평점은 Rating API에서 로드함
      
    } catch (error) {
      console.error('❌ 리뷰 로드 실패:', error);
      setReviews([]);
    } finally {
      setIsLoading(false);
      // 데이터 반영 후 스크롤 복원
      setTimeout(() => restoreScroll(), 0);
    }
  };

  // 평점 분포 계산
  const getRatingDistribution = () => ratingStats;

  // 평균 평점 계산
  const getAverageRating = () => (typeof averageFromApi === 'number' ? Math.round(averageFromApi * 10) / 10 : 0);

  // 평점 텍스트 변환
  const getRatingText = (rating: number) => {
    if (rating >= 4.5) return '최고예요';
    if (rating >= 4.0) return '재미있어요';
    if (rating >= 3.0) return '볼만해요';
    if (rating >= 2.0) return '그럭저럭';
    return '별로예요';
  };

  const formatRelativeTime = (iso?: string, updatedIso?: string) => {
    if (!iso) return '';
    try {
      // UTC 시간을 로컬 시간으로 변환
      const created = new Date(iso + 'Z'); // Z를 추가해서 UTC로 명시
      const updated = updatedIso ? new Date(updatedIso + 'Z') : null;
      const diff = Date.now() - created.getTime();
      const minutes = Math.floor(diff / 60000);
      const hours = Math.floor(minutes / 60);
      const days = Math.floor(hours / 24);
      const months = Math.floor(days / 30);
      const years = Math.floor(days / 365);
      
      let base = '';
      if (years > 0) {
        base = `${years}년 전`;
      } else if (months > 0) {
        base = `${months}개월 전`;
      } else if (days > 0) {
        base = `${days}일 전`;
      } else if (hours > 0) {
        base = `${hours}시간 전`;
      } else if (minutes > 0) {
        base = `${minutes}분 전`;
      } else {
        base = '방금 전';
      }
      
      if (updated && Math.abs(updated.getTime() - created.getTime()) > 60_000) {
        base += ' (수정됨)';
      }
      return base;
    } catch {
      return '';
    }
  };

  const handleCreateReview = async () => {
    if (!currentUser) {
      setShowLoginRequired(true);
      setTimeout(() => setShowLoginRequired(false), 3000);
      return;
    }
    
    if (!newReview.content.trim()) return;
    
    try {
      await createReview(animeId, { content: newReview.content });
      setNewReview({ content: '' });
      await loadReviews();
    } catch (error) {
      console.error('리뷰 작성 실패:', error);
    }
  };

  const handleUpdateReview = async () => {
    if (!editingReview || !editingReview.content.trim()) return;
    
    saveScroll();
    const prev = reviews;
    const targetId = editingReview.id;
    const newContent = editingReview.content;

    setReviews(prevReviews =>
      prevReviews.map(r => (r.id === targetId ? { ...r, content: newContent } : r))
    );
    setEditingReview(null);

    try {
      await updateReview(animeId, targetId, { content: newContent });
      restoreScroll();
    } catch (error) {
      console.error('리뷰 수정 실패:', error);
      setReviews(prev);
      loadReviews();
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const handleDeleteReview = async (reviewId: number) => {
    if (!confirm('정말로 이 리뷰를 삭제하시겠습니까?')) return;
    
    saveScroll();
    const prev = reviews;
    setReviews(prevReviews => prevReviews.filter(r => r.id !== reviewId));

    try {
      await deleteReview(animeId, reviewId);
      restoreScroll();
    } catch (error) {
      console.error('리뷰 삭제 실패:', error);
      setReviews(prev);
      loadReviews();
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const handleToggleLike = async (reviewId: number) => {
    if (!currentUser) {
      setShowLoginRequired(true);
      setTimeout(() => setShowLoginRequired(false), 3000);
      return;
    }
    
    saveScroll();
    const prev = reviews;
    // 낙관적 토글
    setReviews(prevReviews => prevReviews.map(r => {
      if (r.id !== reviewId) return r;
      const liked = !r.isLikedByCurrentUser;
      return {
        ...r,
        isLikedByCurrentUser: liked,
        likeCount: r.likeCount + (liked ? 1 : -1),
      };
    }));

    try {
      await toggleReviewLike(animeId, reviewId);
      // 성공 시 추가 작업 없음
      restoreScroll();
    } catch (error) {
      console.error('좋아요 토글 실패:', error);
      setReviews(prev);
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const toggleReviewExpansion = (reviewId: number) => {
    setExpandedReviews(prev => {
      const newSet = new Set(prev);
      if (newSet.has(reviewId)) {
        newSet.delete(reviewId);
      } else {
        newSet.add(reviewId);
      }
      return newSet;
    });
  };

  const handleRatingClick = async (rating: number) => {
    if (!currentUser) {
      setShowLoginRequired(true);
      setTimeout(() => setShowLoginRequired(false), 3000);
      return;
    }
    saveScroll();
    const prevMy = myRating;
    const prevStats = { ...ratingStats };

    // 낙관적 업데이트: 분포/내 별점
    // 0.5 스텝 유지
    const halfStep = Math.max(0.5, Math.min(5, Math.round(rating * 2) / 2));
    setMyRating(halfStep); // 즉시 UI 반영
    setRatingStats(curr => {
      const next: Record<string, number> = { ...curr };
      const prevKey = typeof prevMy === 'number' ? (Math.round(prevMy * 2) / 2).toFixed(1) : undefined;
      const newKey = (Math.round(halfStep * 2) / 2).toFixed(1);
      if (prevKey && next[prevKey] !== undefined) next[prevKey] = Math.max(0, (next[prevKey] || 0) - 1);
      if (next[newKey] !== undefined) next[newKey] = (next[newKey] || 0) + 1;
      return next;
    });

    try {
      // API 호출과 동기화
      await createOrUpdateRating(animeId, halfStep);
      // 서버 기준 재조회 후 myRating도 보정
      await loadRatings();
      setMyRating(halfStep);
      restoreScroll();
    } catch (e) {
      console.error('별점 저장 실패:', e);
      setMyRating(prevMy);
      setRatingStats(prevStats);
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const ratingDistribution = ratingStats; // 문자열 키 ('1.0'..'5.0' by 0.5)
  const averageRating = getAverageRating();
  const valuesForCalc = halfKeys.map(k => ratingDistribution[k] ?? 0);
  const totalFromDist = valuesForCalc.reduce((a,b)=>a+b,0);
  const totalRatings = totalFromDist;
  const maxCount = Math.max(...valuesForCalc, 0);
  const hasAny = valuesForCalc.some(v => v > 0);

  if (isLoading) {
    return <div className={styles.loadingContainer}>리뷰를 불러오는 중...</div>;
  }

  return (
    <div className={styles.mainContainer}>
      {/* 평점 정보 섹션 */}
      <div className={styles.reviewCard}>
        <div className={styles.ratingGrid}>
          {/* 내 별점 */}
          <div className={styles.ratingSection}>
            <h3 className={styles.ratingTitle}>내 별점</h3>
            <div className={styles.ratingSubtitle}>{myRating ? getRatingText(myRating) : '평점 없음'}</div>
            <div className={styles.myRatingInput}
                 onMouseLeave={() => setHoverRating(null)}>
              {[1, 2, 3, 4, 5].map((index) => {
                const active = hoverRating ?? myRating ?? 0;
                const score = active || 0;
                const filled = score >= index ? 1 : Math.max(0, Math.min(1, score - (index - 1)));
                return (
                  <button
                    key={index}
                    onClick={(e) => {
                      const rect = (e.currentTarget as HTMLButtonElement).getBoundingClientRect();
                      const half = (e.clientX - rect.left) / rect.width <= 0.5 ? 0.5 : 1.0;
                      handleRatingClick(index - 1 + half);
                    }}
                    onMouseMove={(e) => {
                      const rect = (e.currentTarget as HTMLButtonElement).getBoundingClientRect();
                      const half = (e.clientX - rect.left) / rect.width <= 0.5 ? 0.5 : 1.0;
                      const next = index - 1 + half;
                      if (hoverRating !== next) setHoverRating(next);
                    }}
                    onMouseEnter={(e) => {
                      const rect = (e.currentTarget as HTMLButtonElement).getBoundingClientRect();
                      const half = (e.clientX - rect.left) / rect.width <= 0.5 ? 0.5 : 1.0;
                      setHoverRating(index - 1 + half);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
                        e.preventDefault();
                        const next = Math.min(5, Math.round(((myRating || 0) + 0.5) * 2) / 2);
                        handleRatingClick(next);
                      } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
                        e.preventDefault();
                        const prev = Math.max(0.5, Math.round(((myRating || 0) - 0.5) * 2) / 2);
                        handleRatingClick(prev);
                      }
                    }}
                    aria-label={`별 ${index}`}
                    style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
                  >
                    <Star value={filled} size={28} color="#8B5CF6" emptyColor="#E5E7EB" />
                  </button>
                );
              })}
            </div>
          </div>

          {/* 평균 별점 */}
          <div className={styles.ratingSection}>
            <h3 className={styles.ratingTitle}>평균 별점</h3>
            <div className={styles.ratingValue}>{averageRating.toFixed(1)}</div>
            <div className={styles.ratingSubtitle}>{totalRatings}개의 별점</div>
            <div className={styles.ratingInput}>
              {[1, 2, 3, 4, 5].map((index) => {
                const score = averageRating;
                const filled = score >= index ? 1 : Math.max(0, Math.min(1, score - (index - 1)));
                return (
                  <Star key={index} value={filled} size={28} color="#8B5CF6" emptyColor="#E5E7EB" />
                );
              })}
            </div>
          </div>

          {/* 평점 분포 (세로 히스토그램, 전체 대비 비율) */}
          {hasAny && (
            <div className={styles.ratingSection}>
              <h3 className={styles.ratingTitle}>평점 분포</h3>
              {(() => {
                const chartHeight = 140; // px
                const minBarPx = 4; // 시각 최소 높이
                return (
                  <div className={styles.chartContainer} style={{ height: chartHeight + 'px' }}>
                    {halfKeys.map((label) => {
                      const count = ratingDistribution[label] ?? 0;
                      const ratio = totalRatings > 0 ? (count / totalRatings) : 0;
                      let barPx = Math.round(ratio * chartHeight * 0.8); // 최대 80%
                      if (count > 0 && barPx < minBarPx) barPx = minBarPx;
                      const isIntegerLabel = label.endsWith('.0');
                      return (
                        <div key={label} className={styles.chartBar} aria-label={`별 ${label}: ${count}개 (${Math.round(ratio*100)}%)`}>
                          <div className={styles.chartBarInner} style={{ height: chartHeight + 'px' }}>
                            <div className={styles.chartBarElement} style={{ height: barPx + 'px', backgroundColor: '#8B5CF6' }} />
                          </div>
                          {isIntegerLabel ? (
                            <div className={styles.chartBarLabel}>{label}</div>
                          ) : (
                            <div className={styles.chartBarSpacer}>.</div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                );
              })()}
            </div>
          )}
          {ratingLoading && (
            <div className={styles.chartLegend}>
              <div className={styles.chartLegendGrid}>
                {[1,2,3,4,5].map(k => (
                  <div key={k} className={styles.chartLegendItem} />
                ))}
              </div>
            </div>
          )}
          {ratingError && (
            <div className={styles.errorMessage}>
              {ratingError}
              <button onClick={loadRatings} className={styles.retryButton}>재시도</button>
            </div>
          )}
        </div>

        {/* 리뷰 작성 - 항상 표시 */}
        <div className={styles.reviewFormSection}>
          {!currentUser && (
            <div className={styles.loginRequiredMessage}>
              <p className={styles.loginRequiredText}>리뷰를 작성하려면 로그인이 필요합니다.</p>
            </div>
          )}
          <textarea
            value={newReview.content}
            onChange={(e) => setNewReview(prev => ({ ...prev, content: e.target.value }))}
            placeholder="이 작품에 대한 내 평가를 남겨보세요!"
            className={styles.reviewTextarea}
            rows={4}
          />
          <div className={styles.formButtons}>
            <button
              onClick={handleCreateReview}
              disabled={!newReview.content.trim()}
              className={styles.submitButton}
            >
              작성하기
            </button>
          </div>
        </div>
      </div>

      {/* 정렬 옵션 (오른쪽 정렬) */}
      <div className={styles.sortSection}>
        <div className={styles.sortButtons}>
          <button
            onClick={() => setSortBy('latest')}
            className={`${styles.sortButton} ${sortBy === 'latest' ? styles.sortButtonActive : styles.sortButtonInactive}`}
          >
            최신순
          </button>
          <button
            onClick={() => setSortBy('rating')}
            className={`${styles.sortButton} ${sortBy === 'rating' ? styles.sortButtonActive : styles.sortButtonInactive}`}
          >
            평점순
          </button>
        </div>
      </div>

      {/* 로그인 필요 메시지 */}
      {showLoginRequired && (
        <div className={styles.warningMessage}>
          로그인이 필요합니다.
        </div>
      )}

      {/* 리뷰 목록 */}
      <div className={styles.reviewsList}>
        {reviews.map((review) => (
          <div key={review.id} className={styles.reviewItem}>
            {editingReview?.id === review.id ? (
              // 수정 모드
              <div className={styles.editForm}>
                <div className={styles.editFormContent}>
                  <div className={styles.formField}>
                    <label className={styles.formLabel}>평점</label>
                    <div className={styles.starRating}>
                      {[1, 2, 3, 4, 5].map((star) => (
                        <button
                          key={star}
                          onClick={() => setEditingReview(prev => prev ? { ...prev, rating: star } : null)}
                          className={`${styles.star} ${editingReview.rating >= star ? styles.starActive : styles.starInactive}`}
                        >
                          ★
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className={styles.formField}>
                    <label className={styles.formLabel}>내용</label>
                    <textarea
                      value={editingReview.content}
                      onChange={(e) => setEditingReview(prev => prev ? { ...prev, content: e.target.value } : null)}
                      className={styles.editTextarea}
                      rows={4}
                    />
                  </div>
                  <div className={styles.editButtons}>
                    <button
                      onClick={() => setEditingReview(null)}
                      className={styles.cancelButton}
                    >
                      취소
                    </button>
                    <button
                      onClick={handleUpdateReview}
                      className={styles.saveButton}
                    >
                      수정
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              // 표시 모드
              <div className={styles.reviewContent}>
                <div className={styles.reviewHeader}>
                  <div className={styles.userRating}>
                    <div className={styles.userRatingStars}>
                      {[1, 2, 3, 4, 5].map((star) => {
                        const isMine = !!(currentUser && review.userName === (currentUser as any).username);
                        const scoreVal = isMine && typeof myRating === 'number' && myRating > 0 ? myRating as number : (Number(review.rating) || 0);
                        const filled = scoreVal >= star ? 1 : Math.max(0, Math.min(1, scoreVal - (star - 1)));
                        return <Star key={star} value={filled} size={18} color="#8B5CF6" emptyColor="#E5E7EB" />;
                      })}
                    </div>
                    <span className={styles.userRatingValue}>{
                      (() => {
                        const isMine = !!(currentUser && review.userName === (currentUser as any).username);
                        const scoreVal = isMine && typeof myRating === 'number' && myRating > 0 ? myRating as number : (Number(review.rating) || 0);
                        return scoreVal.toFixed(1);
                      })()
                    }</span>
                  </div>
                  <div className={styles.reviewMeta}>
                    <div className={styles.userNameSection}>
                      <img 
                        src={review.userProfileImage || ''} 
                        alt={review.userName} 
                        className={styles.userNameAvatar}
                        onError={(e) => {
                          console.error('❌ 닉네임 프로필 이미지 로딩 실패:', review.userProfileImage);
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                      <span className={styles.userName}>{review.userName}</span>
                    </div>

                    {currentUser && ((typeof review.userId === 'number' && (currentUser as any).id === review.userId) || review.userName === (currentUser as any).username) && (
                      <>
                        <button
                          onClick={() => setEditingReview(review)}
                          className={styles.actionButton}
                        >
                          수정
                        </button>
                        <button
                          onClick={() => handleDeleteReview(review.id)}
                          className={styles.actionButton}
                        >
                          삭제
                        </button>
                      </>
                    )}
                  </div>
                </div>
                
                {/* 별점과 내용 사이에 날짜 표시 */}
                <div className={styles.reviewDateSection}>
                  <span className={styles.reviewDate}>{formatRelativeTime(review.createdAt, review.updatedAt)}</span>
                </div>
                
                <div className={styles.reviewText}>
                  {expandedReviews.has(review.id) ? (
                    <div>
                      <p className={styles.reviewTextExpanded}>{review.content}</p>
                      <button
                        onClick={() => toggleReviewExpansion(review.id)}
                        className={styles.expandButton}
                      >
                        접기
                      </button>
                    </div>
                  ) : (
                    <div>
                      <p className={styles.reviewTextCollapsed}>{review.content}</p>
                      {review.content.length > 150 && (
                        <button
                          onClick={() => toggleReviewExpansion(review.id)}
                          className={styles.expandButton}
                        >
                          더보기
                        </button>
                      )}
                    </div>
                  )}
                </div>
                
                <div className={styles.reviewActionButtons}>
                  <button
                    onClick={() => handleToggleLike(review.id)}
                    className={`${styles.likeButton} ${
                      review.isLikedByCurrentUser ? styles.likeButtonActive : styles.likeButtonInactive
                    }`}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className={styles.likeIcon}>
                      <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                    </svg>
                    <span>{review.likeCount}</span>
                  </button>
                  
                  {currentUser && (
                    <button
                      onClick={() => setShowCommentForm(showCommentForm === review.id ? null : review.id)}
                      className={styles.commentButton}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className={styles.commentIcon}>
                        <path d="M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h4l4 4 4-4h4c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
                      </svg>
                      댓글
                    </button>
                  )}
                </div>
                
                {/* 리뷰 댓글 입력 폼 */}
                {showCommentForm === review.id && (
                  <div className={styles.reviewCommentForm}>
                    <textarea
                      value={newReview.content}
                      onChange={(e) => setNewReview(prev => ({ ...prev, content: e.target.value }))}
                      placeholder="댓글을 작성해주세요..."
                      className={styles.reviewCommentTextarea}
                      rows={3}
                    />
                    <div className={styles.reviewCommentFormButtons}>
                      <button
                        onClick={() => {
                          setShowCommentForm(null);
                          setNewReview({ content: '' });
                        }}
                        className={styles.cancelButton}
                      >
                        취소
                      </button>
                      <button
                        onClick={async () => {
                          if (!newReview.content.trim()) return;
                          try {
                            saveScroll();
                            await createComment(review.id, { content: newReview.content });
                            setNewReview({ content: '' });
                            setShowCommentForm(null);
                            // 해당 리뷰의 댓글만 새로고침
                            setCommentRefreshTrigger(prev => prev + 1);
                            setTimeout(() => restoreScroll(), 0);
                          } catch (error) {
                            console.error('댓글 작성 실패:', error);
                            setTimeout(() => restoreScroll(), 0);
                          }
                        }}
                        disabled={!newReview.content.trim()}
                        className={styles.saveButton}
                      >
                        작성
                      </button>
                    </div>
                  </div>
                )}
                
                <CommentList 
                  reviewId={review.id} 
                  myRating={myRating ?? 0} 
                  refreshTrigger={commentRefreshTrigger}
                  onCommentCreated={() => {
                    // 댓글 작성 후 스크롤 위치 복원
                    setTimeout(() => restoreScroll(), 0);
                  }}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      {reviews.length === 0 && (
        <div className={styles.emptyState}>
          아직 리뷰가 없습니다. 첫 번째 리뷰를 작성해보세요!
        </div>
      )}
    </div>
  );
}
