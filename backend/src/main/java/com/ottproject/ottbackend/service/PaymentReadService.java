package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PaymentHistoryItemDto;
import com.ottproject.ottbackend.dto.PaymentResultResponseDto;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentReadService
 *
 * 큰 흐름
 * - 결제/환불 이력 등 읽기 전용 조회를 담당한다(MyBatis 연동).
 *
 * 메서드 개요
 * - listHistory: 사용자 결제 이력 목록(기간 필터)
 * - sumWatchedSecondsSincePaidEpisodes: 결제시각 이후 4화 이상 누적 시청 초 합
 */
@Service // 빈 등록
@RequiredArgsConstructor // 생성자 주입
@Transactional(readOnly = true) // 읽기 전용 트랜잭션
public class PaymentReadService { // 읽기 서비스
	private final PaymentQueryMapper paymentQueryMapper; // MyBatis 매퍼

	public List<PaymentHistoryItemDto> listHistory(Long userId, LocalDateTime start, LocalDateTime end) { // 이력 조회
		return paymentQueryMapper.listHistory(userId, start, end); // 매퍼 호출
	}

	public int sumWatchedSecondsSincePaidEpisodes(Long userId, LocalDateTime since) { // 4화 이상 누적 시청 합
		Integer v = paymentQueryMapper.sumWatchedSecondsSincePaidEpisodes(userId, since); // 매퍼 호출
		return v == null ? 0 : v; // null 방어
	}
	
	/**
	 * 결제 상태 조회
	 * - 결제 ID로 결제 상태를 조회하여 DTO로 반환
	 */
	public PaymentResultResponseDto getPaymentStatus(Long paymentId, Long userId) {
		// 결제 정보 조회 (PaymentQueryMapper에 추가 필요)
		Payment payment = paymentQueryMapper.findById(paymentId);
		if (payment == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다");
		}
		
		// 사용자 권한 확인
		if (!payment.getUser().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 결제만 조회할 수 있습니다");
		}
		
		// DTO 변환
		PaymentResultResponseDto result = new PaymentResultResponseDto();
		result.paymentId = payment.getId();
		result.status = payment.getStatus();
		result.providerPaymentId = payment.getProviderPaymentId();
		result.receiptUrl = payment.getReceiptUrl();
		
		// 상태별 시각 설정
		switch (payment.getStatus()) {
			case SUCCEEDED:
				result.occurredAt = payment.getPaidAt();
				break;
			case FAILED:
				result.occurredAt = payment.getFailedAt();
				break;
			case CANCELED:
				result.occurredAt = payment.getCanceledAt();
				break;
			case REFUNDED:
				result.occurredAt = payment.getRefundedAt();
				break;
			default:
				result.occurredAt = payment.getCreatedAt();
		}
		
		return result;
	}
}


