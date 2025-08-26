"use client";
import { useState, useEffect, useRef } from "react";
import { getAnimeReviews, createReview, toggleReviewLike, updateReview, deleteReview, isValidReviewResponse } from "@/lib/api/reviews";
import { createOrUpdateRating, getMyRating, getRatingStats } from "@/lib/api/rating";
import Star from "@/components/ui/Star";
import { getCurrentUser } from "@/lib/api/auth";
import CommentList from "./CommentList";

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
  const halfKeys = ['1.0','1.5','2.0','2.5','3.0','3.5','4.0','4.5','5.0'] as const;
  const [ratingStats, setRatingStats] = useState<Record<string, number>>({ '1.0':0,'1.5':0,'2.0':0,'2.5':0,'3.0':0,'3.5':0,'4.0':0,'4.5':0,'5.0':0 });
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [averageFromApi, setAverageFromApi] = useState<number>(0);
  const [ratingLoading, setRatingLoading] = useState(false);
  const [ratingError, setRatingError] = useState<string | null>(null);
  const scrollYRef = useRef<number>(0);

  useEffect(() => {
    loadReviews();
    loadCurrentUser();
    loadRatings();
  }, [animeId, sortBy]);
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
      console.log('사용자 정보 로드 실패:', error);
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
    return <div className="text-center py-8">리뷰를 불러오는 중...</div>;
  }

  return (
    <div className="space-y-6">
      {/* 평점 정보 섹션 */}
      <div className="bg-white rounded-lg p-6 border border-gray-200">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-end">
          {/* 내 별점 */}
          <div className="text-center">
            <h3 className="text-lg font-semibold text-gray-800 mb-2">내 별점</h3>
            <div className="text-3xl font-bold text-purple-600 mb-1">{myRating ?? '-'}</div>
            <div className="text-sm text-gray-600 mb-3">{myRating ? getRatingText(myRating) : '평점 없음'}</div>
            <div className="flex justify-center gap-3"
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
                    className="w-7 h-7 inline-flex items-center justify-center cursor-pointer pointer-events-auto"
                  >
                    <Star value={filled} size={28} color="#8B5CF6" emptyColor="#E5E7EB" />
                  </button>
                );
              })}
            </div>
          </div>

          {/* 평균 별점 */}
          <div className="text-center">
            <h3 className="text-lg font-semibold text-gray-800 mb-2">평균 별점</h3>
            <div className="text-3xl font-bold text-purple-600 mb-1">{averageRating.toFixed(1)}</div>
            <div className="text-sm text-gray-600 mb-3">{totalRatings}개의 별점</div>
            <div className="flex justify-center gap-2">
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
            <div className="text-center">
              <h3 className="text-lg font-semibold text-gray-800 mb-4">평점 분포</h3>
              {(() => {
                const chartHeight = 140; // px
                const minBarPx = 4; // 시각 최소 높이
                return (
                  <div className="flex items-end justify-between px-3" style={{ height: chartHeight + 'px' }}>
                    {halfKeys.map((label) => {
                      const count = ratingDistribution[label] ?? 0;
                      const ratio = totalRatings > 0 ? (count / totalRatings) : 0;
                      let barPx = Math.round(ratio * chartHeight * 0.8); // 최대 80%
                      if (count > 0 && barPx < minBarPx) barPx = minBarPx;
                      const isIntegerLabel = label.endsWith('.0');
                      return (
                        <div key={label} className="flex flex-col items-center w-9" aria-label={`별 ${label}: ${count}개 (${Math.round(ratio*100)}%)`}>
                          <div className="flex items-end" style={{ height: chartHeight + 'px' }}>
                            <div className="w-3 rounded-t transition-[height] duration-150 hover:brightness-110" style={{ height: barPx + 'px', backgroundColor: '#8B5CF6' }} />
                          </div>
                          {isIntegerLabel ? (
                            <div className="text-xs text-gray-900 font-medium mt-1">{label}</div>
                          ) : (
                            <div className="text-xs mt-1" style={{ visibility: 'hidden' }}>.</div>
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
            <div className="mt-2 px-3">
              <div className="grid grid-cols-5 gap-3">
                {[1,2,3,4,5].map(k => (
                  <div key={k} className="w-full h-3 rounded-full bg-gray-200 animate-pulse" />
                ))}
              </div>
            </div>
          )}
          {ratingError && (
            <div className="mt-2 text-center text-sm text-red-600">
              {ratingError}
              <button onClick={loadRatings} className="ml-2 px-2 py-0.5 bg-purple-600 text-white rounded">재시도</button>
            </div>
          )}
        </div>

        {/* 리뷰 작성 - 항상 표시 */}
        <div className="mt-6">
          {!currentUser && (
            <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
              <p className="text-blue-800 text-sm">리뷰를 작성하려면 로그인이 필요합니다.</p>
            </div>
          )}
          <textarea
            value={newReview.content}
            onChange={(e) => setNewReview(prev => ({ ...prev, content: e.target.value }))}
            placeholder="이 작품에 대한 내 평가를 남겨보세요!"
            className="w-full p-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            rows={4}
          />
          <div className="flex justify-end mt-3">
            <button
              onClick={handleCreateReview}
              disabled={!newReview.content.trim()}
              className="px-6 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              작성하기
            </button>
          </div>
        </div>
      </div>

      {/* 정렬 옵션 (오른쪽 정렬) */}
      <div className="flex justify-end items-center">
        <div className="flex space-x-2">
          <button
            onClick={() => setSortBy('latest')}
            className={`px-3 py-1 rounded ${sortBy === 'latest' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
          >
            최신순
          </button>
          <button
            onClick={() => setSortBy('rating')}
            className={`px-3 py-1 rounded ${sortBy === 'rating' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
          >
            평점순
          </button>
        </div>
      </div>

      {/* 로그인 필요 메시지 */}
      {showLoginRequired && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-yellow-800">
          로그인이 필요합니다.
        </div>
      )}

      {/* 리뷰 목록 */}
      <div className="space-y-4">
        {reviews.map((review) => (
          <div key={review.id} className="border border-gray-200 rounded-lg">
            {editingReview?.id === review.id ? (
              // 수정 모드
              <div className="p-4">
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">평점</label>
                    <div className="flex space-x-1">
                      {[1, 2, 3, 4, 5].map((star) => (
                        <button
                          key={star}
                          onClick={() => setEditingReview(prev => prev ? { ...prev, rating: star } : null)}
                          className={`text-2xl ${editingReview.rating >= star ? 'text-yellow-500' : 'text-gray-300'}`}
                        >
                          ★
                        </button>
                      ))}
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">내용</label>
                    <textarea
                      value={editingReview.content}
                      onChange={(e) => setEditingReview(prev => prev ? { ...prev, content: e.target.value } : null)}
                      className="w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                      rows={4}
                    />
                  </div>
                  <div className="flex justify-end space-x-2">
                    <button
                      onClick={() => setEditingReview(null)}
                      className="px-4 py-2 text-gray-600 hover:text-gray-800 transition-colors"
                    >
                      취소
                    </button>
                    <button
                      onClick={handleUpdateReview}
                      className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
                    >
                      수정
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              // 표시 모드
              <div className="p-4">
                <div className="flex justify-between items-start mb-1">
                  <div className="flex items-center space-x-2">
                    {/* 사용자 아바타 (이미지 우선, 없으면 이니셜) */}
                    {review.userProfileImage ? (
                      <img src={review.userProfileImage} alt={review.userName} className="w-8 h-8 rounded-full object-cover" />
                    ) : (
                      <div className="w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center text-xs text-white">
                        {review.userName?.[0] || '?'}
                      </div>
                    )}
                    <div className="flex items-center gap-1.5">
                      {[1, 2, 3, 4, 5].map((star) => {
                        const isMine = !!(currentUser && review.userName === (currentUser as any).username);
                        const scoreVal = isMine && typeof myRating === 'number' && myRating > 0 ? myRating as number : (Number(review.rating) || 0);
                        const filled = scoreVal >= star ? 1 : Math.max(0, Math.min(1, scoreVal - (star - 1)));
                        return <Star key={star} value={filled} size={18} color="#8B5CF6" emptyColor="#E5E7EB" />;
                      })}
                    </div>
                    <span className="text-sm font-semibold text-gray-800">{
                      (() => {
                        const isMine = !!(currentUser && review.userName === (currentUser as any).username);
                        const scoreVal = isMine && typeof myRating === 'number' && myRating > 0 ? myRating as number : (Number(review.rating) || 0);
                        return scoreVal.toFixed(1);
                      })()
                    }</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs text-gray-500 mr-2">{review.createdAt ? (new Date(review.createdAt).toLocaleDateString()) : ''}{review.updatedAt && review.updatedAt !== review.createdAt ? ' (수정됨)' : ''}</span>
                    <span className="font-semibold text-gray-800">{review.userName}</span>
                    <button
                      onClick={() => handleToggleLike(review.id)}
                      className={`flex items-center space-x-1 px-2 py-1 rounded text-sm transition-colors ${
                        review.isLikedByCurrentUser
                          ? 'bg-blue-100 text-blue-600 hover:bg-blue-200'
                          : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                      }`}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-4 h-4">
                        <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                      </svg>
                      <span>{review.likeCount}</span>
                    </button>
                    {currentUser && review.userName === (currentUser as any).username && (
                      <>
                        <button
                          onClick={() => setEditingReview(review)}
                          className="px-2 py-1 bg-blue-100 text-blue-600 rounded text-sm hover:bg-blue-200 transition-colors"
                        >
                          수정
                        </button>
                        <button
                          onClick={() => handleDeleteReview(review.id)}
                          className="px-2 py-1 bg-red-100 text-red-600 rounded text-sm hover:bg-red-200 transition-colors"
                        >
                          삭제
                        </button>
                      </>
                    )}
                  </div>
                </div>
                
                <div className="text-gray-700">
                  {expandedReviews.has(review.id) ? (
                    <div>
                      <p className="whitespace-pre-wrap">{review.content}</p>
                      <button
                        onClick={() => toggleReviewExpansion(review.id)}
                        className="text-purple-600 hover:text-purple-800 text-sm mt-2"
                      >
                        접기
                      </button>
                    </div>
                  ) : (
                    <div>
                      <p className="line-clamp-3">{review.content}</p>
                      {review.content.length > 150 && (
                        <button
                          onClick={() => toggleReviewExpansion(review.id)}
                          className="text-purple-600 hover:text-purple-800 text-sm mt-2"
                        >
                          더보기
                        </button>
                      )}
                    </div>
                  )}
                </div>
                
                <CommentList reviewId={review.id} myRating={myRating ?? 0} />
              </div>
            )}
          </div>
        ))}
      </div>

      {reviews.length === 0 && (
        <div className="text-center py-12 text-gray-500">
          아직 리뷰가 없습니다. 첫 번째 리뷰를 작성해보세요!
        </div>
      )}
    </div>
  );
}
