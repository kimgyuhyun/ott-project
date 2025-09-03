// 동일 오리진 경유

// 에피소드 댓글 관련 API 함수들
import { EpisodeComment, PagedEpisodeCommentsResponse } from '@/types/episodeComments';

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

// 에피소드별 댓글 목록 조회
export async function getEpisodeComments(episodeId: number, page: number = 0, size: number = 10, sort: string = 'latest'): Promise<PagedEpisodeCommentsResponse> {
  return apiCall(`/api/episodes/${episodeId}/comments?page=${page}&size=${size}&sort=${sort}`);
}

// 댓글 작성
export async function createEpisodeComment(episodeId: number, commentData: {
  content: string;
  parentId?: number;
}): Promise<number> {
  return apiCall(`/api/episodes/${episodeId}/comments`, {
    method: 'POST',
    body: JSON.stringify(commentData),
  });
}

// 댓글 수정
export async function updateEpisodeComment(episodeId: number, commentId: number, commentData: {
  content: string;
}) {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}`, {
    method: 'PUT',
    body: JSON.stringify(commentData),
  });
}

// 댓글 삭제
export async function deleteEpisodeComment(episodeId: number, commentId: number) {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}`, {
    method: 'DELETE',
  });
}

// 댓글 좋아요 토글
export async function toggleEpisodeCommentLike(episodeId: number, commentId: number) {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}/like`, {
    method: 'POST',
  });
}

// 댓글 신고
export async function reportEpisodeComment(episodeId: number, commentId: number) {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}/report`, {
    method: 'POST',
  });
}

// 대댓글 목록 조회
export async function getEpisodeCommentReplies(episodeId: number, commentId: number): Promise<EpisodeComment[]> {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}/replies`);
}

// 대댓글 작성
export async function createEpisodeReply(episodeId: number, commentId: number, content: string): Promise<number> {
  return apiCall(`/api/episodes/${episodeId}/comments/${commentId}/replies`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  });
}
