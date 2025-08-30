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
  return apiCall(`/api/episodes/mypage/watch-history?page=${page}&size=${size}`);
}

// 특정 애니메이션의 시청 기록 조회
export async function getAnimeWatchHistory(animeId: number) {
  try {
    const history = await getUserWatchHistory(0, 1000);
    const animeHistory = (history as any).content?.filter((item: any) => item.animeId === animeId) || [];
    
    if (animeHistory.length === 0) return null;
    
    // 가장 최근에 본 에피소드 찾기
    const latestEpisode = animeHistory.sort((a: any, b: any) => 
      new Date(b.watchedAt || b.createdAt).getTime() - new Date(a.watchedAt || a.createdAt).getTime()
    )[0];
    
    return {
      episodeId: latestEpisode.episodeId,
      episodeNumber: latestEpisode.episodeNumber || 1,
      positionSec: latestEpisode.positionSec || 0,
      duration: latestEpisode.duration || 0,
      completed: latestEpisode.completed || false,
      watchedAt: latestEpisode.watchedAt || latestEpisode.createdAt
    };
  } catch (error) {
    console.error('애니메이션 시청 기록 조회 중 오류:', error);
    return null;
  }
}

// 사용자 보고싶다 작품 조회
export async function getUserWantList(page: number = 0, size: number = 20) {
  return apiCall(`/api/mypage/favorites/anime?page=${page}&size=${size}`);
}

// 사용자 활동 통계 조회
export async function getUserStats() {
  try {
    // 시청 기록과 보고싶다 데이터를 병렬로 조회
    const [watchHistory, wantList] = await Promise.all([
      getUserWatchHistory(0, 1000), // 충분히 큰 크기로 조회
      getUserWantList(0, 1000)
    ]);
    
    // 통계 계산
    const totalEpisodes = (watchHistory as any).content?.length || 0;
    const totalAnime = new Set((watchHistory as any).content?.map((item: any) => item.animeId) || []).size;
          const wantCount = (wantList as any).content?.length || 0;
    const completedAnime = (watchHistory as any).content?.filter((item: any) => item.completed)?.length || 0;
    const watchingAnime = totalAnime - completedAnime;
    
    // 총 시청 시간 계산 (초 단위)
    const totalWatchTime = (watchHistory as any).content?.reduce((total: number, item: any) => {
      return total + (item.positionSec || 0);
    }, 0) || 0;
    
    return {
      totalWatchTime,
      totalEpisodes,
      totalAnime,
      wantCount,
      completedAnime,
      watchingAnime
    };
  } catch (error) {
    console.error('통계 계산 중 오류:', error);
    // 오류 시 기본값 반환
    return {
      totalWatchTime: 0,
      totalEpisodes: 0,
      totalAnime: 0,
      wantCount: 0,
      completedAnime: 0,
      watchingAnime: 0
    };
  }
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
