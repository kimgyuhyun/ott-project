package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.UserSettingsDto;
import com.ottproject.ottbackend.entity.UserSettings;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.UserSettingsRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SettingsService
 *
 * 큰 흐름
 * - 사용자 재생 환경 설정을 조회/업데이트한다.
 *
 * 메서드 개요
 * - get: 사용자 설정 조회(없으면 기본값)
 * - update: 부분 갱신(널이 아닌 필드만 반영)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService { // 사용자 재생 설정
	private final UserSettingsRepository repo; private final UserRepository userRepo; // 의존성

	/**
	 * 사용자 설정 조회(없으면 기본값 반환)
	 */
	@Transactional(readOnly = true)
	public UserSettingsDto get(Long userId) {
		return repo.findByUserId(userId) // 1:1 설정 조회
				.map(u -> UserSettingsDto.builder()
						.autoSkipIntro(u.getAutoSkipIntro())
						.autoSkipEnding(u.getAutoSkipEnding())
						.defaultQuality(u.getDefaultQuality())
						.autoNextEpisode(u.getAutoNextEpisode())
						.build())
				.orElse(UserSettingsDto.builder()
						.autoSkipIntro(true).autoSkipEnding(true).defaultQuality("auto").autoNextEpisode(true)
						.build()); // 기본값
	}
	/**
	 * 사용자 설정 업데이트(부분 갱신)
	 */
	public void update(Long userId, UserSettingsDto dto) {
		UserSettings entity = repo.findByUserId(userId)
				.orElseGet(() -> UserSettings.builder().user(userRepo.findById(userId).orElseThrow()).build()); // 없으면 생성
		if (dto.getAutoSkipIntro()!=null) entity.setAutoSkipIntro(dto.getAutoSkipIntro()); // 인트로
		if (dto.getAutoSkipEnding()!=null) entity.setAutoSkipEnding(dto.getAutoSkipEnding()); // 엔딩
		if (dto.getDefaultQuality()!=null) entity.setDefaultQuality(dto.getDefaultQuality()); // 화질
		if (dto.getAutoNextEpisode()!=null) entity.setAutoNextEpisode(dto.getAutoNextEpisode()); // 자동 다음화
		repo.save(entity); // 저장
	}
}