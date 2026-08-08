package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.Payment; // 결제 엔티티 임포트
import com.ottproject.ottbackend.enums.PaymentStatus; // 결제 상태 열거형 임포트
import jakarta.persistence.LockModeType; // 비관적 락 모드
import org.springframework.data.jpa.repository.JpaRepository; // JPA 리포지토리 베이스
import org.springframework.data.jpa.repository.Lock; // 락 선언
import org.springframework.data.jpa.repository.Query; // JPQL 선언
import org.springframework.data.repository.query.Param; // 파라미터 바인딩
import org.springframework.stereotype.Repository; // 스프링 빈 등록

import java.time.LocalDateTime; // 시각 구간 필터 임포트
import java.util.List; // 리스트 임포트
import java.util.Optional; // 단건 조회 결과

/**
 * PaymentRepository
 *
 * 큰 흐름
 * - 결제 엔티티(Payment)에 대한 CUD 전용 JPA 리포지토리.
 * - 조회(READ)는 MyBatis `PaymentQueryMapper`로 수행한다.
 */
@Repository // 빈 등록
public interface PaymentRepository extends JpaRepository<Payment, Long> { // 결제 CUD 전용 JPA 리포지토리 선언
	// 주의: 조회 메서드는 추가하지 않음(조회는 MyBatis에서 수행)
	
	// AdminController에서 필요한 메서드만 추가
	List<Payment> findByStatus(PaymentStatus status); // 상태별 결제 조회

	// 대사 배치용: 특정 상태이면서 생성 시각이 구간 내인 결제 조회(오래된 PENDING 정리)
	List<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);

	// 결제 확정 경로 직렬화용 비관적 쓰기 락 조회.
	//
	// 왜 필요한가
	// - 확정 가드(이미 SUCCEEDED 면 return)가 락 없는 findById 뒤의 분기였다. 조회는 과거를 알려줄 뿐
	//   미래를 예약하지 않으므로, 클라 확정과 웹훅이 동시에 도착하면 둘 다 "내가 첫 번째"라는 결론을 낸다.
	//   결제 1건에 구독 2건이 생기고 아웃박스도 2건 쌓였다(subscriptions 에는 막아줄 제약이 없다).
	//   판정 근거를 "내가 본 상태"에서 "내가 잠근 상태"로 바꾼다(ARCHITECTURE 6절).
	//
	// 왜 NOWAIT 이 아닌가 — MembershipSubscriptionRepository.findByIdForUpdate 와 정반대의 선택이다
	// - 두 경로 모두 락 구간 안에 외부 호출은 없다(PG 재검증은 호출자가 트랜잭션 밖에서 끝내고 들어온다, 9절).
	//   그러니 "락 안에서 PG 를 부르느냐"가 갈림길이 아니다. 갈림길은 기다려서 보게 될 상태가
	//   내 작업을 어떻게 끝내주느냐다.
	// - 여기서는 기다렸다가 앞선 확정을 보면 "이미 SUCCEEDED" 가드에 걸려 조용히 물러난다. 그게 바로
	//   원하는 결말이므로 대기가 곧 수렴 수단이다. 즉시 실패시키면 클라 확정이 500 을 받고 웹훅은 재전송을 반복한다.
	// - 구독 쪽은 기다려도 retryCount 가드에 걸려 메시지가 폐기될 뿐이라 기다림에 얻을 것이 없어 NOWAIT 이다.
	// - 이 락 구간에는 외부 호출이 없으므로 대기 시간은 짧은 DB 문장 몇 개로 한정된다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}

