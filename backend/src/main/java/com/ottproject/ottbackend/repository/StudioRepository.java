package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Studio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * StudioRepository
 * 
 * 큰 흐름
 * - 스튜디오 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 스튜디오명으로 조회
 * - existsByName: 스튜디오명 중복 여부
 */
@Repository
public interface StudioRepository extends JpaRepository<Studio, Long> {
    
    Optional<Studio> findByName(String name); // 스튜디오명으로 조회
    
    boolean existsByName(String name); // 스튜디오명 중복 여부
    
    /**
     * 이름 목록으로 스튜디오 조회 (배치 조회)
     */
    java.util.Set<Studio> findByNameIn(java.util.Collection<String> names);
}
