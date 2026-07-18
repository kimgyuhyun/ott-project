package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Anime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 * - findByIdWithoutLock: 락 없이 단건 조회(읽기 전용 트랜잭션용)
 * - existsByTitle: 제목 중복 여부 조회
 * - findByTitleIsNullAndCuratedIsFalse: TMDB 보강 대상(운영자가 손대지 않은 것만) 조회
 */
@Repository // 스프링 컴포넌트 스캔 + 예외 변환
public interface AnimeRepository extends JpaRepository<Anime, Long> { // 통합 Anime JPA 리포지토리

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동일 레코드 동시 수정 방지(쓰기 락)
    @NonNull Optional<Anime> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체(문자열 JPQL 제거)

    /**
     * 락 없는 단건 조회 — 읽기 전용 트랜잭션에서 써야 한다.
     *
     * 왜 findById 와 따로 두는가
     * - findById 는 @Lock(PESSIMISTIC_WRITE) 라 SELECT ... FOR UPDATE 가 나간다.
     *   PostgreSQL 은 read-only 트랜잭션에서 FOR UPDATE 를 거부한다
     *   ("cannot execute SELECT FOR UPDATE in a read-only transaction") → 500 이 된다.
     * - 실제로 @Transactional(readOnly=true) 인 큐레이션 단건 조회가 이것 때문에 죽었다.
     * - 메서드 이름 파생으로는 락을 뗄 수 없어 @Query 로 명시한다. findById 로 되돌리지 말 것.
     */
    @Query("select a from Anime a where a.id = :id")
    Optional<Anime> findByIdWithoutLock(@Param("id") Long id);

    boolean existsByTitle(String title); // 저장 전 유니크 체크

    Optional<Anime> findByTitle(String title); // 제목으로 조회

    /**
     * TMDB 보강 대상 조회.
     * 운영자가 큐레이션한 작품(curated=true)은 제외한다 — 자동 보강이 사람의 판단을 덮어쓰지 않게 한다.
     */
    List<Anime> findByTitleIsNullAndCuratedIsFalse();
}


