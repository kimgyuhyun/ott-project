package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Anime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * AnimeRepository
 *
 * 큰 흐름
 * - 통합 Anime 엔티티의 기본 CUD를 제공하는 JPA 리포지토리.
 * - 동시 수정 충돌 방지를 위해 단건 조회(findById)에 비관적 락을 적용한다.
 *
 * 메서드 개요
 * - findById: 비관적 쓰기 락으로 단건 조회
 * - existsByTitle: 제목 중복 여부 조회
 */
@Repository // 스프링 컴포넌트 스캔 + 예외 변환
public interface AnimeRepository extends JpaRepository<Anime, Long> { // 통합 Anime JPA 리포지토리

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동일 레코드 동시 수정 방지(쓰기 락)
    @NonNull Optional<Anime> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체(문자열 JPQL 제거)

    boolean existsByTitle(String title); // 저장 전 유니크 체크
    
    Optional<Anime> findByTitle(String title); // 제목으로 조회
    
    List<Anime> findByTitleIsNull(); // 한국어 제목이 없는 애니메이션 조회
}


