// 동일 오리진 경유

// 멤버십 관련 API 함수들

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

// 멤버십 플랜 목록 조회
export async function getMembershipPlans() {
  return apiCall('/api/membership/plans');
}

// 사용자 멤버십 구독 상태 조회
export async function getUserMembership() {
  return apiCall('/api/membership/user');
}

// 멤버십 구독 시작
export async function subscribeMembership(planId: number, paymentMethodId: number) {
  return apiCall('/api/membership/subscribe', {
    method: 'POST',
    body: JSON.stringify({ planId, paymentMethodId }),
  });
}

// 멤버십 구독 취소
export async function cancelMembership() {
  return apiCall('/api/membership/cancel', {
    method: 'POST',
  });
}

// 결제 수단 등록
export async function registerPaymentMethod(paymentMethod: any) {
  return apiCall('/api/payment/methods', {
    method: 'POST',
    body: JSON.stringify(paymentMethod),
  });
}

// 결제 수단 목록 조회
export async function getPaymentMethods() {
  return apiCall('/api/payment/methods');
}

// 결제 내역 조회
export async function getPaymentHistory(page: number = 0, size: number = 20) {
  return apiCall(`/api/payment/history?page=${page}&size=${size}`);
}
