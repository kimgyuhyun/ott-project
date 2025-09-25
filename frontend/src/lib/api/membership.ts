// 동일 오리진 경유

// 멤버십 관련 API 함수들
import { PaymentService } from '@/types/payment';

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}, expectJson: boolean = true): Promise<T> {
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

  // JSON 미기대 응답은 바로 종료 (void 응답 처리)
  if (!expectJson) {
    return undefined as T;
  }

  // 응답이 비어있으면 undefined 반환 (환불 API 등)
  const contentType = response.headers.get('content-type');
  if (!contentType || !contentType.includes('application/json')) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text.trim()) {
    return undefined as T;
  }

  return JSON.parse(text);
}

// 멤버십 플랜 목록 조회
export async function getMembershipPlans() {
  return apiCall<MembershipPlan[]>('/api/memberships/plans');
}

// 사용자 멤버십 구독 상태 조회
export async function getUserMembership() {
  return apiCall<UserMembership>('/api/users/me/membership');
}

// 멤버십 구독 시작
export async function subscribeMembership(planCode: string) {
  return apiCall<UserMembership>('/api/memberships/subscribe', {
    method: 'POST',
    body: JSON.stringify({ planCode }),
  });
}

// 멤버십 구독 취소
export async function cancelMembership(idempotencyKey?: string) {
  return apiCall<UserMembership>('/api/memberships/cancel', {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey }),
  });
}

// 멤버십 정기결제 재시작
export async function resumeMembership() {
  return apiCall<UserMembership>('/api/memberships/resume', {
    method: 'POST',
  });
}

// 멤버십 플랜 변경
export async function changeMembershipPlan(newPlanCode: string) {
  return apiCall<MembershipPlanChangeResponse>('/api/memberships/change-plan', {
    method: 'PUT',
    body: JSON.stringify({ newPlanCode }),
  });
}

// 플랜 변경 예약 취소
export async function cancelScheduledPlanChange() {
  return apiCall<UserMembership>('/api/memberships/change-plan/cancel', {
    method: 'POST',
  });
}

// 결제수단 등록
export async function registerPaymentMethod(paymentMethod: PaymentMethodRegisterRequest) {
  return apiCall<void>(
    '/api/payment-methods',
    {
      method: 'POST',
      body: JSON.stringify(paymentMethod),
    },
    false,
  );
}

// 결제수단 목록 조회
export async function getPaymentMethods() {
  return apiCall<PaymentMethodResponse[]>('/api/payment-methods');
}

// 결제수단 기본 지정
export async function setDefaultPaymentMethod(id: number) {
  return apiCall<void>(
    `/api/payment-methods/${id}/default`,
    {
      method: 'PUT',
    },
    false,
  );
}

// 결제수단 삭제
export async function deletePaymentMethod(id: number) {
  return apiCall<void>(
    `/api/payment-methods/${id}`,
    {
      method: 'DELETE',
    },
    false,
  );
}

// 결제 내역 조회
export async function getPaymentHistory(start?: string, end?: string) {
  const params = new URLSearchParams();
  if (start) params.append('start', start);
  if (end) params.append('end', end);
  
  return apiCall<PaymentHistoryItem[]>(`/api/payments/history?${params.toString()}`);
}

// 체크아웃 생성 (결제창 이동용)
export async function createCheckout(planCode: string, successUrl?: string, cancelUrl?: string, idempotencyKey?: string, paymentService?: PaymentService) {
  return apiCall<PaymentCheckoutCreateSuccess>(`/api/payments/checkout`, {
    method: 'POST',
    body: JSON.stringify({ planCode, successUrl, cancelUrl, idempotencyKey, paymentService }),
  });
}

// 결제 상태 확인
export async function checkPaymentStatus(paymentId: number) {
  return apiCall<PaymentStatusResponse>(`/api/payments/${paymentId}/status`);
}

// 환불 요청
export async function requestRefund(paymentId: number) {
  return apiCall<void>(
    `/api/payments/${paymentId}/refund`,
    {
      method: 'POST',
    },
    false,
  );
}

// 타입 정의
export interface MembershipPlan {
  id: number;
  code: string;
  name: string;
  monthlyPrice: number;
  periodMonths: number;
  concurrentStreams: number;
  maxQuality: string;
}

export interface UserMembership {
  id: number;
  planCode: string;
  planName: string;
  startDate: string;
  endAt: string;
  nextBillingAt: string;
  autoRenew: boolean;
  status: string;
  nextPlanCode?: string;
  nextPlanName?: string;
}

export interface MembershipPlanChangeResponse {
  changeType: 'UPGRADE' | 'DOWNGRADE';
  effectiveDate: string;
  prorationAmount?: number;
  message: string;
}

export interface PaymentMethodResponse {
  id: number;
  type: string;
  last4?: string;
  brand?: string;
  expiryMonth?: number;
  expiryYear?: number;
  isDefault: boolean;
  createdAt: string;
}

export interface PaymentMethodRegisterRequest {
  type: string;
  cardNumber?: string;
  expiryMonth?: number;
  expiryYear?: number;
  birthDate?: string;
  password?: string;
}

export interface PaymentHistoryItem {
  paymentId: number;
  planCode: string;
  planName: string;
  amount: number;
  currency: string;
  status: string;
  receiptUrl?: string;
  paidAt?: string;
  refundedAt?: string;
}

export interface PaymentCheckoutCreateSuccess {
  redirectUrl: string | null; // prepare-only 전환으로 null 가능
  paymentId: number;
  providerSessionId: string; // merchant_uid
  amount: number; // 결제 금액(dev에서는 1원)
}

export interface PaymentStatusResponse {
  paymentId: number;
  status: string;
  providerPaymentId?: string;
  receiptUrl?: string;
  reasonCode?: string;
  message?: string;
  occurredAt: string;
}