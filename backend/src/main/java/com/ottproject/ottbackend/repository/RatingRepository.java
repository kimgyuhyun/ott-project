package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByUserIdAndAnimeId(Long userId, Long animeId);
    void deleteByUserIdAndAnimeId(Long userId, Long animeId);
}


