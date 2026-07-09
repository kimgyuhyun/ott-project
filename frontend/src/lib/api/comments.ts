// 동일 오리진 경유

// 댓글 관련 API 함수들

import type { ReviewComment } from "@/types/review";
import type { PagedResponse } from "@/types/common";

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  
  console.log('🌐 [FRONTEND] API 호출:', { url, method: options.method || 'GET' });
  
  const response = await fetch(url, {
    ...options,
    credentials: 'include', // 세션 쿠키 포함
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  console.log('🌐 [FRONTEND] API 응답:', { 
    status: response.status, 
    statusText: response.statusText, 
    ok: response.ok 
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('🌐 [FRONTEND] API 에러:', { 
      status: response.status, 
      statusText: response.statusText, 
      errorText 
    });
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  // 응답이 비어있는지 확인
  const contentType = response.headers.get('content-type');
  if (!contentType || !contentType.includes('application/json')) {
    console.log('🌐 [FRONTEND] API 성공 (빈 응답):', { status: response.status });
    return {} as T; // 빈 객체 반환
  }

  const result = await response.json();
  console.log('🌐 [FRONTEND] API 성공:', result);
  return result;
}

// 리뷰별 댓글 목록 조회
export async function getReviewComments(reviewId: number, page: number = 0, size: number = 10, sort: string = 'latest'): Promise<PagedResponse<ReviewComment>> {
  return apiCall<PagedResponse<ReviewComment>>(`/api/reviews/${reviewId}/comments?page=${page}&size=${size}&sort=${sort}`);
}

// 댓글 작성
export async function createComment(reviewId: number, commentData: {
  content: string;
  parentId?: number;
}) {
  return apiCall(`/api/reviews/${reviewId}/comments`, {
    method: 'POST',
    body: JSON.stringify(commentData),
  });
}

// 댓글 수정
export async function updateComment(reviewId: number, commentId: number, commentData: {
  content: string;
}) {
  return apiCall(`/api/reviews/${reviewId}/comments/${commentId}`, {
    method: 'PUT',
    body: JSON.stringify(commentData),
  });
}

// 댓글 삭제
export async function deleteComment(reviewId: number, commentId: number) {
  return apiCall(`/api/reviews/${reviewId}/comments/${commentId}`, {
    method: 'DELETE',
  });
}

// 댓글 좋아요 토글
export async function toggleCommentLike(reviewId: number, commentId: number) {
  console.log('🌐 [FRONTEND] toggleCommentLike 호출:', { reviewId, commentId });
  return apiCall(`/api/reviews/${reviewId}/comments/${commentId}/like`, {
    method: 'POST',
  });
}

// 댓글 신고
export async function reportComment(reviewId: number, commentId: number) {
  return apiCall(`/api/reviews/${reviewId}/comments/${commentId}/report`, {
    method: 'POST',
  });
}

// 대댓글 목록 조회
export async function getCommentReplies(reviewId: number, commentId: number): Promise<ReviewComment[]> {
  return apiCall<ReviewComment[]>(`/api/reviews/${reviewId}/comments/${commentId}/replies`);
}

// 대댓글 작성
export async function createReply(reviewId: number, commentId: number, content: string) {
  return apiCall(`/api/reviews/${reviewId}/comments/${commentId}/replies`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  });
}
