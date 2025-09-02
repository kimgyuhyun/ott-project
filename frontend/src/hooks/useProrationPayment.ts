import { useState } from 'react';
import { createProrationCheckout, processProrationPayment as completeProrationPayment } from '@/lib/api/proration';
import { useAuth } from './useAuth';

interface ProrationPaymentRequest {
  planCode: string;
  paymentService: string;
  successUrl?: string;
  cancelUrl?: string;
}

interface ProrationPaymentResult {
  success: boolean;
  paymentId?: number;
  errorMessage?: string;
  redirectUrl?: string;
}

export const useProrationPayment = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const processProrationPayment = async (request: ProrationPaymentRequest): Promise<ProrationPaymentResult> => {
    setIsLoading(true);
    setError(null);

    console.log('useProrationPayment - request:', request);
    console.log('useProrationPayment - planCode:', request.planCode);

    try {
      // 1. 백엔드에서 차액 결제 세션 생성
      const checkoutResponse = await createProrationCheckout(
        request.planCode,
        request.successUrl,
        request.cancelUrl,
        request.paymentService
      );

      // 2. 아임포트 SDK 초기화
      if (typeof window !== 'undefined' && window.IMP) {
        window.IMP.init('imp45866522'); // 아임포트 가맹점 식별코드

        // 3. 차액 결제 요청
        return new Promise((resolve) => {
          window.IMP.request_pay({
            pg: checkoutResponse.pg || 'kakaopay.TC0ONETIME',
            pay_method: 'card',
            merchant_uid: checkoutResponse.providerSessionId,
            amount: checkoutResponse.amount,
            name: '플랜 업그레이드 차액 결제',
            buyer_email: user?.email || '',
            buyer_name: user?.name || '',
            m_redirect_url: window.location.origin + '/membership/success',
            popup: false
          }, async (response) => {
            if (response.success) {
              // 결제 성공 시 백엔드에서 차액 결제 완료 처리
              try {
                const result = await completeProrationPayment(checkoutResponse.paymentId);
                resolve({
                  success: true,
                  paymentId: checkoutResponse.paymentId,
                  redirectUrl: checkoutResponse.redirectUrl
                });
              } catch (statusError) {
                resolve({
                  success: false,
                  errorMessage: '차액 결제 완료 처리에 실패했습니다.'
                });
              }
            } else {
              resolve({
                success: false,
                errorMessage: response.error_msg || '차액 결제에 실패했습니다.'
              });
            }
          });
        });
      } else {
        throw new Error('아임포트 SDK를 불러올 수 없습니다.');
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '차액 결제 처리 중 오류가 발생했습니다.';
      setError(errorMessage);
      return {
        success: false,
        errorMessage
      };
    } finally {
      setIsLoading(false);
    }
  };

  const checkProrationPayment = async (paymentId: number): Promise<boolean> => {
    try {
      const result = await processProrationPayment(paymentId);
      return result.success;
    } catch (err) {
      console.error('차액 결제 상태 확인 실패:', err);
      return false;
    }
  };

  return {
    processProrationPayment,
    checkProrationPayment,
    isLoading,
    error,
    clearError: () => setError(null)
  };
};
