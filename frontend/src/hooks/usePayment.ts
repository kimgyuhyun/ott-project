import { useState } from 'react';
import { createCheckout, checkPaymentStatus } from '@/lib/api/membership';
import { useAuth } from './useAuth';
import type { IamportRequestPayData, IamportResponse } from '@/types/iamport';

interface PaymentRequest {
  planCode: string;
  paymentService: string;
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
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const processPayment = async (request: PaymentRequest): Promise<PaymentResult> => {
    setIsLoading(true);
    setError(null);

    console.log('usePayment - request:', request);
    console.log('usePayment - planCode:', request.planCode);

    try {
      // 1. 백엔드에서 체크아웃 세션 생성
      const checkoutResponse = await createCheckout(
        request.planCode,
        request.successUrl,
        request.cancelUrl,
        undefined,
        request.paymentService
      );

      console.log('usePayment - checkoutResponse:', checkoutResponse);

      // 2. 아임포트 SDK 초기화 및 결제 요청
      if (typeof window !== 'undefined' && window.IMP) {
        try {
          window.IMP.init('imp45866522'); // 아임포트 가맹점 식별코드
          console.log('아임포트 SDK 초기화 완료');
        } catch (initError) {
          console.error('아임포트 SDK 초기화 실패:', initError);
          throw new Error('아임포트 SDK 초기화에 실패했습니다.');
        }

        // 3. 결제 요청
        return new Promise((resolve) => {
          const paymentData: IamportRequestPayData = {
            pg: request.paymentService || 'kakaopay',
            pay_method: 'card',
            merchant_uid: checkoutResponse.providerSessionId,
            amount: checkoutResponse.amount,
            name: 'OTT 멤버십 구독',
            buyer_email: user?.email || '',
            buyer_name: user?.username || '',
            m_redirect_url: window.location.origin + '/membership/success',
            popup: false
          };

          console.log('usePayment - paymentData:', paymentData);

          window.IMP.request_pay(paymentData, async (response: IamportResponse) => {
            console.log('usePayment - payment response:', response);
            
            if (response.success) {
              // 결제 성공 시 백엔드에서 결제 상태 확인
              try {
                const statusResponse = await checkPaymentStatus(checkoutResponse.paymentId);
                console.log('usePayment - statusResponse:', statusResponse);
                resolve({
                  success: true,
                  paymentId: checkoutResponse.paymentId,
                  redirectUrl: checkoutResponse.redirectUrl
                });
              } catch (statusError) {
                console.error('usePayment - status check error:', statusError);
                resolve({
                  success: false,
                  errorMessage: '결제 상태 확인에 실패했습니다.'
                });
              }
            } else {
              console.error('usePayment - payment failed:', response.error_msg);
              resolve({
                success: false,
                errorMessage: response.error_msg || '결제에 실패했습니다.'
              });
            }
          });
        });
      } else {
        console.error('아임포트 SDK가 로딩되지 않았습니다. window.IMP:', window.IMP);
        throw new Error('아임포트 SDK를 불러올 수 없습니다. 페이지를 새로고침해주세요.');
      }
    } catch (err) {
      console.error('usePayment - error:', err);
      const errorMessage = err instanceof Error ? err.message : '결제 처리 중 오류가 발생했습니다.';
      setError(errorMessage);
      return {
        success: false,
        errorMessage
      };
    } finally {
      setIsLoading(false);
    }
  };

  const checkPayment = async (paymentId: number): Promise<boolean> => {
    try {
      const status = await checkPaymentStatus(paymentId);
      return status.status === 'SUCCEEDED';
    } catch (err) {
      console.error('결제 상태 확인 실패:', err);
      return false;
    }
  };

  return {
    processPayment,
    checkPayment,
    isLoading,
    error,
    clearError: () => setError(null)
  };
};
