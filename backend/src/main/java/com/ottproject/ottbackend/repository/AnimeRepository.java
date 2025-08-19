package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Anime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.Optional;

@Repository // 스프링 컴포넌트 스캔 + 예외 변환
public interface AnimeRepository extends JpaRepository<Anime, Long> { // 통합 Anime JPA 리포지토리

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동일 레코드 동시 수정 방지(쓰기 락)
    @NonNull Optional<Anime> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체(문자열 JPQL 제거)

    boolean existsByTitle(String title); // 저장 전 유니크 체크
}


