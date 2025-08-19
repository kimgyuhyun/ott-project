package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.util.Optional;

@Repository // 빈 등록
public interface ReviewRepository extends JpaRepository<Review, Long> { // 기본 CUD 제공

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 쓰기 락(동시 수정 방지)
    @NonNull Optional<Review> findById(@NonNull Long id); // 파생 메서드 + @Lock로 대체

    // 파생 삭제: 부모 애니 기준 일괄 삭제
    int deleteByAnime_Id(Long aniId); // deleteBy + 경로식으로 대체
}
