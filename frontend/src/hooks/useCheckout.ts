"use client";
import { createCheckout, checkPaymentStatus } from "@/lib/api/membership";
import type { IamportRequestPayData, IamportResponse } from "@/types/iamport";

async function loadPortOne(): Promise<Window["IMP"] | null> {
  if (typeof window === "undefined") return null;
  if (window.IMP) return window.IMP;
  await new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://cdn.iamport.kr/js/iamport.payment-1.2.0.js";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load PortOne SDK"));
    document.head.appendChild(script);
  });
  return window.IMP;
}

const PG_MAP: Record<string, string> = {
  kakao: "kakaopay",
  toss: "tosspayments",
  nice: "nice",
};

export function useCheckout() {
  const requestPay = async (planCode: string, paymentService: string) => {
    const successUrl = `${window.location.origin}/membership/guide`;
    const cancelUrl = `${window.location.origin}/oauth2/failure`;

    const { providerSessionId, amount, paymentId } = await createCheckout(
      planCode,
      successUrl,
      cancelUrl,
      undefined,
      paymentService
    );

    const IMP = await loadPortOne();
    if (!IMP) throw new Error("PortOne SDK를 불러오지 못했습니다.");

    const merchantCode = (process.env.NEXT_PUBLIC_PORTONE_MERCHANT_CODE as string) || (process.env.NODE_ENV !== 'production' ? 'imp45866522' : '');
    if (!merchantCode) throw new Error("NEXT_PUBLIC_PORTONE_MERCHANT_CODE 미설정");

    IMP.init(merchantCode);

    const pg = PG_MAP[paymentService?.toLowerCase?.()] || "kakaopay"; // 기본값 설정

    await new Promise<void>((resolve, reject) => {
      const paymentData: IamportRequestPayData = {
        pg,
        pay_method: "card",
        merchant_uid: providerSessionId,
        amount,
        name: `Membership ${planCode}`,
        m_redirect_url: successUrl, // 모바일 복귀 URL
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

