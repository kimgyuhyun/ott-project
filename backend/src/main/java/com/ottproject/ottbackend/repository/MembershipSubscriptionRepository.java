package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 구독 리포지토리
 * - 사용자 활성 구독(기간 유효) 조회
 */
@Repository
public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, Long> {
	// 가독성을 위한 JPQL 메서드로 대체
	@Query("""
		select s from MembershipSubscription s
		where s.user.id = :userId
		  and s.status = :status
		  and s.startAt <= :now
		  and (s.endAt is null or s.endAt >= :now)
		order by s.startAt desc
	""")
	Optional<MembershipSubscription> findActiveEffectiveByUser(
			@Param("userId") Long userId,
			@Param("status") MembershipSubscriptionStatus status,
			@Param("now") LocalDateTime now
	);
	
	Optional<MembershipSubscription> findTopByUser_IdOrderByStartAtDesc(Long userId); // 최근 구독(상태 무관)
}


