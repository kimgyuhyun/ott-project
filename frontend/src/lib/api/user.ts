// 동일 오리진 경유

// 사용자 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`; // '' + '/api/...' => '/api/...'
  
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

// 사용자 프로필 정보 조회
export async function getUserProfile() {
  return apiCall('/api/users/me/profile');
}

// 사용자 프로필 정보 수정
export async function updateUserProfile(profileData: any) {
  return apiCall('/api/users/me/profile', {
    method: 'PUT',
    body: JSON.stringify(profileData),
  });
}

// 사용자 설정 조회
export async function getUserSettings() {
  return apiCall('/api/users/me/settings');
}

// 사용자 설정 수정
export async function updateUserSettings(settings: any) {
  return apiCall('/api/users/me/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
  });
}

// 사용자 시청 기록 조회
export async function getUserWatchHistory(page: number = 0, size: number = 20) {
  // 캐시 방지를 위해 타임스탬프 추가
  const timestamp = Date.now();
  return apiCall(`/api/episodes/mypage/watch-history?page=${page}&size=${size}&t=${timestamp}`);
}

// 특정 애니메이션의 시청 기록 조회
export async function getAnimeWatchHistory(animeId: number) {
  try {
    // 캐시 방지를 위해 타임스탬프 추가
    const history = await getUserWatchHistory(0, 1000);
    console.log('🔍 전체 시청 기록:', history);
    
    const animeHistory = (history as any).content?.filter((item: any) => item.animeId === animeId) || [];
    console.log('🔍 해당 애니메이션 시청 기록:', animeHistory);
    console.log('🔍 시청 기록 상세:', animeHistory.map((item: any) => ({
      episodeId: item.episodeId,
      episodeNumber: item.episodeNumber,
      positionSec: item.positionSec,
      completed: item.completed,
      updatedAt: item.updatedAt,
      watchedAt: item.watchedAt,
      createdAt: item.createdAt,
      durationSec: item.durationSec,
      전체데이터: item
    })));
    
    // 각 시청 기록의 상세 정보를 개별적으로 출력
    animeHistory.forEach((item: any, index: number) => {
      console.log(`🔍 시청 기록 ${index + 1}:`, {
        episodeId: item.episodeId,
        episodeNumber: item.episodeNumber,
        positionSec: item.positionSec,
        durationSec: item.durationSec,
        updatedAt: item.updatedAt,
        모든필드: Object.keys(item),
        원본데이터: item
      });
    });
    
    if (animeHistory.length === 0) return null;
    
    // 가장 최근에 본 에피소드 찾기 (마지막 시청 시간 기준)
    const latestEpisode = animeHistory.sort((a: any, b: any) => 
      new Date(b.updatedAt || b.watchedAt || b.createdAt).getTime() - 
      new Date(a.updatedAt || a.watchedAt || a.createdAt).getTime()
    )[0];
    
    console.log('🔍 가장 최근 에피소드:', latestEpisode);
    
    // episodeNumber를 여러 필드에서 찾기
    const episodeNumber = latestEpisode.episodeNumber || 
                         latestEpisode.episode?.episodeNumber || 
                         latestEpisode.episodeNumber || 
                         1;
    
    console.log('🔍 에피소드 번호 찾기:', {
      episodeNumber: latestEpisode.episodeNumber,
      episode_episodeNumber: latestEpisode.episode?.episodeNumber,
      최종결정: episodeNumber
    });
    
    // 완료 상태 계산 (진행률이 90% 이상이면 완료로 간주)
    const durationSec = latestEpisode.durationSec || 0;
    const positionSec = latestEpisode.positionSec || 0;
    const isCompleted = durationSec > 0 && positionSec > 0 && (positionSec / durationSec) >= 0.9;
    
    const result = {
      episodeId: latestEpisode.episodeId,
      episodeNumber: episodeNumber,
      positionSec: positionSec,
      duration: durationSec,
      completed: isCompleted,
      watchedAt: latestEpisode.updatedAt || latestEpisode.watchedAt || latestEpisode.createdAt
    };
    
    console.log('🔍 반환할 시청 기록:', result);
    return result;
  } catch (error) {
    console.error('애니메이션 시청 기록 조회 중 오류:', error);
    return null;
  }
}

// 사용자 보고싶다 작품 조회
export async function getUserWantList(page: number = 0, size: number = 20) {
  console.log('🌐 [FRONTEND] getUserWantList 호출 - page:', page, 'size:', size);
  
  try {
    const result = await apiCall(`/api/mypage/favorites/anime?page=${page}&size=${size}`);
    console.log('🌐 [FRONTEND] getUserWantList 응답:', result);
    return result;
  } catch (error) {
    console.error('🌐 [FRONTEND] getUserWantList 에러:', error);
    throw error;
  }
}

// 사용자 활동 통계 조회
export async function getUserStats() {
  // 백엔드 집계 API 호출
  return apiCall('/api/mypage/stats');
}

// 비밀번호 변경
export async function changePassword(passwordData: any) {
  return apiCall('/api/user/change-password', {
    method: 'POST',
    body: JSON.stringify(passwordData),
  });
}

// 이메일 변경
export async function changeEmail(emailData: any) {
  return apiCall('/api/user/change-email', {
    method: 'POST',
    body: JSON.stringify(emailData),
  });
}
