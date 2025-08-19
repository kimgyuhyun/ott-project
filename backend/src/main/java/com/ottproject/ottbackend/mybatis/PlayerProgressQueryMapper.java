package com.ottproject.ottbackend.mybatis; // 플레이어 진행률 MyBatis 매퍼

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 에피소드 시청 진행률 조회용 MyBatis 매퍼
 * - 1~3화는 무료이므로 제외, 4화 이상만 집계
 * - 결제시각 이후 누적 시청 초 합계를 반환
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


