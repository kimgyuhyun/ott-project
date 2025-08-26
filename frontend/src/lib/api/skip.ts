// 동일 오리진 경유

// 스킵 기능 관련 API 함수들

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

// 에피소드 스킵 메타데이터 조회
export async function getEpisodeSkips(episodeId: number) {
  return apiCall(`/api/episodes/${episodeId}/skips`);
}

// 스킵 사용 추적
export async function trackSkipUsage(episodeId: number, skipData: {
  skipType: string;
  positionSec: number;
}) {
  return apiCall(`/api/episodes/${episodeId}/skips/track`, {
    method: 'POST',
    body: JSON.stringify(skipData),
  });
}
