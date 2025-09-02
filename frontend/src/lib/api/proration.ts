// 차액 결제 관련 API 함수들

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
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  return response.json();
}

// 차액 결제 세션 생성
export async function createProrationCheckout(
  planCode: string, 
  successUrl?: string, 
  cancelUrl?: string, 
  paymentService?: string
) {
  return apiCall<ProrationCheckoutResponse>('/api/payments/proration', {
    method: 'POST',
    body: JSON.stringify({ 
      planCode, 
      successUrl, 
      cancelUrl, 
      paymentService 
    }),
  });
}

// 차액 결제 완료 처리
export async function processProrationPayment(paymentId: number) {
  return apiCall<ProrationPaymentResponse>(`/api/payments/proration/${paymentId}/complete`, {
    method: 'POST',
  });
}

// 차액 계산 조회
export async function calculateProrationAmount(planCode: string) {
  return apiCall<ProrationAmountResponse>(`/api/payments/proration/calculate?planCode=${planCode}`);
}

// 타입 정의
export interface ProrationCheckoutResponse {
  paymentId: number;
  providerSessionId: string;
  amount: number;
  pg?: string;
  redirectUrl?: string;
}

export interface ProrationPaymentResponse {
  success: boolean;
  paymentId: number;
  planChangeResult?: {
    changeType: 'UPGRADE';
    effectiveDate: string;
    prorationAmount: number;
    message: string;
  };
  errorMessage?: string;
}

export interface ProrationAmountResponse {
  prorationAmount: number;
  currentPlan: {
    name: string;
    monthlyPrice: number;
  };
  targetPlan: {
    name: string;
    monthlyPrice: number;
  };
  remainingDays: number;
}
