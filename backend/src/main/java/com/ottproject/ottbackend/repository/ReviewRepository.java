package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.enums.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // 빈 등록
public interface ReviewRepository extends JpaRepository<Review, Long> { // 기본 CUD 제공

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 쓰기 락(동시 수정 방지)
    @Query("select r from Review r where r.id = :id") // JPQL 단건 조회
    // pk 가 :id 인 엔티티 r 한건을 조회
    Optional<Review> findByIdForUpdate(@Param("id") Long id); // :id -> 메서드 인자 바인딩

    // 상태 업데이트(소프트 삭제/복구/신고 처리)
    @Modifying(clearAutomatically = true, flushAutomatically = true) // DML + 영속성 컨텍스트 자동 동기화 옵션 활성화
    @Query("update Review r set r.status = :status where r.id = :id") //  상태 변경(소프트 삭제/복구/신고 등)
    // pk 가 :id 인 리뷰와 status 컬럼을 :status 값으로 갱신
    int updateStatus(@Param("id") Long id, @Param("status")ReviewStatus status); // 변경 행 수

    // 필요 시: 특정 애니의 모든 리뷰 하드 삭제(관리 기능 등)
    @Modifying(clearAutomatically = true, flushAutomatically = true) // + DML 컨텍스트 동기화
    @Query("delete from Review r where r.aniList.id = :aniListId") // 부모 FK 기준 일괄 삭제
    // 해당 애니(ani_list)의 모든 리뷰를 하드 삭제
    int deleteByAniListId(@Param("aniListId") Long aniListId); // 삭제 행 수 반환
}
