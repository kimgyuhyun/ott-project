package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.SkipMetaResponseDto;
import com.ottproject.ottbackend.enums.SkipType;
import com.ottproject.ottbackend.entity.SkipUsage;
import com.ottproject.ottbackend.repository.EpisodeRepository;
import com.ottproject.ottbackend.repository.EpisodeSkipMetaRepository;
import com.ottproject.ottbackend.repository.SkipUsageRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PlayerSkipService
 *
 * 큰 흐름
 * - 스킵 메타 조회와 스킵 사용 로깅을 제공한다.
 *
 * 메서드 개요
 * - get: 에피소드별 스킵 메타 조회
 * - trackUsage: 스킵 사용 로그 적재(열거/문자열 오버로드)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerSkipService { // 스킵 메타 조회
	private final EpisodeSkipMetaRepository repo; // 메타 리포지토리
	private final SkipUsageRepository skipUsageRepository; // 사용 로그 리포지토리
	private final UserRepository userRepository; // 사용자 조회
	private final EpisodeRepository episodeRepository; // 에피소드 조회
	public SkipMetaResponseDto get(Long episodeId) { // 메타 조회
		return repo.findByEpisodeId(episodeId)
				.map(m -> new SkipMetaResponseDto(m.getIntroStart(), m.getIntroEnd(), m.getOutroStart(), m.getOutroEnd())) // DTO 변환
				.orElse(new SkipMetaResponseDto(null,null,null,null)); // 없으면 null 필드 반환
	}

	// 통계/로그 적재: 간단 DB 저장
	@Transactional
	public void trackUsage(Long userId, Long episodeId, SkipType type, Integer atSec) {
		var usage = SkipUsage.builder() // 엔티티 빌드
					.user(userId != null ? userRepository.findById(userId).orElse(null) : null) // 사용자(비로그인 null)
					.episode(episodeRepository.findById(episodeId).orElseThrow()) // 에피소드 로드
					.type(type) // 타입
					.atSec(atSec) // 시점
					.build();
		skipUsageRepository.save(usage); // 저장
	}

	@Transactional
	public void trackUsage(Long userId, Long episodeId, String type, Integer atSec) { // 문자열 타입 오버로드
		SkipType t = null; // 파싱 결과
		if (type != null) {
			try { t = SkipType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {} // 안전 파싱
		}
		if (t == null) return; // 유효하지 않으면 무시
		trackUsage(userId, episodeId, t, atSec); // 위 메서드 재사용
	}
}