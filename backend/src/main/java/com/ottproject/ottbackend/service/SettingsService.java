package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.UserSettingsDto;
import com.ottproject.ottbackend.entity.UserSettings;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SettingsService
 *
 * 큰 흐름
 * - 사용자 재생 환경 설정을 조회하고 관리한다.
 *
 * 메서드 개요
 * - get: 사용자별 재생 설정 조회
 * - update: 사용자 재생 설정 갱신 (부분 갱신)
 */
@Service
@RequiredArgsConstructor
public class SettingsService { // 사용자 재생 설정

    private final UserSettingsRepository userSettingsRepository;

    /**
     * 사용자별 재생 설정 조회
     */
    @Transactional(readOnly = true)
    public UserSettingsDto get(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(this::convertToDto)
                .orElse(UserSettingsDto.builder()
                        .autoSkipIntro(true)
                        .autoSkipEnding(true)
                        .defaultQuality("auto")
                        .autoNextEpisode(true)
                        .theme("light") // 기본 테마
                        .build());
    }

    /**
     * 사용자 재생 설정 갱신 (부분 갱신)
     */
    @Transactional
    public void update(Long userId, UserSettingsDto dto) {
        UserSettings userSettings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setId(userId);
                    return UserSettings.builder()
                            .user(user)
                            .build();
                });

        // 부분 갱신 (null이 아닌 값만 업데이트)
        if (dto.getAutoSkipIntro() != null) {
            userSettings.setAutoSkipIntro(dto.getAutoSkipIntro());
        }
        if (dto.getAutoSkipEnding() != null) {
            userSettings.setAutoSkipEnding(dto.getAutoSkipEnding());
        }
        if (dto.getDefaultQuality() != null) {
            userSettings.setDefaultQuality(dto.getDefaultQuality());
        }
        if (dto.getAutoNextEpisode() != null) {
            userSettings.setAutoNextEpisode(dto.getAutoNextEpisode());
        }
        if (dto.getTheme() != null) {
            userSettings.setTheme(dto.getTheme());
        }

        userSettingsRepository.save(userSettings);
    }

    /**
     * DTO 변환
     */
    private UserSettingsDto convertToDto(UserSettings userSettings) {
        return UserSettingsDto.builder()
                .autoSkipIntro(userSettings.getAutoSkipIntro())
                .autoSkipEnding(userSettings.getAutoSkipEnding())
                .defaultQuality(userSettings.getDefaultQuality())
                .autoNextEpisode(userSettings.getAutoNextEpisode())
                .theme(userSettings.getTheme())
                .build();
    }
}
