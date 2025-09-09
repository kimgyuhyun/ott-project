package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.Payment; // 결제 엔티티 임포트
import com.ottproject.ottbackend.enums.PaymentStatus; // 결제 상태 열거형 임포트
import org.springframework.data.jpa.repository.JpaRepository; // JPA 리포지토리 베이스
import org.springframework.stereotype.Repository; // 스프링 빈 등록

import java.util.List; // 리스트 임포트

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
}

