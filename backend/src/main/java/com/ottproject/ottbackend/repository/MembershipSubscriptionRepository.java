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
 * MembershipSubscriptionRepository
 *
 * 큰 흐름
 * - 구독 엔티티의 CUD 및 상태/기간 기준 조회를 제공하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findActiveEffectiveByUser: 주어진 시점(now)에 유효한 사용자 구독(ACTIVE) 조회
 * - findTopByUser_IdOrderByStartAtDesc: 사용자 최근 구독(상태 무관) 조회
 */
@Repository
public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, Long> {
	// 가독성을 위한 JPQL 메서드로 대체
	// limit 1 은 필수다: 유효한 구독이 둘 이상이면 Optional 반환 타입 때문에
	// IncorrectResultSizeDataAccessException 이 터진다(정렬만으로는 한 건으로 좁혀지지 않는다).
	// 무기한 구독(endAt=null)이 있는 사용자가 재구독하면 subscribe() 가 연장 분기를 타지 못해
	// 겹치는 ACTIVE 구독이 실제로 생기며, 그 사용자의 구독 조회가 영구히 500 이 됐다.
	@Query("""
		select s from MembershipSubscription s
		where s.user.id = :userId
		  and s.status = :status
		  and s.startAt <= :now
		  and (s.endAt is null or s.endAt >= :now)
		order by s.startAt desc
		limit 1
	""")
	Optional<MembershipSubscription> findActiveEffectiveByUser(
			@Param("userId") Long userId,
			@Param("status") MembershipSubscriptionStatus status,
			@Param("now") LocalDateTime now
	);
	
	Optional<MembershipSubscription> findTopByUser_IdOrderByStartAtDesc(Long userId); // 최근 구독(상태 무관)
}


