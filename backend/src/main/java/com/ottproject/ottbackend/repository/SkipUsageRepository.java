package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.SkipUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * SkipUsageRepository
 *
 * 큰 흐름
 * - 스킵 사용 로그를 관리하는 JPA 리포지토리.
 */
@Repository
public interface SkipUsageRepository extends JpaRepository<SkipUsage, Long> { }


