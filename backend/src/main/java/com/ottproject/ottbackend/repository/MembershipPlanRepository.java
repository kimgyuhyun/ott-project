package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MembershipPlanRepository
 *
 * 큰 흐름
 * - 요금제(플랜) 엔티티 CRUD를 담당하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByCode: 플랜 코드로 단건 조회
 */
@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
	Optional<MembershipPlan> findByCode(String code); // 코드로 조회
}


