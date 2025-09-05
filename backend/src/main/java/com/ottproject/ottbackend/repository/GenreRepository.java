package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * GenreRepository
 * 
 * 큰 흐름
 * - 장르 엔티티의 기본 CRUD를 제공하는 JPA 리포지토리
 * 
 * 메서드 개요
 * - findByName: 장르명으로 조회
 * - existsByName: 장르명 중복 여부
 */
@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    
    Optional<Genre> findByName(String name); // 장르명으로 조회
    
    boolean existsByName(String name); // 장르명 중복 여부
}
