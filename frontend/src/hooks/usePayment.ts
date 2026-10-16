import { useRef, useState } from "react";
import {
  createCheckout,
  checkPaymentStatus,
  completePayment,
} from "@/lib/api/membership";
import {
  loadPortOne,
  preferPortOnePopup,
  getPortOneMerchantCode,
} from "@/lib/portone/loadPortOne";
import { useAuth } from "@/lib/AuthContext";
import { resolveConfirmOutcome } from "@/lib/paymentConfirmation";
import type { IamportRequestPayData, IamportResponse } from "@/types/iamport";
import {
  PaymentError,
  PaymentErrorCode,
  PaymentService,
  PaymentState,
  isRetryableError,
  delay,
  validatePaymentResponse,
} from "@/types/payment";

interface PaymentRequest {
  planCode: string;
  paymentService: PaymentService;
  successUrl?: string;
  cancelUrl?: string;
}

interface PaymentResult {
  success: boolean;
  /** 결제창은 끝났지만 서버 확정을 아직 확인하지 못했다(실패가 아니다) */
  pending?: boolean;
  paymentId?: number;
  errorMessage?: string;
  redirectUrl?: string | null;
}

export const usePayment = () => {
  const [paymentState, setPaymentState] = useState<PaymentState>({
    status: "idle",
    retryCount: 0,
  });
  const { user } = useAuth();
  // 진행 중인 결제 의도의 멱등키(#191). 결제창이 끝나거나 서버가 실패로 답하면 비운다.
  const checkoutIntentRef = useRef<{
    key: string;
    planCode: string;
    paymentService: PaymentService;
  } | null>(null);

  // 로깅 함수
  const logPaymentEvent = (event: string, data: Record<string, unknown>) => {
    console.log(`[Payment] ${event}:`, {
      timestamp: new Date().toISOString(),
      userId: user?.id,
      ...data,
    });
  };

  const processPayment = async (
    request: PaymentRequest,
  ): Promise<PaymentResult> => {
    setPaymentState((prev) => ({
      ...prev,
      status: "loading",
      error: undefined,
    }));

    logPaymentEvent("Payment initiated", {
      planCode: request.planCode,
      paymentService: request.paymentService,
    });

    // 결제 의도마다 멱등키를 하나 만들고, 같은 의도의 재시도(응답 유실 뒤 다시 누름)에는 같은 키를 쓴다(#191).
    // 서버는 같은 키에 첫 응답(결제 ID·결제창 세션)을 그대로 돌려주므로 PENDING 결제가 하나만 생긴다.
    // 예전에는 키를 보내지 않아 다시 누를 때마다 PENDING 결제와 결제창 세션이 하나씩 더 생겼다.
    // 플랜이나 결제 서비스를 바꿨으면 다른 의도다(같은 키에 다른 내용을 보내면 서버가 422 로 거절한다).
    const intent = checkoutIntentRef.current;
    const idempotencyKey =
      intent &&
      intent.planCode === request.planCode &&
      intent.paymentService === request.paymentService
        ? intent.key
        : crypto.randomUUID();
    checkoutIntentRef.current = {
      key: idempotencyKey,
      planCode: request.planCode,
      paymentService: request.paymentService,
    };

    try {
      // 1. 백엔드에서 체크아웃 세션 생성
      const checkoutResponse = await createCheckout(
        request.planCode,
        request.successUrl,
        request.cancelUrl,
        idempotencyKey,
        request.paymentService,
      );

      logPaymentEvent("Checkout created", {
        paymentId: checkoutResponse.paymentId,
        amount: checkoutResponse.amount,
        providerSessionId: checkoutResponse.providerSessionId,
      });

      // 2. 아임포트 SDK 로드 대기
      logPaymentEvent("Waiting for SDK to load", {});
      const IMP = await loadPortOne();

      if (!IMP) {
        logPaymentEvent("SDK load failed", {});
        throw new PaymentError(
          PaymentErrorCode.SDK_NOT_LOADED,
          "아임포트 SDK를 불러올 수 없습니다. 페이지를 새로고침해주세요.",
        );
      }

      logPaymentEvent("SDK loaded successfully", { hasIMP: !!IMP });

      // 3. SDK 초기화
      try {
        const merchantCode = getPortOneMerchantCode();
        IMP.init(merchantCode);
        logPaymentEvent("SDK initialized", { merchantCode });
      } catch (initError) {
        logPaymentEvent("SDK initialization failed", { error: initError });
        throw new PaymentError(
          PaymentErrorCode.SDK_NOT_LOADED,
          "아임포트 SDK 초기화에 실패했습니다.",
          initError as Error,
        );
      }

      // 4. 결제 요청
      return new Promise((resolve) => {
        // pg 와 customer_uid 는 서버가 정한 값을 그대로 쓴다. 여기에 자체 PG_MAP 을 두면
        // 서버 매핑과 갈라진다 — 실제로 갈라져 있었고, 서버가 정기결제 채널을 내려주는 동안
        // 이 훅이 단건 채널(kakaopay.TC0ONETIME)로 결제창을 열어 빌링키가 발급되지 않았다.
        const pg = checkoutResponse.pg;

        const paymentData: IamportRequestPayData = {
          pg,
          pay_method: "card",
          merchant_uid: checkoutResponse.providerSessionId,
          amount: checkoutResponse.amount,
          name: "OTT 멤버십 구독",
          buyer_email: user?.email || "",
          buyer_name: user?.username || "",
          // 모바일 리다이렉트 흐름에서는 이 콜백이 실행되지 않으므로, success 페이지가
          // paymentId/type + 아임포트가 붙여주는 imp_uid로 확정 API를 호출한다.
          m_redirect_url:
            window.location.origin +
            "/membership/success?paymentId=" +
            checkoutResponse.paymentId +
            "&type=membership",
          popup: preferPortOnePopup(),
          // 정기결제 채널일 때만 채워진다. 이 값이 있어야 결제창이 결제와 동시에 빌링키를 발급해
          // 이 이름에 묶어 두고, 이후 자동 청구가 그 이름으로 재청구한다. 없으면 단건 결제로 끝난다.
          ...(checkoutResponse.customerUid
            ? { customer_uid: checkoutResponse.customerUid }
            : {}),
        };

        logPaymentEvent("Payment data prepared", {
          pg,
          amount: paymentData.amount,
          merchant_uid: paymentData.merchant_uid,
        });

        IMP.request_pay(paymentData, async (response: IamportResponse) => {
          // 결제창이 끝났다(성공·실패·취소 모두). 이 의도의 체크아웃은 다 썼으므로 다음 결제는 새 키로 한다.
          checkoutIntentRef.current = null;
          // 응답 타입 검증
          const validation = validatePaymentResponse(response);
          if (!validation.isValid) {
            logPaymentEvent("Invalid payment response", {
              response,
              error: validation.error,
            });
            const error = new PaymentError(
              PaymentErrorCode.INVALID_PAYMENT_DATA,
              validation.error || "유효하지 않은 결제 응답입니다.",
            );
            setPaymentState((prev) => ({ ...prev, status: "error", error }));
            resolve({
              success: false,
              errorMessage: error.message,
            });
            return;
          }

          logPaymentEvent("Payment response received", {
            success: response.success,
            error_msg: response.error_msg,
            imp_uid: response.imp_uid,
          });

          if (response.success) {
            // 클라이언트 결제 확정(동기 주 경로): imp_uid로 서버가 아임포트에 재검증 후 멤버십 즉시 지급.
            // 결제창의 success 는 "결제창이 끝났다"일 뿐이다. 서버가 확정했는지는 서버 상태로만 판정한다(#192).
            // 예전에는 확정 호출이 실패하면 이유와 상관없이 성공으로 간주해, 서버가 거절한 결제(PG 재검증 불일치)도
            // 멤버십 없이 "결제 완료"로 안내됐다.
            const paymentId = checkoutResponse.paymentId;
            const impUid = response.imp_uid;
            const outcome = await resolveConfirmOutcome(
              () =>
                impUid
                  ? completePayment(paymentId, impUid)
                  : Promise.reject(new Error("imp_uid 없음")),
              async () => (await checkPaymentStatus(paymentId)).status,
            );
            logPaymentEvent("Payment confirm outcome", { paymentId, outcome });

            if (outcome === "FAILED") {
              const error = new PaymentError(
                PaymentErrorCode.PAYMENT_FAILED,
                "결제가 완료되지 않았습니다.",
              );
              setPaymentState((prev) => ({ ...prev, status: "error", error }));
              resolve({ success: false, errorMessage: error.message });
              return;
            }
            if (outcome === "PENDING") {
              // 실패가 아니다. 웹훅·대사(10분 주기)가 곧 확정할 수 있고, 금액 불일치처럼 사람이 봐야 하는 건이면
              // 대사가 경보를 올린다. 다시 결제하면 이중 결제가 되므로 그것부터 막는다.
              setPaymentState((prev) => ({ ...prev, status: "idle" }));
              resolve({
                success: false,
                pending: true,
                paymentId,
                errorMessage:
                  "결제 확인이 늦어지고 있습니다. 확인되면 멤버십이 자동으로 시작되니 다시 결제하지 마세요. 10분이 지나도 시작되지 않으면 고객센터로 문의해 주세요.",
              });
              return;
            }
            setPaymentState((prev) => ({
              ...prev,
              status: "success",
              paymentId: checkoutResponse.paymentId,
            }));
            resolve({
              success: true,
              paymentId: checkoutResponse.paymentId,
              redirectUrl: checkoutResponse.redirectUrl,
            });
          } else {
            logPaymentEvent("Payment failed", {
              error_msg: response.error_msg,
              imp_uid: response.imp_uid,
            });
            const error = new PaymentError(
              PaymentErrorCode.PAYMENT_FAILED,
              response.error_msg || "결제에 실패했습니다.",
            );
            setPaymentState((prev) => ({ ...prev, status: "error", error }));
            resolve({
              success: false,
              errorMessage: error.message,
            });
          }
        });
      });
    } catch (err) {
      logPaymentEvent("Payment process error", { error: err });

      let paymentError: PaymentError;
      const response = (
        err as {
          response?: { status?: number; data?: { message?: string } };
        }
      )?.response;
      // 서버가 답한 실패면 이 의도는 끝났다(다음 시도는 새 키). 응답 자체를 못 받았으면 같은 키로 다시 보내야
      // 서버가 이미 만든 결제를 그대로 돌려준다.
      if (response) {
        checkoutIntentRef.current = null;
      }
      if (err instanceof PaymentError) {
        paymentError = err;
      } else if (response?.status === 409 || response?.status === 422) {
        // 서버가 체크아웃을 거절했다. 결제창은 열리지 않았고 돈도 나가지 않았다. 409 는 이미 이용 중인
        // 멤버십(#193)이거나 쓸 수 없는 멱등키, 422 는 같은 키에 다른 내용(#191)이다. 이유는 서버 문구로 보여준다.
        // 네트워크 오류 문구로 덮으면 사용자가 다시 시도할 이유를 찾는다.
        paymentError = new PaymentError(
          PaymentErrorCode.CHECKOUT_FAILED,
          response.data?.message || "결제를 시작할 수 없습니다.",
          err as Error,
        );
      } else if (err instanceof Error) {
        paymentError = new PaymentError(
          PaymentErrorCode.NETWORK_ERROR,
          "결제 처리 중 오류가 발생했습니다.",
          err,
        );
      } else {
        paymentError = new PaymentError(
          PaymentErrorCode.NETWORK_ERROR,
          "알 수 없는 오류가 발생했습니다.",
        );
      }

      setPaymentState((prev) => ({
        ...prev,
        status: "error",
        error: paymentError,
      }));
      return {
        success: false,
        errorMessage: paymentError.message,
      };
    }
    // 로딩 상태는 결제 콜백(성공/실패)과 catch에서 종료시킨다.
    // (과거 버그) finally에서 즉시 status를 idle로 되돌려, IMP.request_pay 콜백이
    // 끝나기 전에 isLoading이 꺼지면서 결제창이 떠 있는 동안 결제 버튼이 다시 활성화됐다.
  };

  const checkPayment = async (paymentId: number): Promise<boolean> => {
    try {
      const status = await checkPaymentStatus(paymentId);
      logPaymentEvent("Payment status checked", {
        paymentId,
        status: status.status,
      });
      return status.status === "SUCCEEDED";
    } catch (err) {
      logPaymentEvent("Payment status check failed", { paymentId, error: err });
      return false;
    }
  };

  // 재시도 로직이 포함된 결제 처리
  const processPaymentWithRetry = async (
    request: PaymentRequest,
    maxRetries = 3,
  ): Promise<PaymentResult> => {
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        setPaymentState((prev) => ({ ...prev, retryCount: attempt - 1 }));
        const result = await processPayment(request);

        if (result.success) {
          setPaymentState((prev) => ({ ...prev, retryCount: 0 }));
          return result;
        }
        // 확인 중인 결제를 다시 시도하면 새 결제창이 열려 이중 결제가 된다(#192)
        if (result.pending) {
          return result;
        }

        // 실패한 경우 재시도 가능한지 확인
        if (attempt === maxRetries) {
          return result;
        }

        // 재시도 가능한 에러인지 확인 (현재는 단순히 attempt < maxRetries로 처리)
        logPaymentEvent("Retrying payment", { attempt, maxRetries });
        await delay(1000 * attempt); // 지수 백오프
      } catch (error) {
        if (attempt === maxRetries) {
          throw error;
        }
        logPaymentEvent("Payment attempt failed, retrying", { attempt, error });
        await delay(1000 * attempt);
      }
    }

    throw new PaymentError(
      PaymentErrorCode.PAYMENT_FAILED,
      "최대 재시도 횟수를 초과했습니다.",
    );
  };

  return {
    processPayment,
    processPaymentWithRetry,
    checkPayment,
    isLoading: paymentState.status === "loading",
    error: paymentState.error?.message || null,
    paymentState,
    clearError: () =>
      setPaymentState((prev) => ({ ...prev, error: undefined })),
  };
};
