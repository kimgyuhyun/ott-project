// 알림 관련 API 함수들

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

// 알림 목록 조회
export async function getNotifications(page: number = 0, size: number = 20) {
  try {
    return await apiCall(`/api/notifications?page=${page}&size=${size}`);
  } catch (error: any) {
    if (error?.status === 401) {
      console.log('🔍 알림 목록 조회 실패: 로그인 필요 (401)');
      return { content: [], totalElements: 0, totalPages: 0, size, number: page, first: true, last: true };
    }
    throw error;
  }
}

// 읽지 않은 알림 개수 조회
export async function getUnreadNotificationCount() {
  try {
    return await apiCall('/api/notifications/unread-count');
  } catch (error: any) {
    if (error?.status === 401) {
      console.log('🔍 읽지 않은 알림 개수 조회 실패: 로그인 필요 (401)');
      return 0;
    }
    throw error;
  }
}

// 개별 알림 읽음 처리
export async function markNotificationAsRead(notificationId: number) {
  return apiCall(`/api/notifications/${notificationId}/read`, {
    method: 'PUT',
  });
}

// 전체 알림 읽음 처리
export async function markAllNotificationsAsRead() {
  return apiCall('/api/notifications/read-all', {
    method: 'PUT',
  });
}
