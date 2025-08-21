package com.ottproject.ottbackend.mybatis; // 플레이어 진행률 MyBatis 매퍼

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * PlayerProgressQueryMapper
 *
 * 큰 흐름
 * - 플레이어 진행률 관련 읽기 전용 쿼리를 수행하는 MyBatis 매퍼.
 *
 * 메서드 개요
 * - sumWatchedSecondsSincePaidEpisodes: 결제 시각 이후 4화 이상 누적 시청 초 합계
 */
@Mapper
public interface PlayerProgressQueryMapper {

	/**
	 * 결제 시각 이후, 4화 이상 에피소드의 누적 시청 초 합계
	 */
	Integer sumWatchedSecondsSincePaidEpisodes(
		@Param("userId") Long userId,
		@Param("since") LocalDateTime since
	);
}


