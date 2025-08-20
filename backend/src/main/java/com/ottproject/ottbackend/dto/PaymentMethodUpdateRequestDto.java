package com.ottproject.ottbackend.dto;

/**
 * 결제수단 부분 수정 요청 DTO
 *
 * 큰 흐름(Javadoc):
 * - 결제수단의 일부 속성만 부분적으로 업데이트할 때 사용합니다.
 * - Null 값은 해당 필드를 변경하지 않음을 의미합니다.
 */
public class PaymentMethodUpdateRequestDto { // 결제수단 부분 수정 요청 DTO 선언
    public Integer expiryMonth; // 선택: 만료 월(Null이면 변경 없음)
    public Integer expiryYear;  // 선택: 만료 연도(Null이면 변경 없음)
    public Integer priority;    // 선택: 폴백 우선순위(낮을수록 우선, Null이면 변경 없음)
    public String label;        // 선택: 별칭(Null이면 변경 없음)
}


