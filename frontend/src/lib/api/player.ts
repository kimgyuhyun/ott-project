// 동일 오리진 경유

// 플레이어 관련 API 함수들

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

  return response.json();
}

// 에피소드 스트림 URL 발급
export async function getEpisodeStreamUrl(episodeId: number) {
  return apiCall(`/api/episodes/${episodeId}/stream-url`);
}

// 시청 진행률 저장
export async function saveEpisodeProgress(episodeId: number, progressData: {
  positionSec: number;
  durationSec: number;
}) {
  return apiCall(`/api/episodes/${episodeId}/progress`, {
    method: 'POST',
    body: JSON.stringify(progressData),
  });
}

// 시청 진행률 조회
export async function getEpisodeProgress(episodeId: number) {
  return apiCall(`/api/episodes/${episodeId}/progress`);
}

// 여러 에피소드 진행률 벌크 조회
export async function getBulkEpisodeProgress(episodeIds: number[]) {
  return apiCall(`/api/episodes/progress`, {
    method: 'POST',
    body: JSON.stringify({ episodeIds }),
  });
}

// 다음 에피소드 조회
export async function getNextEpisode(episodeId: number) {
  return apiCall(`/api/episodes/${episodeId}/next`);
}

// 마이페이지 시청 기록 조회
export async function getWatchHistory(page: number = 0, size: number = 20) {
  return apiCall(`/api/episodes/mypage/watch-history?page=${page}&size=${size}`);
}
