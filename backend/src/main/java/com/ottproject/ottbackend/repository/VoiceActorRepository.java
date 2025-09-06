package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.VoiceActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

/**
 * 성우 Repository
 * 
 * 큰 흐름
 * - 성우 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 성우명으로 조회
 * - existsByName: 성우명 중복 여부
 * - findByNameIn: 이름 목록으로 배치 조회 (성능 최적화)
 */
@Repository
public interface VoiceActorRepository extends JpaRepository<VoiceActor, Long> {
    
    /**
     * 이름으로 성우 조회
     */
    Optional<VoiceActor> findByName(String name);
    
    /**
     * 성우명 중복 여부
     */
    boolean existsByName(String name);
    
    /**
     * 이름 목록으로 성우 배치 조회 (N+1 쿼리 방지)
     */
    Set<VoiceActor> findByNameIn(Set<String> names);
}
