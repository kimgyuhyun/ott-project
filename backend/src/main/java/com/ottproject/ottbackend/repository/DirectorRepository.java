package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Director;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 감독 Repository
 * 
 * 큰 흐름
 * - 감독 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 감독명으로 조회
 * - existsByName: 감독명 중복 여부
 */
@Repository
public interface DirectorRepository extends JpaRepository<Director, Long> {
    
    /**
     * 이름으로 감독 조회
     */
    Optional<Director> findByName(String name);
    
    /**
     * 감독명 중복 여부
     */
    boolean existsByName(String name);
    
    /**
     * 이름 목록으로 감독 조회 (배치 조회)
     */
    java.util.Set<Director> findByNameIn(java.util.Collection<String> names);
}
