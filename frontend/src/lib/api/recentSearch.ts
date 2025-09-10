// 최근 검색어 API 서비스
// 로컬스토리지 → Redis REST API 전환

interface RecentSearchResponse {
  data: string[];
}

interface RecentSearchRequest {
  term: string;
}

// 진행 중 요청 관리 (중복 방지)
const inFlightRequests = new Map<string, Promise<any>>();

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${endpoint}`;
  
  try {
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
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new Error('서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.');
    }
    throw error;
  }
}

// 중복 요청 방지 래퍼
function deduplicateRequest<T>(key: string, requestFn: () => Promise<T>): Promise<T> {
  if (inFlightRequests.has(key)) {
    return inFlightRequests.get(key)!;
  }
  
  const promise = requestFn().finally(() => {
    inFlightRequests.delete(key);
  });
  
  inFlightRequests.set(key, promise);
  return promise;
}

// 최근 검색어 목록 조회
export async function fetchRecentSearches(): Promise<string[]> {
  const startTime = Date.now();
  console.info('[Search][Recent] GET start');
  
  try {
    const result = await deduplicateRequest('fetchRecentSearches', async () => {
      return await apiCall<string[]>('/api/search/recent', {
        method: 'GET',
      });
    });
    
    const duration = Date.now() - startTime;
    console.info(`[Search][Recent] GET end ${duration}ms len=${result.length} cache=false`);
    return result;
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`[Search][Recent] GET failed ${duration}ms error=${error}`);
    return []; // 실패 시 빈 배열 반환
  }
}

// 최근 검색어 추가
export async function addRecentSearch(term: string): Promise<string[]> {
  const startTime = Date.now();
  console.info(`[Search][Recent] POST start term=${term}`);
  
  try {
    const result = await deduplicateRequest('addRecentSearch', async () => {
      return await apiCall<string[]>('/api/search/recent', {
        method: 'POST',
        body: JSON.stringify({ term }),
      });
    });
    
    const duration = Date.now() - startTime;
    console.info(`[Search][Recent] POST end ${duration}ms len=${result.length}`);
    return result;
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`[Search][Recent] POST failed ${duration}ms error=${error}`);
    throw error; // 쓰기 실패는 에러 throw
  }
}

// 특정 검색어 삭제
export async function removeRecentSearch(term: string): Promise<string[]> {
  const startTime = Date.now();
  console.info(`[Search][Recent] DELETE start term=${term}`);
  
  try {
    const result = await deduplicateRequest('removeRecentSearch', async () => {
      return await apiCall<string[]>(`/api/search/recent?term=${encodeURIComponent(term)}`, {
        method: 'DELETE',
      });
    });
    
    const duration = Date.now() - startTime;
    console.info(`[Search][Recent] DELETE end ${duration}ms len=${result.length}`);
    return result;
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`[Search][Recent] DELETE failed ${duration}ms error=${error}`);
    throw error; // 쓰기 실패는 에러 throw
  }
}

// 전체 검색어 삭제
export async function clearRecentSearches(): Promise<void> {
  const startTime = Date.now();
  console.info('[Search][Recent] DELETE ALL start');
  
  try {
    await deduplicateRequest('clearRecentSearches', async () => {
      return await apiCall<void>('/api/search/recent', {
        method: 'DELETE',
      });
    });
    
    const duration = Date.now() - startTime;
    console.info(`[Search][Recent] DELETE ALL end ${duration}ms`);
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`[Search][Recent] DELETE ALL failed ${duration}ms error=${error}`);
    throw error; // 쓰기 실패는 에러 throw
  }
}
