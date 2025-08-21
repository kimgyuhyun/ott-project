package com.ottproject.ottbackend.mybatis; // MyBatis 매퍼 패키지 선언

import com.ottproject.ottbackend.dto.PaymentHistoryItemDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentQueryMapper
 *
 * 큰 흐름
 * - 결제/환불 이력 등 읽기 전용 복잡 쿼리를 담당하는 MyBatis 매퍼.
 *
 * 메서드 개요
 * - listHistory: 사용자 결제 이력 목록(기간 필터 선택)
 * - sumWatchedSecondsSincePaidEpisodes: 결제시각 이후 4화 이상 누적 시청 초 합
 */
@Mapper // MyBatis 매퍼로 등록
public interface PaymentQueryMapper { // 결제 조회 매퍼 인터페이스 시작

	List<PaymentHistoryItemDto> listHistory( // 사용자 결제 이력 조회
		@Param("userId") Long userId, // 사용자 ID
		@Param("start") LocalDateTime start, // 조회 시작 시각(선택 가능)
		@Param("end") LocalDateTime end // 조회 종료 시각(선택 가능)
	);

	/** 누적 시청 초 합(결제시각 이후 & 4화 이상만) */
	Integer sumWatchedSecondsSincePaidEpisodes(
		@Param("userId") Long userId,
		@Param("since") LocalDateTime since
	);
}


