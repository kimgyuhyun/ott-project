package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserSettingsRepository
 *
 * 큰 흐름
 * - 사용자 재생 설정 엔티티를 관리하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByUserId: 1:1 설정을 사용자 ID로 조회
 */
@Repository // 스프링 빈 등록
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> { // 설정 리포지토리
	Optional<UserSettings> findByUserId(Long userId); // user.id 경로 탐색
	
	Optional<UserSettings> findByUser_Id(Long userId); // user.id 경로 탐색 (별칭)
}
