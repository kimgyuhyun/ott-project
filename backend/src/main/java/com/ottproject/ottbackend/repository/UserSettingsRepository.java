package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 사용자 재생 설정 리포지토리
 */
@Repository // 스프링 빈 등록
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> { // 설정 리포지토리
	Optional<UserSettings> findByUserId(Long userId); // user.id 경로 탐색
}
