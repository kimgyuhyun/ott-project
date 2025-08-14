package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.SkipUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 스킵 사용 로그 리포지토리
 */
@Repository
public interface SkipUsageRepository extends JpaRepository<SkipUsage, Long> { }


