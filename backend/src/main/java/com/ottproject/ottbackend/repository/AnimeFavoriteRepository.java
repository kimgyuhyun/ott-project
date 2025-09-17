package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AnimeFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AnimeFavoriteRepository
 *
 * 큰 흐름
 * - 사용자와 작품 간 보고싶다(보관함) 관계를 관리하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByUser_IdAndAnime_Id: 특정 사용자-작품 보고싶다 단건 조회
 * - existsByUser_IdAndAnime_Id: 보고싶다 여부 확인
 * - deleteByUser_IdAndAnime_Id: 보고싶다 해제(멱등)
 * - countByAnime_Id: 작품별 보고싶다 수
 */
@Repository // 스프링 빈 등록
public interface AnimeFavoriteRepository extends JpaRepository<AnimeFavorite, Long> { // 리포지토리 인터페이스
    Optional<AnimeFavorite> findByUser_IdAndAnime_Id(Long userId, Long animeId); // 특정 유저-작품 보고싶다 조회
    boolean existsByUser_IdAndAnime_Id(Long userId, Long animeId); // 보고싶다 여부 확인
    void deleteByUser_IdAndAnime_Id(Long userId, Long animeId); // 보고싶다 삭제(멱등)
    long countByAnime_Id(Long animeId); // 작품별 보고싶다 수(옵션)
    List<AnimeFavorite> findByAnimeId(Long animeId); // 특정 작품을 찜한 사용자 목록
}