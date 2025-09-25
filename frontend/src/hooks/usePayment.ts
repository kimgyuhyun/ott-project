import { useState } from 'react';
import { createCheckout, checkPaymentStatus } from '@/lib/api/membership';
import { useAuth } from './useAuth';
import type { IamportRequestPayData, IamportResponse } from '@/types/iamport';
import { 
  PaymentError, 
  PaymentErrorCode, 
  ValidPgCode, 
  PaymentService, 
  PaymentState,
  isValidPg,
  isRetryableError,
  delay,
  validatePaymentResponse
} from '@/types/payment';

interface PaymentRequest {
  planCode: string;
  paymentService: PaymentService;
  successUrl?: string;
  cancelUrl?: string;
}

interface PaymentResult {
  success: boolean;
  paymentId?: number;
  errorMessage?: string;
  redirectUrl?: string | null;
}

export const usePayment = () => {
  const [paymentState, setPaymentState] = useState<PaymentState>({
    status: 'idle',
    retryCount: 0
  });
  const { user } = useAuth();

  // PG 매핑 - 타입 안전성 강화
  const PG_MAP: Record<PaymentService, ValidPgCode> = {
    kakao: "kakaopay.TC0ONETIME",
    toss: "tosspayments",
    nice: "nice",
  };

  // 로깅 함수
  const logPaymentEvent = (event: string, data: any) => {
    console.log(`[Payment] ${event}:`, {
      timestamp: new Date().toISOString(),
      userId: user?.id,
      ...data
    });
  };

  const processPayment = async (request: PaymentRequest): Promise<PaymentResult> => {
    setPaymentState(prev => ({ ...prev, status: 'loading', error: undefined }));
    
    logPaymentEvent('Payment initiated', {
      planCode: request.planCode,
      paymentService: request.paymentService
    });

    try {
      // 1. 백엔드에서 체크아웃 세션 생성
      const checkoutResponse = await createCheckout(
        request.planCode,
        request.successUrl,
        request.cancelUrl,
        undefined,
        request.paymentService
      );

      logPaymentEvent('Checkout created', {
        paymentId: checkoutResponse.paymentId,
        amount: checkoutResponse.amount,
        providerSessionId: checkoutResponse.providerSessionId
      });

      // 2. 아임포트 SDK 초기화 및 결제 요청
      if (typeof window !== 'undefined' && window.IMP) {
        try {
          window.IMP.init('imp45866522'); // 아임포트 가맹점 식별코드
          logPaymentEvent('SDK initialized', { merchantCode: 'imp45866522' });
        } catch (initError) {
          logPaymentEvent('SDK initialization failed', { error: initError });
          throw new PaymentError(
            PaymentErrorCode.SDK_NOT_LOADED,
            '아임포트 SDK 초기화에 실패했습니다.',
            initError as Error
          );
        }

        // 3. 결제 요청
        return new Promise((resolve) => {
          // PG 코드 검증 및 변환
          const pg = PG_MAP[request.paymentService] || "kakaopay.TC0ONETIME";
          
          if (!isValidPg(pg)) {
            const error = new PaymentError(
              PaymentErrorCode.INVALID_PG,
              `지원하지 않는 PG 코드입니다: ${pg}`
            );
            logPaymentEvent('Invalid PG code', { pg, paymentService: request.paymentService });
            resolve({
              success: false,
              errorMessage: error.message
            });
            return;
          }
          
          const paymentData: IamportRequestPayData = {
            pg,
            pay_method: 'card',
            merchant_uid: checkoutResponse.providerSessionId,
            amount: checkoutResponse.amount,
            name: 'OTT 멤버십 구독',
            buyer_email: user?.email || '',
            buyer_name: user?.username || '',
            m_redirect_url: window.location.origin + '/membership/success',
            popup: false
          };

          logPaymentEvent('Payment data prepared', {
            pg,
            amount: paymentData.amount,
            merchant_uid: paymentData.merchant_uid
          });

          window.IMP.request_pay(paymentData, async (response: IamportResponse) => {
            // 응답 타입 검증
            const validation = validatePaymentResponse(response);
            if (!validation.isValid) {
              logPaymentEvent('Invalid payment response', { response, error: validation.error });
              const error = new PaymentError(
                PaymentErrorCode.INVALID_PAYMENT_DATA,
                validation.error || '유효하지 않은 결제 응답입니다.'
              );
              setPaymentState(prev => ({ ...prev, status: 'error', error }));
              resolve({
                success: false,
                errorMessage: error.message
              });
              return;
            }

            logPaymentEvent('Payment response received', {
              success: response.success,
              error_msg: response.error_msg,
              imp_uid: response.imp_uid
            });
            
            if (response.success) {
              // 결제 성공 시 백엔드에서 결제 상태 확인
              try {
                const statusResponse = await checkPaymentStatus(checkoutResponse.paymentId);
                logPaymentEvent('Payment status confirmed', {
                  paymentId: checkoutResponse.paymentId,
                  status: statusResponse.status
                });
                setPaymentState(prev => ({ 
                  ...prev, 
                  status: 'success', 
                  paymentId: checkoutResponse.paymentId 
                }));
                resolve({
                  success: true,
                  paymentId: checkoutResponse.paymentId,
                  redirectUrl: checkoutResponse.redirectUrl
                });
              } catch (statusError) {
                logPaymentEvent('Status check failed', { 
                  error: statusError,
                  paymentId: checkoutResponse.paymentId 
                });
                const error = new PaymentError(
                  PaymentErrorCode.STATUS_CHECK_FAILED,
                  '결제 상태 확인에 실패했습니다.',
                  statusError as Error
                );
                setPaymentState(prev => ({ ...prev, status: 'error', error }));
                resolve({
                  success: false,
                  errorMessage: error.message
                });
              }
            } else {
              logPaymentEvent('Payment failed', { 
                error_msg: response.error_msg,
                imp_uid: response.imp_uid 
              });
              const error = new PaymentError(
                PaymentErrorCode.PAYMENT_FAILED,
                response.error_msg || '결제에 실패했습니다.'
              );
              setPaymentState(prev => ({ ...prev, status: 'error', error }));
              resolve({
                success: false,
                errorMessage: error.message
              });
            }
          });
        });
      } else {
        logPaymentEvent('SDK not available', { windowIMP: !!window.IMP });
        throw new PaymentError(
          PaymentErrorCode.SDK_NOT_LOADED,
          '아임포트 SDK를 불러올 수 없습니다. 페이지를 새로고침해주세요.'
        );
      }
    } catch (err) {
      logPaymentEvent('Payment process error', { error: err });
      
      let paymentError: PaymentError;
      if (err instanceof PaymentError) {
        paymentError = err;
      } else if (err instanceof Error) {
        paymentError = new PaymentError(
          PaymentErrorCode.NETWORK_ERROR,
          '결제 처리 중 오류가 발생했습니다.',
          err
        );
      } else {
        paymentError = new PaymentError(
          PaymentErrorCode.NETWORK_ERROR,
          '알 수 없는 오류가 발생했습니다.'
        );
      }
      
      setPaymentState(prev => ({ ...prev, status: 'error', error: paymentError }));
      return {
        success: false,
        errorMessage: paymentError.message
      };
    } finally {
      setPaymentState(prev => ({ ...prev, status: 'idle' }));
    }
  };

  const checkPayment = async (paymentId: number): Promise<boolean> => {
    try {
      const status = await checkPaymentStatus(paymentId);
      logPaymentEvent('Payment status checked', { paymentId, status: status.status });
      return status.status === 'SUCCEEDED';
    } catch (err) {
      logPaymentEvent('Payment status check failed', { paymentId, error: err });
      return false;
    }
  };

  // 재시도 로직이 포함된 결제 처리
  const processPaymentWithRetry = async (request: PaymentRequest, maxRetries = 3): Promise<PaymentResult> => {
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        setPaymentState(prev => ({ ...prev, retryCount: attempt - 1 }));
        const result = await processPayment(request);
        
        if (result.success) {
          setPaymentState(prev => ({ ...prev, retryCount: 0 }));
          return result;
        }
        
        // 실패한 경우 재시도 가능한지 확인
        if (attempt === maxRetries) {
          return result;
        }
        
        // 재시도 가능한 에러인지 확인 (현재는 단순히 attempt < maxRetries로 처리)
        logPaymentEvent('Retrying payment', { attempt, maxRetries });
        await delay(1000 * attempt); // 지수 백오프
        
      } catch (error) {
        if (attempt === maxRetries) {
          throw error;
        }
        logPaymentEvent('Payment attempt failed, retrying', { attempt, error });
        await delay(1000 * attempt);
      }
    }
    
    throw new PaymentError(PaymentErrorCode.PAYMENT_FAILED, '최대 재시도 횟수를 초과했습니다.');
  };

  return {
    processPayment,
    processPaymentWithRetry,
    checkPayment,
    isLoading: paymentState.status === 'loading',
    error: paymentState.error?.message || null,
    paymentState,
    clearError: () => setPaymentState(prev => ({ ...prev, error: undefined }))
  };
};
