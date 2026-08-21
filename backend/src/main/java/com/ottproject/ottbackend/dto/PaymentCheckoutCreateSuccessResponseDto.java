package com.ottproject.ottbackend.dto;

/**
 * 체크아웃(결제창) 생성 성공 응답 DTO
 *
 * 큰 흐름
 * - 결제창으로 이동할 URL과 내부 결제 ID를 반환한다.
 *
 * 필드 개요
 * - redirectUrl/paymentId
 */
public class PaymentCheckoutCreateSuccessResponseDto { // 체크아웃 생성 성공 응답 DTO 클래스 시작
    public String redirectUrl; // 결제창 리다이렉트 URL
    public Long paymentId; // 내부 결제 레코드 ID(상태 추적용)
    public String providerSessionId; // 게이트웨이 세션/merchant_uid
    public Long amount; // 결제 금액(검증용)
    public String pg; // 프론트 SDK용 PortOne PG 코드(kakaopay.TCSUBSCRIP|kakaopay.TC0ONETIME|tosspayments|nice)
    // 결제창에 넘길 빌링키 식별자. 정기결제 채널일 때만 채워지고 아니면 null 이다.
    // 이 값이 있으면 결제창이 결제와 동시에 빌링키를 발급해 게이트웨이 쪽에 이 값으로 묶어 보관한다.
    public String customerUid;
}
