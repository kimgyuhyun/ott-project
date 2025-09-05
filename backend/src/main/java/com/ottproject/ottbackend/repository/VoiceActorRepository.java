package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.VoiceActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 성우 Repository
 */
@Repository
public interface VoiceActorRepository extends JpaRepository<VoiceActor, Long> {
    
    /**
     * 이름으로 성우 조회
     */
    Optional<VoiceActor> findByName(String name);
    
    /**
     * 영어 이름으로 성우 조회
     */
    Optional<VoiceActor> findByNameEn(String nameEn);
    
    /**
     * 일본어 이름으로 성우 조회
     */
    Optional<VoiceActor> findByNameJp(String nameJp);
}
