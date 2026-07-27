package com.ottproject.ottbackend.mybatis; // 플레이어 진행률 MyBatis 매퍼

import com.ottproject.ottbackend.dto.EpisodeProgressFlushDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PlayerProgressQueryMapper
 *
 * 큰 흐름
 * - 플레이어 진행률 관련 쿼리를 수행하는 MyBatis 매퍼.
 *
 * 메서드 개요
 * - sumWatchedSecondsSincePaidEpisodes: 결제 시각 이후 4화 이상 누적 시청 초 합계
 * - upsertProgressBatch: Redis 버퍼에서 모은 진행률을 한 문장으로 일괄 반영
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

	/**
	 * 진행률 배치 upsert(있으면 갱신, 없으면 생성)
	 */
	int upsertProgressBatch(@Param("rows") List<EpisodeProgressFlushDto> rows);
}


