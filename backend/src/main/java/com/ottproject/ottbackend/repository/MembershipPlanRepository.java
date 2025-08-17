package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 플랜 리포지토리
 */
@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    Optional<MembershipPlan> findByCode(String code); // 코드로 조회
}


