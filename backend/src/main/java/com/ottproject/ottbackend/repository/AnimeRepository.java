package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Anime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // 스프링 컴포넌트 스캔 + 예외 변환
public interface AnimeRepository extends JpaRepository<Anime, Long> { // 통합 Anime JPA 리포지토리

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동일 레코드 동시 수정 방지(쓰기 락)
    @Query("select a from Anime a where a.id = :id") // JPQL 단건 조회(트랜잭션 내 호출)
    Optional<Anime> findByIdForUpdate(@Param("id") Long id); // :id ↔ 메서드 인자 매핑

    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 영속성 컨텍스트 동기화
    @Query("update Anime a set a.isActive = :active where a.id = :id") // 활성/비활성 토글
    int updateActive(@Param("id") Long id, @Param("active") boolean active); // 갱신 행 수 반환

    boolean existsByTitle(String title); // 저장 전 유니크 체크
}


