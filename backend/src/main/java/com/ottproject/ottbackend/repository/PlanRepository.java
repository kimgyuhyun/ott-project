package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 플랜 리포지토리
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);
}


