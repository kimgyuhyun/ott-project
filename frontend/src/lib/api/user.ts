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
    if (response.status === 401) {
      // 401 에러는 리다이렉트하지 않고 에러로만 처리
      const err: any = new Error('UNAUTHORIZED');
      err.status = 401;
      err.body = errorText;
      throw err;
    }
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
  try {
    // 캐시 방지를 위해 타임스탬프 추가
    const timestamp = Date.now();
    return await apiCall(`/api/episodes/mypage/watch-history?page=${page}&size=${size}&t=${timestamp}`);
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 빈 결과 반환
    if (error?.status === 401) {
      console.log('🔍 시청 기록 조회 실패: 로그인 필요 (401)');
      return { content: [], totalElements: 0, totalPages: 0, size, number: page, first: true, last: true };
    }
    throw error;
  }
}

// 사용자 최근 본(애니별 최신 1건)
export async function getUserRecentAnime(params?: { page?: number; size?: number; cursorUpdatedAt?: string; cursorAnimeId?: number }) {
  try {
    const page = params?.page ?? 0;
    const size = params?.size ?? 20;
    const qp = new URLSearchParams();
    qp.append('page', String(page));
    qp.append('size', String(size));
    if (params?.cursorUpdatedAt) qp.append('cursorUpdatedAt', params.cursorUpdatedAt);
    if (params?.cursorAnimeId != null) qp.append('cursorAnimeId', String(params.cursorAnimeId));
    qp.append('t', String(Date.now()));
    return await apiCall(`/api/episodes/mypage/recent-anime?${qp.toString()}`);
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 빈 결과 반환
    if (error?.status === 401) {
      console.log('🔍 최근 본 목록 조회 실패: 로그인 필요 (401)');
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      return { content: [], totalElements: 0, totalPages: 0, size, number: page, first: true, last: true };
    }
    throw error;
  }
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
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 null 반환
    if (error?.status === 401) {
      console.log('🔍 시청 기록 조회 실패: 로그인 필요 (401)');
      return null;
    }
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
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 빈 결과 반환
    if (error?.status === 401) {
      console.log('🔍 보고싶다 목록 조회 실패: 로그인 필요 (401)');
      return { content: [], totalElements: 0, totalPages: 0, size, number: page, first: true, last: true };
    }
    console.error('🌐 [FRONTEND] getUserWantList 에러:', error);
    throw error;
  }
}

// 사용자 활동 통계 조회
export async function getUserStats() {
  try {
    // 백엔드 집계 API 호출
    return await apiCall('/api/mypage/stats');
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 빈 통계 반환
    if (error?.status === 401) {
      console.log('🔍 사용자 통계 조회 실패: 로그인 필요 (401)');
      return { totalWatchTime: 0, totalEpisodes: 0, favoriteCount: 0, recentCount: 0 };
    }
    throw error;
  }
}

// 비밀번호 변경
export async function changePassword(passwordData: any) {
  return apiCall('/api/settings/change-password', {
    method: 'PUT',
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

// 사용자 정주행 완료 작품 조회
export async function getUserBingeList() {
  console.log('🌐 [FRONTEND] getUserBingeList 호출');
  
  try {
    const result = await apiCall('/api/mypage/binge');
    console.log('🌐 [FRONTEND] getUserBingeList 응답:', result);
    return result;
  } catch (error: any) {
    // 401 에러인 경우 로그인하지 않은 상태로 간주하고 빈 결과 반환
    if (error?.status === 401) {
      console.log('🔍 정주행 목록 조회 실패: 로그인 필요 (401)');
      return [];
    }
    console.error('🌐 [FRONTEND] getUserBingeList 에러:', error);
    throw error;
  }
}

// 최근본 목록에서 숨김 처리
export async function hideFromRecent(aniId: number) {
  console.log('🌐 [FRONTEND] hideFromRecent 호출 - aniId:', aniId);
  
  try {
    await apiCall(`/api/mypage/recent/anime/${aniId}`, {
      method: 'DELETE'
    });
    console.log('🌐 [FRONTEND] hideFromRecent 성공');
  } catch (error: any) {
    console.error('🌐 [FRONTEND] hideFromRecent 에러:', error);
    throw error;
  }
}

// 찜 취소 (보고싶다 목록에서 삭제)
export async function removeFromWantList(aniId: number) {
  console.log('🌐 [FRONTEND] removeFromWantList 호출 - aniId:', aniId);
  
  try {
    await apiCall(`/api/anime/${aniId}/favorite`, {
      method: 'POST'
    });
    console.log('🌐 [FRONTEND] removeFromWantList 성공');
  } catch (error: any) {
    console.error('🌐 [FRONTEND] removeFromWantList 에러:', error);
    throw error;
  }
}

// 정주행 목록에서 완전 삭제 (시청 기록 완전 삭제)
export async function deleteFromBinge(aniId: number) {
  console.log('🌐 [FRONTEND] deleteFromBinge 호출 - aniId:', aniId);
  
  try {
    await apiCall(`/api/mypage/binge/anime/${aniId}`, {
      method: 'DELETE'
    });
    console.log('🌐 [FRONTEND] deleteFromBinge 성공');
  } catch (error: any) {
    console.error('🌐 [FRONTEND] deleteFromBinge 에러:', error);
    throw error;
  }
}