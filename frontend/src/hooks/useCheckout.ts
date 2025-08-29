"use client";
import { createCheckout } from "@/lib/api/membership";

declare global {
  interface Window {
    IMP?: any;
  }
}

async function loadPortOne(): Promise<any> {
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
  kakao: "kakaopay.TC0ONETIME",
  toss: "tosspayments",
  nice: "nice",
};

export function useCheckout() {
  const requestPay = async (planCode: string, paymentService: string) => {
    const successUrl = `${window.location.origin}/membership/guide`;
    const cancelUrl = `${window.location.origin}/oauth2/failure`;

    const { providerSessionId, amount } = await createCheckout(
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

    const pg = PG_MAP[paymentService?.toLowerCase?.()] || "kakaopay.TC0ONETIME"; // 기본값 설정

    await new Promise<void>((resolve, reject) => {
      IMP.request_pay(
        {
          pg,
          merchant_uid: providerSessionId,
          name: `Membership ${planCode}`,
          amount,
          m_redirect_url: successUrl, // 모바일 복귀 URL
        },
        (rsp: any) => {
          if (rsp.success) {
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

