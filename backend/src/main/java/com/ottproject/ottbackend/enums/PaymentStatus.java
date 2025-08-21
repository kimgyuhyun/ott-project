package com.ottproject.ottbackend.enums; // 결제 상태 enum 패키지 선언

/**
 * 결제 상태를 표현하는 열거형
 *
 * 흐름 개요
 * - PENDING: 결제창(체크아웃) 생성 후 승인 전인 초기 상태
 * - SUCCEEDED: 승인 완료(영수증/외부 결제ID 확정). 서비스 레이어에서 멤버십 활성/연장 트리거 대상
 * - FAILED: 승인 실패(잔액 부족, 한도 초과, 3DS 실패 등). 종료 상태
 * - CANCELED: 사용자 취소/세션 만료/중단 등으로 결제가 취소됨. 종료 상태
 * - REFUNDED: 승인된 결제에 대해 전액/부분 환불 완료(환불 금액/시각 기록)
 *
 * 상태 전이 예시
 * - PENDING → SUCCEEDED | FAILED | CANCELED
 * - SUCCEEDED → REFUNDED (부분/전액 환불)
 *
 * 운영 참고
 * - 웹훅 수신 시 HMAC 서명 검증 및 금액/통화/세션ID 재검증 후 상태 전환
 * - SUCCEEDED 전환 시 `providerPaymentId`/`receiptUrl` 세팅, REFUNDED 전환 시 `refundedAmount`/`refundedAt` 세팅
 */
public enum PaymentStatus { // 결제 상태
    PENDING,   // 결제 진행 중(승인 전)
    SUCCEEDED, // 결제 승인 완료(멤버십 활성/연장 트리거)
    FAILED,    // 결제 실패(승인 거절 등)
    CANCELED,  // 결제 취소(사용자/세션 만료 등)
    REFUNDED   // 환불 완료(부분/전액)
}