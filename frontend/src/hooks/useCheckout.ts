"use client";
import { createCheckout, completePayment } from "@/lib/api/membership";
import {
  loadPortOne,
  preferPortOnePopup,
  getPortOneMerchantCode,
} from "@/lib/portone/loadPortOne";
import type { IamportRequestPayData, IamportResponse } from "@/types/iamport";
import type { PaymentService } from "@/types/payment";

export function useCheckout() {
  const requestPay = async (planCode: string, paymentService: string) => {
    // paymentService 유효성 검사
    if (!paymentService || typeof paymentService !== 'string') {
      throw new Error('유효하지 않은 결제 서비스입니다.');
    }

    const successUrl = `${window.location.origin}/membership/guide`;
    const cancelUrl = `${window.location.origin}/oauth2/failure`;

    // pg 와 customerUid 는 서버가 정한다. 예전에는 프론트가 자기 PG_MAP 으로 다시 매핑했는데,
    // 서버도 같은 매핑을 들고 있어 두 곳이 갈라졌고 프론트 쪽이 단건 채널로 굳어 있었다.
    const { providerSessionId, amount, paymentId, pg, customerUid } = await createCheckout(
      planCode,
      successUrl,
      cancelUrl,
      undefined,
      paymentService as PaymentService
    );

    const IMP = await loadPortOne();
    if (!IMP) throw new Error("PortOne SDK를 불러오지 못했습니다.");

    IMP.init(getPortOneMerchantCode());

    await new Promise<void>((resolve, reject) => {
      const paymentData: IamportRequestPayData = {
        pg,
        pay_method: "card",
        merchant_uid: providerSessionId,
        amount,
        name: `Membership ${planCode}`,
        m_redirect_url: successUrl,
        popup: preferPortOnePopup(),
        // 정기결제 채널일 때만 채워진다. 이 값이 있으면 결제창이 결제와 동시에 빌링키를 발급해
        // 이 이름에 묶어 두고, 이후 자동 청구가 그 이름으로 재청구한다. 없으면 단건 결제로 끝난다.
        ...(customerUid ? { customer_uid: customerUid } : {}),
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

