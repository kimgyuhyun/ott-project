package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
 * - findLatestByUserAndStatusIn: 주어진 상태들 중 최근 구독 1건 조회(탈퇴 시 즉시 해지 대상)
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

	// 탈퇴 시 정리해야 할 구독 조회(ACTIVE + PAST_DUE).
	// endAt 조건을 넣지 않는다: 만료됐는데도 PAST_DUE 로 남아 던닝 재시도가 도는 구독을 놓치면,
	// 익명화된 계정에 계속 결제가 시도된다.
	// limit 1 은 findActiveEffectiveByUser 와 같은 이유로 필수다(겹치는 ACTIVE 구독이 실제로 존재한다).
	@Query("""
		select s from MembershipSubscription s
		where s.user.id = :userId
		  and s.status in :statuses
		order by s.startAt desc
		limit 1
	""")
	Optional<MembershipSubscription> findLatestByUserAndStatusIn(
			@Param("userId") Long userId,
			@Param("statuses") Collection<MembershipSubscriptionStatus> statuses
	);

	// 재청구 경로 동시 실행 직렬화용 비관적 쓰기 락 조회.
	// retryCount 가드는 커밋 후에만 유효해서, MQ 중복 배달 시 첫 처리 커밋 전에 두 번째가
	// 들어오면 둘 다 가드를 통과해 결제 API 가 두 번 호출된다.
	//
	// NOWAIT 인 이유(lock.timeout = 0)
	// - 경합에서 진 쪽은 기다려도 소득이 없다. 앞선 트랜잭션이 커밋되고 나면 retryCount 가 올라가 있어
	//   prepareRetry 의 스테일 가드에 걸려 그대로 폐기된다. 폐기될 메시지 때문에 컨슈머 스레드를
	//   붙잡아 둘 이유가 없으므로 즉시 실패시킨다. 폐기된 건은 스윕 배치(nextBillingAt)가 다음 주기에 복구한다.
	// - 대기가 정답인 반대 사례는 PaymentRepository.findByIdForUpdate 주석 참고. 기다려서 보게 될 상태가
	//   내 작업을 정상 종료시키느냐(대기), 어차피 폐기시키느냐(NOWAIT)로 갈린다.
	// - 이 락 구간 안에 외부 호출은 없다. 예전에는 구독 행을 잠근 채 PG 를 호출해 락 보유 시간이 PG 응답
	//   시간에 묶였지만, 지금은 retryBilling 이 PG 호출을 2단계로 떼어내 트랜잭션·락 밖에서 끝낸다.
	//   여기에 외부 호출을 다시 들여놓지 말 것(ARCHITECTURE 9절).
	// - PostgreSQL 은 문장 수준에서 NOWAIT/SKIP LOCKED 만 지원한다. 임의의 대기 시간(예: 10초)을 주면
	//   Hibernate 방언이 그냥 FOR UPDATE 로 떨어뜨려 조용히 무시되므로, 여기서 쓸 수 있는 값은 0 뿐이다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
	@Query("select s from MembershipSubscription s where s.id = :id")
	Optional<MembershipSubscription> findByIdForUpdate(@Param("id") Long id);
}


