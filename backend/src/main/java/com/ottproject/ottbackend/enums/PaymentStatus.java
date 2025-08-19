package com.ottproject.ottbackend.enums; // 결제 상태 enum 패키지 선언

/**
 * 결제 상태를 표현하는 열거형
 *
 * 흐름 개요:
 * - PENDING: 결제창 생성/진행 중 초기 상태
 * - SUCCEEDED: 결제 승인 완료(영수증/결제ID 확정) → 멤버십 활성/연장 트리거 대상
 * - FAILED: 승인 실패(잔액 부족, 한도 초과 등)
 * - CANCELED: 사용자가 결제 도중 취소하거나 세션 만료 등으로 중단
 * - REFUNDED: 승인된 결제에 대해 전액/부분 환불 완료
 *
 * 전이 예시:
 * - PENDING → SUCCEEDED | FAILED | CANCELED
 * - SUCCEEDED → REFUNDED
 * - FAILED/CANCELED → (종료 상태)
 */
public enum PaymentStatus { // 결제 단건의 생명주기 상태를 정의하는 enum 선언
    PENDING, // 결제 진행 중(승인 전) 상태
    SUCCEEDED, // 결제가 승인되어 성공적으로 완료된 상태
    FAILED, // 결제가 실패한 상태(승인 거절 등)
    CANCELED, // 결제 프로세스가 사용자/시스템에 의해 취소된 상태
    REFUNDED // 결제 완료 후 환불이 처리된 상태
}