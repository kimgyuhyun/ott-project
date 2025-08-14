package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Subscription;
import com.ottproject.ottbackend.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 구독 리포지토리
 * - 사용자 활성 구독(기간 유효) 조회
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
	Optional<Subscription> findFirstByUser_IdAndStatusAndStartAtBeforeAndEndAtAfterOrEndAtIsNullOrderByStartAtDesc(
			Long userId, SubscriptionStatus status, LocalDateTime now1, LocalDateTime now2); // 가장 최근 시작된 유효 구독
}


