package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 태그 Repository
 * 
 * 큰 흐름
 * - 태그 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 태그명으로 조회
 * - existsByName: 태그명 중복 여부
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    
    /**
     * 이름으로 태그 조회
     */
    Optional<Tag> findByName(String name);
    
    /**
     * 태그명 중복 여부
     */
    boolean existsByName(String name);
    
    /**
     * 이름 목록으로 태그 조회 (배치 조회)
     */
    java.util.Set<Tag> findByNameIn(java.util.Collection<String> names);
}
