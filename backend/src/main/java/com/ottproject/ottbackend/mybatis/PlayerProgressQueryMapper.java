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
 * - mergeProgress: 값이 불완전한 진행률 한 건을 기존 값과 병합해 반영
 * - updateHiddenInRecent: 최근본 목록 숨김 여부 일괄 변경
 * - deleteProgressByUserAndEpisodes: 진행률 일괄 삭제
 *
 * 왜 진행률 쓰기가 전부 여기에 있는가
 * - episode_progress 를 쓰는 경로는 한 가지 수단만 쓴다(ARCHITECTURE 3). 버퍼 flush 의 배치 upsert 가
 *   ON CONFLICT 단조 증가 가드를 들고 있어 JPA 로 옮길 수 없으므로, 나머지 쓰기를 이쪽으로 모았다.
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

	/**
	 * 진행률 단건 병합 upsert
	 * - null 로 넘긴 값은 기존 값을 그대로 둔다(위치·길이 중 하나만 도착한 경우).
	 * - 위치는 항상 길이 이하로 잘라 넣는다.
	 */
	int mergeProgress(
		@Param("userId") Long userId,
		@Param("episodeId") Long episodeId,
		@Param("positionSec") Integer positionSec,
		@Param("durationSec") Integer durationSec,
		@Param("updatedAt") LocalDateTime updatedAt
	);

	/**
	 * 최근본 목록 숨김 여부 일괄 변경(시청 기록 자체는 남긴다)
	 */
	int updateHiddenInRecent(
		@Param("userId") Long userId,
		@Param("episodeIds") List<Long> episodeIds,
		@Param("hidden") boolean hidden
	);

	/**
	 * 진행률 일괄 삭제(정주행 목록에서 완전 삭제)
	 */
	int deleteProgressByUserAndEpisodes(
		@Param("userId") Long userId,
		@Param("episodeIds") List<Long> episodeIds
	);
}


