package com.ottproject.ottbackend.repository; // 리포지토리 패키지 선언

import com.ottproject.ottbackend.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PaymentMethodRepository
 *
 * 큰 흐름
 * - 사용자 저장 결제수단을 관리하는 JPA 리포지토리.
 * - 정렬 규칙: 기본(isDefault desc) → priority asc.
 * - 소프트 삭제(deletedAt) 제외 조회 메서드 제공.
 *
 * 메서드 개요
 * - findByUser_IdOrderByIsDefaultDescPriorityAsc: 사용자 전체 결제수단(기본→우선순위)
 * - findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc: 삭제 제외 목록
 * - findByIdAndUser_Id: 사용자 소유 단건 조회
 */
@Repository // 빈 등록
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> { // 결제수단 JPA 리포지토리

	List<PaymentMethod> findByUser_IdOrderByIsDefaultDescPriorityAsc(Long userId); // 전체(기본→우선순위)

	List<PaymentMethod> findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(Long userId); // 소프트 삭제 제외

	Optional<PaymentMethod> findByIdAndUser_Id(Long id, Long userId); // 사용자 소유 단건

}


