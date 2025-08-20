package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository // 빈 등록
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> { // 결제수단 JPA 리포지토리

	List<PaymentMethod> findByUser_IdOrderByIsDefaultDescPriorityAsc(Long userId); // 기본 수단 우선 + 우선순위 정렬(전체)

	// 삭제되지 않은(active) 결제수단만 조회
	List<PaymentMethod> findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(Long userId); // 삭제되지 않은(active) 수단만 조회

	// 사용자 소유의 특정 결제수단 단건 조회
	Optional<PaymentMethod> findByIdAndUser_Id(Long id, Long userId); // 사용자 소유 범위로 단건 조회

}


