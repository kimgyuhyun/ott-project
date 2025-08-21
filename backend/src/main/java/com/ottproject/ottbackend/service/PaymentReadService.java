package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PaymentHistoryItemDto;
import com.ottproject.ottbackend.mybatis.PaymentQueryMapper;
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
}


