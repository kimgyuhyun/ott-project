package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

/**
 * 캐릭터 Repository
 * 
 * 큰 흐름
 * - 캐릭터 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 캐릭터명으로 조회
 * - existsByName: 캐릭터명 중복 여부
 * - findByNameIn: 이름 목록으로 배치 조회 (성능 최적화)
 */
@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
    
    /**
     * 이름으로 캐릭터 조회
     */
    Optional<Character> findByName(String name);
    
    /**
     * 캐릭터명 중복 여부
     */
    boolean existsByName(String name);
    
    /**
     * 이름 목록으로 캐릭터 배치 조회 (N+1 쿼리 방지)
     */
    Set<Character> findByNameIn(Set<String> names);
}
