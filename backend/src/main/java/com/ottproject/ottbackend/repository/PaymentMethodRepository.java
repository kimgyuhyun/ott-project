package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // 빈 등록
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> { // 결제수단 JPA 리포지토리

	List<PaymentMethod> findByUser_IdOrderByIsDefaultDescPriorityAsc(Long userId); // 기본 수단 우선 + 우선순위 정렬

}


