// 동일 오리진 경유

// 애니메이션 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용 (Nginx/Next rewrites 경유)
const API_BASE = '/api';

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
export async function getAnimeList(page: number = 0, size: number = 20, sort: string = 'id') {
  return apiCall(`/anime?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}`);
}

// 범용 목록 조회(필터/정렬/페이지) - 필터링 기능 유지, 응답 처리만 getAnimeList와 동일
export async function listAnime(params: {
  status?: string | null;
  genreIds?: number[] | null;
  tagIds?: number[] | null;
  minRating?: number | null;
  year?: number | null;
  quarter?: number | null;
  type?: string | null;
  isDub?: boolean | null;
  isSubtitle?: boolean | null;
  isExclusive?: boolean | null;
  isCompleted?: boolean | null;
  isNew?: boolean | null;
  isPopular?: boolean | null;
  sort?: string;
  page?: number;
  size?: number;
  cursorId?: number;
  cursorRating?: number;
} = {}) {
  const qp = new URLSearchParams();
  if (params.status) qp.append('status', params.status);
  if (params.genreIds && params.genreIds.length) params.genreIds.forEach(id => qp.append('genreIds', String(id)));
  if (params.tagIds && params.tagIds.length) params.tagIds.forEach(id => qp.append('tagIds', String(id)));
  if (params.minRating != null) qp.append('minRating', String(params.minRating));
  if (params.year != null) qp.append('year', String(params.year));
  if (params.quarter != null) qp.append('quarter', String(params.quarter));
  if (params.type) qp.append('type', params.type);
  if (params.isDub != null) qp.append('isDub', String(params.isDub));
  if (params.isSubtitle != null) qp.append('isSubtitle', String(params.isSubtitle));
  if (params.isExclusive != null) qp.append('isExclusive', String(params.isExclusive));
  if (params.isCompleted != null) qp.append('isCompleted', String(params.isCompleted));
  if (params.isNew != null) qp.append('isNew', String(params.isNew));
  if (params.isPopular != null) qp.append('isPopular', String(params.isPopular));
  qp.append('sort', params.sort ?? 'id');
  qp.append('page', String(params.page ?? 0));
  qp.append('size', String(params.size ?? 20));
  if (params.cursorId != null) qp.append('cursorId', String(params.cursorId));
  if (params.cursorRating != null) qp.append('cursorRating', String(params.cursorRating));
  
  // getAnimeList와 동일한 응답 처리: 단순히 apiCall만 반환
  const url = `/anime?${qp.toString()}`;
  console.log('[DEBUG] listAnime API 호출 URL:', url);
  return apiCall(url);
}

// 애니메이션 상세 정보 조회
export async function getAnimeDetail(animeId: number) {
  return apiCall(`/anime/${animeId}`);
}

// 요일별 신작 애니메이션 조회
export async function getWeeklyAnime(dayOfWeek: string) {
  return apiCall(`/anime/weekly/${dayOfWeek}`);
}

// 장르별 애니메이션 검색
export async function getAnimeByGenre(genre: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/genre/${genre}?page=${page}&size=${size}`);
}

// 태그별 애니메이션 검색
export async function getAnimeByTag(tag: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/tag/${tag}?page=${page}&size=${size}`);
}

// 애니메이션 검색
export async function searchAnime(query: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
}

// 추천 애니메이션 조회
export async function getRecommendedAnime() {
  return apiCall('/anime/recommended');
}

// 인기 애니메이션 조회
export async function getPopularAnime() {
  return apiCall('/anime/popular');
}

// 최신 애니메이션 조회
export async function getLatestAnime() {
  return apiCall('/anime/latest');
}

// 실시간 트렌딩(24h) 조회
export async function getTrendingAnime24h(limit: number = 10) {
  return apiCall(`/anime/trending-24h?limit=${limit}`);
}

// 마스터: 장르/태그 목록
export async function getGenres() {
  return apiCall('/anime/genres');
}

export async function getTags() {
  return apiCall('/anime/tags');
}

// 필터 옵션 목록
export async function getSeasons() {
  return apiCall('/anime/seasons');
}

export async function getYearOptions() {
  return apiCall('/anime/year-options');
}

export async function getStatuses() {
  return apiCall('/anime/statuses');
}

export async function getTypes() {
  return apiCall('/anime/types');
}