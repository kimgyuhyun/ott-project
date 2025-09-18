// 동일 오리진 경유

// 플레이어 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  console.log('🌐 API 호출:', {
    endpoint,
    method: options.method || 'GET',
    body: options.body
  });
  
  const response = await fetch(endpoint, {
    ...options,
    credentials: 'include', // 세션 쿠키 포함
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  console.log('🌐 API 응답:', {
    status: response.status,
    statusText: response.statusText,
    ok: response.ok
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('🌐 API 에러:', {
      status: response.status,
      statusText: response.statusText,
      errorText
    });
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  // 응답이 비어있는지 확인 (Content-Length가 0이거나 빈 응답)
  const contentLength = response.headers.get('content-length');
  const contentType = response.headers.get('content-type');
  
  if (contentLength === '0' || !contentType?.includes('application/json')) {
    console.log('🌐 API 성공 (빈 응답):', { status: response.status });
    return {} as T; // 빈 객체 반환
  }

  const result = await response.json();
  console.log('🌐 API 성공:', result);
  return result;
}

// 자막 관련 API
export async function getSubtitles(episodeId: number) {
  return apiCall(`/api/player/episodes/${episodeId}/subtitles`);
}

export async function getDefaultSubtitle(episodeId: number) {
  return apiCall(`/api/player/episodes/${episodeId}/subtitles/default`);
}

export async function getSubtitleByLanguage(episodeId: number, language: string) {
  return apiCall(`/api/player/episodes/${episodeId}/subtitles/${language}`);
}

// 스킵 관련 API
export async function getSkips(episodeId: number) {
  return apiCall(`/api/player/episodes/${episodeId}/skips`);
}

// 사용자 재생 설정 API
export async function getUserPlaybackSettings() {
  return apiCall('/api/player/users/me/settings');
}

export async function updateUserPlaybackSettings(settings: {
  autoSkipIntro: boolean;
  autoSkipEnding: boolean;
  defaultQuality: string;
  playbackSpeed: number;
}) {
  return apiCall('/api/users/me/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
  });
}

// 에피소드 스트림 URL 발급
export async function getEpisodeStreamUrl(episodeId: number) {
  return apiCall(`/api/episodes/${episodeId}/stream-url`);
}

// 진행률 저장 디바운싱을 위한 Map (메모리 누수 방지)
const saveProgressTimeouts = new Map<number, ReturnType<typeof setTimeout>>();
const MAX_TIMEOUTS = 100; // 최대 타이머 수 제한

// 메모리 정리 함수
function cleanupTimeouts() {
  if (saveProgressTimeouts.size > MAX_TIMEOUTS) {
    console.warn('⚠️ saveProgressTimeouts Map 크기 초과, 정리 중...');
    for (const [episodeId, timeout] of saveProgressTimeouts.entries()) {
      clearTimeout(timeout);
      saveProgressTimeouts.delete(episodeId);
    }
  }
}

// 시청 진행률 저장 (디바운싱 적용 + 재시도 로직)
export async function saveEpisodeProgress(episodeId: number, progressData: {
  positionSec: number;
  durationSec: number;
}, retryCount: number = 0): Promise<any> {
  console.log('🌐 saveEpisodeProgress API 호출:', {
    url: `/api/episodes/${episodeId}/progress`,
    method: 'POST',
    data: progressData,
    retryCount
  });
  
  // 메모리 정리
  cleanupTimeouts();
  
  // 기존 타이머 취소
  const existingTimeout = saveProgressTimeouts.get(episodeId);
  if (existingTimeout) {
    clearTimeout(existingTimeout);
  }
  
  // 2초 후에 실제 저장 (디바운싱)
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(async () => {
      try {
        const result = await apiCall(`/api/episodes/${episodeId}/progress`, {
          method: 'POST',
          body: JSON.stringify(progressData),
        });
        console.log('🌐 saveEpisodeProgress API 성공:', result);
        saveProgressTimeouts.delete(episodeId);
        resolve(result);
      } catch (error) {
        console.error('🌐 saveEpisodeProgress API 실패:', error);
        saveProgressTimeouts.delete(episodeId);
        
        // 재시도 로직 (최대 2회)
        if (retryCount < 2) {
          console.log(`🔄 재시도 ${retryCount + 1}/2`);
          setTimeout(() => {
            saveEpisodeProgress(episodeId, progressData, retryCount + 1)
              .then(resolve)
              .catch(reject);
          }, 1000 * (retryCount + 1)); // 지수 백오프
        } else {
          reject(error);
        }
      }
    }, 2000);
    
    saveProgressTimeouts.set(episodeId, timeout);
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

