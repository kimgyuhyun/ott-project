package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.ViewingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 시청 프로필 리포지토리
 *
 * 큰 흐름
 * - 조회는 언제나 "내 계정의 프로필 전체"라 조건이 user_id 하나뿐이다.
 *   그래서 메서드 이름 파생 쿼리로 충분하다.
 */
public interface ViewingProfileRepository extends JpaRepository<ViewingProfile, Long> {

    /** 계정의 프로필을 만든 순서대로 반환한다. 선택 화면의 표시 순서가 매번 같아야 한다. */
    List<ViewingProfile> findByUserIdOrderByIdAsc(Long userId);

    /** 계정의 프로필 개수. 생성 상한 판정에 쓴다. */
    long countByUserId(Long userId);
}
