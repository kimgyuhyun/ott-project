// 결제 관련 타입 정의

export enum PaymentErrorCode {
  SDK_NOT_LOADED = 'SDK_NOT_LOADED',
  INVALID_PG = 'INVALID_PG',
  PAYMENT_FAILED = 'PAYMENT_FAILED',
  NETWORK_ERROR = 'NETWORK_ERROR',
  CHECKOUT_FAILED = 'CHECKOUT_FAILED',
  STATUS_CHECK_FAILED = 'STATUS_CHECK_FAILED',
  INVALID_PAYMENT_DATA = 'INVALID_PAYMENT_DATA',
  USER_CANCELLED = 'USER_CANCELLED'
}

export class PaymentError extends Error {
  constructor(
    public code: PaymentErrorCode,
    message: string,
    public originalError?: Error
  ) {
    super(message);
    this.name = 'PaymentError';
  }
}

// 유효한 PG 코드 타입
export type ValidPgCode = 'kakaopay.TC0ONETIME' | 'tosspayments' | 'nice';

// PG 매핑 타입
export type PaymentService = 'kakao' | 'toss' | 'nice';

// 결제 상태 타입
export interface PaymentState {
  status: 'idle' | 'loading' | 'success' | 'error';
  error?: PaymentError;
  paymentId?: number;
  retryCount: number;
}

// PG 코드 검증 함수
export const isValidPg = (pg: string): pg is ValidPgCode => {
  return ['kakaopay.TC0ONETIME', 'tosspayments', 'nice'].includes(pg);
};

// 재시도 가능한 에러인지 확인
export const isRetryableError = (error: PaymentError): boolean => {
  return [
    PaymentErrorCode.NETWORK_ERROR,
    PaymentErrorCode.STATUS_CHECK_FAILED
  ].includes(error.code);
};

// 지연 함수
export const delay = (ms: number): Promise<void> => 
  new Promise(resolve => setTimeout(resolve, ms));

// IamportResponse 타입 검증 함수
export const isValidIamportResponse = (response: any): response is import('@/types/iamport').IamportResponse => {
  return (
    response &&
    typeof response === 'object' &&
    typeof response.success === 'boolean' &&
    (response.error_msg === undefined || typeof response.error_msg === 'string') &&
    (response.imp_uid === undefined || typeof response.imp_uid === 'string') &&
    (response.merchant_uid === undefined || typeof response.merchant_uid === 'string')
  );
};

// 결제 응답 안전성 검증
export const validatePaymentResponse = (response: any): { isValid: boolean; error?: string } => {
  if (!isValidIamportResponse(response)) {
    return {
      isValid: false,
      error: '유효하지 않은 결제 응답 형식입니다.'
    };
  }

  if (!response.success && !response.error_msg) {
    return {
      isValid: false,
      error: '결제 실패 원인을 알 수 없습니다.'
    };
  }

  return { isValid: true };
};