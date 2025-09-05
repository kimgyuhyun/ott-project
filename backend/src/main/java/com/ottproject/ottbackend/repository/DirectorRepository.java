package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Director;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 감독 Repository
 */
@Repository
public interface DirectorRepository extends JpaRepository<Director, Long> {
    
    /**
     * 이름으로 감독 조회
     */
    Optional<Director> findByName(String name);
    
    /**
     * 영어 이름으로 감독 조회
     */
    Optional<Director> findByNameEn(String nameEn);
    
    /**
     * 일본어 이름으로 감독 조회
     */
    Optional<Director> findByNameJp(String nameJp);
}
