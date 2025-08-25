// 동일 오리진 경유

// 애니메이션 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`; // '' + '/api/...' => '/api/...'
  
  try {
    // 네트워크 연결 상태 확인
    if (!navigator.onLine) {
      throw new Error('네트워크 연결을 확인해주세요.');
    }

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
  } catch (error) {
    // 네트워크 오류인지 확인
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new Error('서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.');
    }
    
    // 기존 에러는 그대로 전달
    throw error;
  }
}

// 애니메이션 목록 조회 (홈페이지 메인)
export async function getAnimeList(page: number = 0, size: number = 20) {
  return apiCall(`/api/anime?page=${page}&size=${size}`);
}

// 애니메이션 상세 정보 조회
export async function getAnimeDetail(animeId: number) {
  return apiCall(`/api/anime/${animeId}`);
}

// 요일별 신작 애니메이션 조회
export async function getWeeklyAnime(dayOfWeek: string) {
  return apiCall(`/api/anime/weekly/${dayOfWeek}`);
}

// 장르별 애니메이션 검색
export async function getAnimeByGenre(genre: string, page: number = 0, size: number = 20) {
  return apiCall(`/api/anime/genre/${genre}?page=${page}&size=${size}`);
}

// 태그별 애니메이션 검색
export async function getAnimeByTag(tag: string, page: number = 0, size: number = 20) {
  return apiCall(`/api/anime/tag/${tag}?page=${page}&size=${size}`);
}

// 애니메이션 검색
export async function searchAnime(query: string, page: number = 0, size: number = 20) {
  return apiCall(`/api/anime/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
}

// 추천 애니메이션 조회
export async function getRecommendedAnime() {
  return apiCall('/api/anime/recommended');
}

// 인기 애니메이션 조회
export async function getPopularAnime() {
  return apiCall('/api/anime/popular');
}

// 최신 애니메이션 조회
export async function getLatestAnime() {
  return apiCall('/api/anime/latest');
}
