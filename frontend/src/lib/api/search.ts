// 동일 오리진 경유

// 검색 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  
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

// 타입 정의
export interface SearchResultItem {
  id: number;
  title: string;
  posterUrl?: string;
  rating?: number;
  badges?: string[];
  episode?: number;
  [key: string]: any;
}

export interface PagedResponse<T> {
  content?: T[];
  items?: T[];
  totalElements?: number;
  total?: number;
}

function normalizeArray<T = any>(data: any): T[] {
  const isArr = Array.isArray(data);
  const hasContent = data && Array.isArray((data as any).content);
  const hasItems = data && Array.isArray((data as any).items);
  console.log('[API normalizeArray] typeof=', typeof data, 'isArray=', isArr, 'keys=', data ? Object.keys(data) : null);
  if (isArr) return data as T[];
  if (hasContent) return (data as PagedResponse<T>).content as T[];
  if (hasItems) return (data as PagedResponse<T>).items as T[];
  console.warn('[API normalizeArray] 예상치 못한 구조, 빈 배열 반환');
  return [] as T[];
}

// 자동완성 검색 제안
export async function getSearchSuggestions(query: string, limit: number = 10): Promise<SearchResultItem[]> {
  if (!query || query.trim().length === 0) return [];
  const raw = await apiCall(`/api/search/suggest?query=${encodeURIComponent(query.trim())}&limit=${limit}`);
  return normalizeArray<SearchResultItem>(raw);
}

// 통합 검색
export async function searchContent(query: string, genreIds?: number[], tagIds?: number[], sort: string = 'id', page: number = 0, size: number = 20): Promise<SearchResultItem[] | PagedResponse<SearchResultItem>> {
  const params = new URLSearchParams();
  if (query) params.append('query', query);
  if (genreIds && genreIds.length > 0) {
    genreIds.forEach(id => params.append('genreIds', id.toString()));
  }
  if (tagIds && tagIds.length > 0) {
    tagIds.forEach(id => params.append('tagIds', id.toString()));
  }
  params.append('sort', sort);
  params.append('page', page.toString());
  params.append('size', size.toString());
  
  const raw = await apiCall(`/api/search?${params.toString()}`);
  // 원형이 필요할 수도 있어 원본을 반환하되, 상위에서 normalizeArray로 처리 가능
  return raw as any;
}

// 애니메이션 전용 검색 (기존 anime.ts와 중복 방지)
export async function searchAnimeOnly(query: string, page: number = 0, size: number = 20): Promise<SearchResultItem[]> {
  const raw = await apiCall(`/api/anime/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
  return normalizeArray<SearchResultItem>(raw);
}
