package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 캐릭터 Repository
 */
@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
    
    /**
     * 이름으로 캐릭터 조회
     */
    Optional<Character> findByName(String name);
    
    /**
     * 영어 이름으로 캐릭터 조회
     */
    Optional<Character> findByNameEn(String nameEn);
    
    /**
     * 일본어 이름으로 캐릭터 조회
     */
    Optional<Character> findByNameJp(String nameJp);
}
