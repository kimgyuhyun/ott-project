"use client";
import { createCheckout, checkPaymentStatus } from "@/lib/api/membership";
import {
  loadPortOne,
  preferPortOnePopup,
  getPortOneMerchantCode,
} from "@/lib/portone/loadPortOne";
import type { IamportRequestPayData, IamportResponse } from "@/types/iamport";
import type { PaymentService } from "@/types/payment";

const PG_MAP: Record<string, string> = {
  kakao: "kakaopay.TC0ONETIME",
  kakaopay: "kakaopay.TC0ONETIME",
  toss: "tosspayments",
  tosspay: "tosspayments", 
  tosspayments: "tosspayments",
  nice: "nice",
  nicepay: "nice",
  card: "kakaopay", // 기본 카드 결제
};

export function useCheckout() {
  const requestPay = async (planCode: string, paymentService: string) => {
    // paymentService 유효성 검사
    if (!paymentService || typeof paymentService !== 'string') {
      throw new Error('유효하지 않은 결제 서비스입니다.');
    }

    const successUrl = `${window.location.origin}/membership/guide`;
    const cancelUrl = `${window.location.origin}/oauth2/failure`;

    const { providerSessionId, amount, paymentId } = await createCheckout(
      planCode,
      successUrl,
      cancelUrl,
      undefined,
      paymentService as PaymentService
    );

    const IMP = await loadPortOne();
    if (!IMP) throw new Error("PortOne SDK를 불러오지 못했습니다.");

    IMP.init(getPortOneMerchantCode());

    // paymentService가 유효한지 확인하고 pg 값 설정
    const normalizedPaymentService = paymentService?.toLowerCase?.()?.trim();
    const pg = normalizedPaymentService && PG_MAP[normalizedPaymentService] 
      ? PG_MAP[normalizedPaymentService] 
      : "kakaopay.TC0ONETIME";
    
    console.log('Payment Service:', paymentService, 'Normalized:', normalizedPaymentService, 'PG:', pg);

    await new Promise<void>((resolve, reject) => {
      const paymentData: IamportRequestPayData = {
        pg,
        pay_method: "card",
        merchant_uid: providerSessionId,
        amount,
        name: `Membership ${planCode}`,
        m_redirect_url: successUrl,
        popup: preferPortOnePopup(),
      };

      IMP.request_pay(
        paymentData,
        async (rsp: IamportResponse) => {
          if (rsp.success) {
            try {
              // 결제 완료 후 백엔드 상태 확인
              await waitForPaymentConfirmation(paymentId);
              // 결제 성공 시 멤버십 안내 페이지로 리다이렉트
              window.location.href = successUrl;
              resolve();
            } catch (error) {
              console.error("결제 상태 확인 실패:", error);
              // 상태 확인 실패 시에도 성공 페이지로 이동 (웹훅 처리 대기)
              window.location.href = successUrl;
              resolve();
            }
          } else {
            reject(new Error(rsp.error_msg || "결제 실패"));
          }
        }
      );
    });
  };

  /**
   * 결제 확인 완료까지 대기
   * - 최대 재시도 횟수 및 대기 시간 설정
   */
  const waitForPaymentConfirmation = async (paymentId: number, maxRetries: number = 5, delayMs: number = 500): Promise<void> => {
    for (let i = 0; i < maxRetries; i++) {
      try {
        const status = await checkPaymentStatus(paymentId);
        if (status.status === 'SUCCEEDED') {
          console.log('결제 상태 확인 완료:', status);
          return;
        }
        
        console.log(`결제 상태 확인 중... (${i + 1}/${maxRetries}) - 현재상태: ${status.status}`);
        
        // 아직 처리 중인 경우 대기
        if (i < maxRetries - 1) {
          await new Promise(resolve => setTimeout(resolve, delayMs));
        }
      } catch (error) {
        console.warn(`결제 상태 확인 시도 ${i + 1} 실패:`, error);
        if (i < maxRetries - 1) {
          await new Promise(resolve => setTimeout(resolve, delayMs));
        }
      }
    }
    
    throw new Error('결제 상태 확인 시간 초과 (2.5초)');
  };

  return { requestPay };
}

