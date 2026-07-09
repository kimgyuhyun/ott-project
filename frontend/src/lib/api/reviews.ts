// 동일 오리진 경유

// 리뷰 관련 API 함수들

import type { Review } from "@/types/review";
import type { PagedResponse } from "@/types/common";

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  
  const response = await fetch(url, {
    ...options,
    credentials: 'include', // 세션 쿠키 포함
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  // 본문이 없는 204 등의 응답을 허용
  return response
    .json()
    .catch(() => undefined as unknown as T);
}

// 애니메이션별 리뷰 목록 조회
export async function getAnimeReviews(animeId: number, sort: string = 'latest', page: number = 0, size: number = 10): Promise<PagedResponse<Review>> {
  console.log('🚀 getAnimeReviews 호출:', { animeId, sort, page, size });
  const endpoint = `/api/anime/${animeId}/reviews?sort=${sort}&page=${page}&size=${size}`;
  console.log('🔗 API 엔드포인트:', endpoint);

  try {
    const result = await apiCall<PagedResponse<Review>>(endpoint);
    console.log('✅ getAnimeReviews 성공:', result);
    return result;
  } catch (error) {
    console.error('❌ getAnimeReviews 실패:', error);
    throw error;
  }
}

// 리뷰 작성
export async function createReview(animeId: number, reviewData: {
  content: string;
}) {
  return apiCall(`/api/anime/${animeId}/reviews`, {
    method: 'POST',
    body: JSON.stringify({
      aniId: animeId,
      content: reviewData.content
    }),
  });
}

// 리뷰 수정
export async function updateReview(animeId: number, reviewId: number, reviewData: {
  content: string;
}) {
  return apiCall(`/api/anime/${animeId}/reviews/${reviewId}`, {
    method: 'PUT',
    body: JSON.stringify({
      aniId: animeId,
      content: reviewData.content
    }),
  });
}

// 리뷰 삭제
export async function deleteReview(animeId: number, reviewId: number) {
  return apiCall(`/api/anime/${animeId}/reviews/${reviewId}`, {
    method: 'DELETE',
  });
}

// 리뷰 좋아요 토글
export async function toggleReviewLike(animeId: number, reviewId: number) {
  return apiCall(`/api/anime/${animeId}/reviews/${reviewId}/like`, {
    method: 'POST',
  });
}

// 리뷰 신고
export async function reportReview(animeId: number, reviewId: number) {
  return apiCall(`/api/anime/${animeId}/reviews/${reviewId}/report`, {
    method: 'POST',
  });
}

// Type guard to validate review response structure
export function isValidReviewResponse(data: any): data is { content: any[] } {
  return data && typeof data === 'object' && 'content' in data && Array.isArray(data.content);
}
