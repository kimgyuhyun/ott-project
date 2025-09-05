package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 태그 리포지토리
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    
    /**
     * 이름으로 태그 찾기
     */
    Optional<Tag> findByName(String name);
}
