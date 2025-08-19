package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.Payment; // 결제 엔티티 임포트
import org.springframework.data.jpa.repository.JpaRepository; // JPA 리포지토리 베이스
import org.springframework.stereotype.Repository; // 스프링 빈 등록

/**
 * PaymentRepository
 *
 * 역할:
 * - 결제 엔티티(Payment)에 대한 CUD 전용 JPA 리포지토리입니다.
 * - 조회(READ)는 전부 MyBatis 매퍼를 통해 수행하며, 본 인터페이스에는 조회용 커스텀 메서드를 정의하지 않습니다.
 * - 기본 제공되는 findById 등 JPA 기본 메서드는 사용하지 않도록 서비스 계층에서 규칙을 준수합니다.
 */
@Repository // 빈 등록
public interface PaymentRepository extends JpaRepository<Payment, Long> { // 결제 CUD 전용 JPA 리포지토리 선언
	// 주의: 조회 메서드를 추가하지 마세요. 조회는 MyBatis(PaymentQueryMapper)에서만 수행합니다.
}

