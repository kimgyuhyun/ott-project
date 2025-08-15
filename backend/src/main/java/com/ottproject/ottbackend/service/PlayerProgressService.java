package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.EpisodeProgressResponseDto;
import com.ottproject.ottbackend.entity.EpisodeProgress;
import com.ottproject.ottbackend.repository.EpisodeProgressRepository;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시청 진행률 저장/조회 서비스
 * - upsert 멱등 저장
 * - 단건/벌크 조회 제공
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerProgressService { // 진행률 저장/조회
	private final EpisodeProgressRepository repo; // 진행률
	private final UserRepository userRepo; private final EpisodeRepository episodeRepo; // 참조 조회

	/**
	 * 진행률 멱등 저장(있으면 갱신, 없으면 생성)
	 */
	public void upsert(Long userId, Long episodeId, int pos, int dur) { // 멱등 저장
		EpisodeProgress entity = repo.findByUser_IdAndEpisode_Id(userId, episodeId) // 기존 진행률 조회
				.orElseGet(() -> EpisodeProgress.builder()
						.user(userRepo.findById(userId).orElseThrow()) // 사용자 로드
						.episode(episodeRepo.findById(episodeId).orElseThrow()) // 에피소드 로드
						.positionSec(0).durationSec(0).build()); // 초기값
		entity.setPositionSec(pos); entity.setDurationSec(dur); // 값 갱신
		repo.save(entity); // 저장
	}

	/**
	 * 진행률 단건 조회
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<EpisodeProgressResponseDto> find(Long userId, Long episodeId) { // 단건 조회
		return repo.findByUser_IdAndEpisode_Id(userId, episodeId) // 진행률 조회
				.map(p -> EpisodeProgressResponseDto.builder()
						.positionSec(p.getPositionSec()) // 현재 위치
						.durationSec(p.getDurationSec()) // 총 길이
						.updatedAt(p.getUpdatedAt()) // 갱신 시각
						.build()); // DTO 변환
	}

	/**
	 * 진행률 벌크 조회(에피소드 ID 집합)
	 */
	@Transactional(readOnly = true)
	public java.util.Map<Long, EpisodeProgressResponseDto> findBulk(Long userId, java.util.Collection<Long> episodeIds) {
		java.util.List<EpisodeProgress> list = repo.findByUser_IdAndEpisode_IdIn(userId, episodeIds); // 일괄 조회
		java.util.Map<Long, EpisodeProgressResponseDto> map = new java.util.HashMap<>(); // 결과 맵
		for (EpisodeProgress p : list) { // 각 진행률 변환
			map.put(
					p.getEpisode().getId(), // 키: 에피소드 ID
					EpisodeProgressResponseDto.builder()
							.positionSec(p.getPositionSec())
							.durationSec(p.getDurationSec())
							.updatedAt(p.getUpdatedAt())
							.build()
			); // 값: DTO
		}
		return map; // 맵 반환
	}
}