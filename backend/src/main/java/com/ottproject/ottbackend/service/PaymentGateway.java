package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;

/**
 * PaymentGateway
 *
 * 큰 흐름
 * - 외부 결제 게이트웨이(아임포트/Stripe 등) 연동을 표준화한다.
 *
 * 메서드 개요
 * - createCheckoutSession: 체크아웃 세션 생성
 * - prepare: 이미 발급한 세션 식별자에 청구 금액 고정
 * - issueRefund: 환불 처리
 * - findRefundStatus: 환불 여부 역조회
 * - chargeWithSavedMethod: 저장수단 자동 청구
 * - verifyWebhookBasicValidation: 웹훅 기본 검증
 * - verifyPayment: 결제 성공 주장 재검증
 * - findPaymentBySessionId: 세션 식별자로 결제 상태 역조회(대사용)
 *
 * 용어
 * - providerSessionId: 결제 건마다 호출자가 만들어 게이트웨이에 등록하는 주문 식별자(아임포트의 merchant_uid).
 * - providerPaymentId: 게이트웨이가 승인 후 부여하는 결제 식별자(아임포트의 imp_uid).
 */
public interface PaymentGateway { // 게이트웨이 추상화 시작

    /**
     * 체크아웃 세션 생성 (prepare-only)
     *
     * 동작:
     * - 게이트웨이에 /payments/prepare 등록만 수행하여 merchant_uid(세션)와 금액을 고정한다.
     * - 실제 결제창 호출은 프론트엔드 JS SDK가 수행한다.
     *
     * 반환:
     * - sessionId: 게이트웨이 세션 식별자(merchant_uid)
     * - redirectUrl: (prepare-only에서는 사용 안 함, null 권장)
     */
    CheckoutSession createCheckoutSession(
            User user,
            MembershipPlan plan,
            String successUrl,
            String cancelUrl,
            String paymentService,
            long amount); // 세션 생성 시그니처

    final class CheckoutSession { // 반환 DTO 내장형
        public String sessionId; // 게이트웨이 세션 ID
        public String redirectUrl; // 결제창 이동 URL
    }

    /**
     * 결제 사전 등록 — 호출자가 직접 만든 providerSessionId 에 청구 금액을 고정한다.
     * - 차액(proration) 결제처럼 세션 식별자를 호출자가 정하는 흐름에서 쓴다.
     *   createCheckoutSession 과 같은 등록을 거쳐야 verifyPayment 가 같은 기준으로 금액을 대조할 수 있다.
     * - 등록에 실패하면 예외를 던진다. 조용히 넘어가면 금액 고정 없이 결제창이 열린다.
     */
    void prepare(String providerSessionId, long amount);

    /**
     * 환불 요청
     * - 전액 또는 일부 환불 처리 후 결과 반환
     */
    RefundResult issueRefund(String providerPaymentId, long amount); // 환불 실행 시그니처

    final class RefundResult { // 환불 결과 내장형
        public String providerRefundId; // 외부 환불 식별자
        public java.time.LocalDateTime refundedAt; // 환불 완료 시각
    }

    /**
     * 환불 역조회 결과
     * - REFUNDED: 환불이 실제로 나갔음이 확인됨
     * - NOT_REFUNDED: 환불이 나가지 않았음이 확인됨
     * - UNKNOWN: 판정 불가(조회 실패/비2xx/응답 파싱 실패). 모르는 것을 "안 나감"으로 읽으면
     *   선점이 풀려 이중 환불이 되므로, 조회 예외는 절대 NOT_REFUNDED 로 삼키지 않는다.
     */
    enum RefundStatus {
        REFUNDED,
        NOT_REFUNDED,
        UNKNOWN
    }

    /**
     * 환불 여부 역조회
     * - 게이트웨이 호출이 예외로 끝나 결과를 모르는 선점을 대사 배치가 판정하는 데 쓴다.
     * - 이 프로젝트의 환불은 항상 전액이므로 부분환불을 구분하지 않는다.
     */
    RefundStatus findRefundStatus(String providerPaymentId);

    /**
     * 저장된 결제수단(빌링키)로 자동 청구 수행
     * - 성공 시 외부 결제 식별자/시각/영수증 URL 반환
     * - merchantUid 는 호출자가 결정한다. 게이트웨이가 매번 새로 만들면 같은 청구 시도를 두 번 불러도
     *   게이트웨이 쪽에서 구분할 수 없어, 중복 배달이 그대로 이중 청구가 된다.
     */
    ChargeResult chargeWithSavedMethod(
            String providerCustomerId,
            String providerMethodId,
            String merchantUid,
            long amount,
            String currency,
            String description);

    /**
     * 빌링키 발급 여부 확인
     * - customerUid 는 우리가 정하는 값이고, 게이트웨이가 실제 빌링키를 거기에 묶어 자기 쪽에 보관한다.
     *   그래서 "발급됐는가"는 우리 DB 로 알 수 없고 게이트웨이에 물어야만 알 수 있다.
     * - 결제수단을 등록하기 전에 이걸 확인한다. 확인 없이 등록하면 빌링키가 없는 값이 저장 결제수단으로
     *   남아, 자동 청구가 그 값을 customer_uid 로 보내고 게이트웨이가 매번 거절한다.
     * @return 발급이 확인되면 true. 조회 실패나 판정 불가도 false 다(없는 것으로 취급해야 안전하다).
     */
    boolean hasBillingKey(String customerUid);

    final class ChargeResult {
        public String providerPaymentId;
        public java.time.LocalDateTime paidAt;
        public String receiptUrl;
    }

    /**
     * 웹훅 기본 검증
     * - 게이트웨이가 보낸 웹훅 데이터의 기본 유효성을 검증합니다.
     * - 실제 검증은 웹훅 처리 후 API 호출로 수행합니다.
     */
    boolean verifyWebhookBasicValidation(String rawBody, java.util.Map<String, String> headers);

    /**
     * 결제 성공 주장 재검증
     * - 웹훅 페이로드나 결제창 콜백은 위조될 수 있으므로, 게이트웨이에 직접 물어 상태·금액·세션 식별자가
     *   모두 일치할 때만 true 를 돌려준다.
     * - 판정할 수 없는 경우(조회 실패, 응답 파싱 실패)는 false 다. 여기서는 fail-closed 가 맞다.
     *   확정을 막을 뿐이고, 실제로 결제된 건은 대사(findPaymentBySessionId)가 뒤에서 다시 집는다.
     */
    boolean verifyPayment(String providerPaymentId, String providerSessionId, long expectedAmount);

    /**
     * 세션 식별자로 결제 상태 역조회(대사용)
     * - 아직 승인 전인 PENDING 결제는 providerPaymentId 가 없으므로 세션 식별자로 친다.
     * - 조회 실패와 "결제 시도 기록 없음"을 모두 found=false 로 돌려준다. 예외를 던지지 않는다.
     *   대사 배치가 건별로 돌기 때문에, 한 건의 조회 실패가 배치 전체를 멈추면 안 된다.
     * - 환불 여부 판정에는 쓰지 않는다. 조회 실패를 "안 나감"으로 읽으면 이중 환불이 되므로
     *   그쪽은 UNKNOWN 을 구분하는 findRefundStatus 를 쓴다.
     */
    ReconcileResult findPaymentBySessionId(String providerSessionId);

    /**
     * 역조회한 결제의 상태
     * - READY: 아직 승인 전(사전 등록만 된 상태)
     * - PAID: 승인 완료
     * - FAILED: 승인 실패
     * - CANCELLED: 승인 후 취소됨
     * - UNKNOWN: 게이트웨이가 이 어댑터가 모르는 값을 돌려줬다. 판정 불가이므로 호출자는 상태를 바꾸지 않고
     *   결제를 미확정으로 남긴다(READY 와 같은 처리). 결제사가 어휘를 바꾸거나 새 상태를 추가하면
     *   여기로 떨어지므로, 어댑터는 UNKNOWN 을 만들 때 반드시 로그를 남긴다. 조용히 삼키면 그 결제는
     *   대사 배치가 영원히 다시 집는 미결 건으로 남는다.
     *
     * 문자열 대신 열거형인 이유
     * - 원문 상태값을 그대로 실어 나르면 어휘 해석이 호출부마다 흩어진다. 실제로 대사 스위치 두 곳이
     *   cancelled/canceled 두 철자를 모두 받고 있었다(아임포트는 cancelled 만 보낸다). 결제사가 대문자로
     *   보내는 순간 양쪽 스위치가 default 로 떨어져 결제가 조용히 미결로 남는다.
     * - 원문을 아는 유일한 지점은 어댑터이므로 정규화도 거기서 끝낸다. 환불 쪽 RefundStatus 와 같은 방식이다.
     */
    enum ReconcileStatus {
        READY,
        PAID,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    /**
     * 결제 상태 역조회 결과
     * - found=false 면 나머지 필드는 의미가 없다.
     */
    final class ReconcileResult {
        public boolean found; // 게이트웨이에 결제 시도 기록이 존재하는지
        public ReconcileStatus status; // 정규화된 결제 상태
        public String providerPaymentId; // 게이트웨이가 부여한 결제 식별자
        public long amount; // 실제 결제 금액
        public String receiptUrl; // 영수증 URL
    }

    /**
     * 결제 실패 유형
     * - HARD_DECLINE: 영구적 실패(재시도 불가)
     * - SOFT_DECLINE: 일시적 실패(재시도 가능)
     * - AMBIGUOUS: 승인 여부 불명(응답 타임아웃 등). 승인됐는데 실패로 단정하면 다음 시도가
     *   새 merchant_uid 로 다시 청구돼 이중 청구가 된다. 던닝을 진행시키지 말고 대사로 확인해야 한다.
     */
    enum FailureType {
        HARD_DECLINE,
        SOFT_DECLINE,
        AMBIGUOUS
    }

    /**
     * 결제 예외(유형/코드/메시지 포함)
     */
    final class ChargeException extends RuntimeException {
        public final FailureType failureType;
        public final String errorCode;

        public ChargeException(FailureType type, String code, String message) {
            super(message);
            this.failureType = type;
            this.errorCode = code;
        }
    }
}
