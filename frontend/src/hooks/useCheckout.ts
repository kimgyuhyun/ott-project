"use client";
import { createCheckout, completePayment } from "@/lib/api/membership";
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
              // 클라이언트 결제 확정(동기 주 경로): imp_uid로 서버가 아임포트에 재검증 후 멤버십 즉시 지급
              // imp_uid가 없거나 확정 호출이 실패해도 웹훅/대사 배치가 백업으로 확정하므로 성공 페이지로 진행
              if (rsp.imp_uid) {
                await completePayment(paymentId, rsp.imp_uid);
              }
            } catch (error) {
              console.error("결제 확정 호출 실패(웹훅/배치가 백업 처리):", error);
            }
            // 결제 성공 시 멤버십 안내 페이지로 리다이렉트
            window.location.href = successUrl;
            resolve();
          } else {
            reject(new Error(rsp.error_msg || "결제 실패"));
          }
        }
      );
    });
  };

  return { requestPay };
}

