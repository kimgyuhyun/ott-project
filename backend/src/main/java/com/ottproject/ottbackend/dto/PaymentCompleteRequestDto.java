package com.ottproject.ottbackend.dto; // DTO 패키지 선언

/**
 * 클라이언트 결제 확정 요청 DTO
 *
 * 큰 흐름
 * - 결제창(SDK) 성공 콜백에서 전달하는 아임포트 결제 식별자(imp_uid)를 담는다.
 * - 서버는 이 imp_uid로 아임포트 API에 결제 상태를 재검증한 뒤 확정한다.
 */
public class PaymentCompleteRequestDto { // 결제 확정 요청 DTO
	public String impUid; // 아임포트 결제 식별자(imp_uid)
}
