package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.entity.AniList;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.ReviewStatus;
import com.ottproject.ottbackend.mybatis.ReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.AniListRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor // final 필드 주입용 생성자 자동 생성
@Service
@Transactional // 쓰기 메서드 트랜잭션 관리
public class ReviewService {

    // MyBatis 조회 매퍼(목록/상세)
    private final ReviewCommentQueryMapper reviewQueryMapper; // 읽기 전용(목록/상세/카운트)
    // JPA 저장/수정/삭제
    private final ReviewRepository reviewRepository; // 리뷰 CUD
    private final UserRepository userRepository; // 사용자 연관 검증/지정
    private final AniListRepository aniListRepository; // 애니 연관 검증/지정

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public PagedResponse<ReviewResponseDto> list(Long aniId, Long currentUserId, String sort, int page, int size) {
        int limit = size; // LIMIT 계산
        int offset = Math.max(page, 0) * size; /// OFFSET 계산(0 미만 보호)
        List<ReviewResponseDto> items = reviewQueryMapper // 목록 데이터 조회
                .findReviewsByAniId(aniId, currentUserId, sort, limit, offset);
        long total = reviewQueryMapper.countReviewsByAniId(aniId); // 총 개수 조회(페이지네이션)
        return new PagedResponse<>(items,total,page,size); // 표준 페이지 응답
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜젝션
    public ReviewResponseDto getOne(Long reviewId, Long currentUserId) {
        return reviewQueryMapper.findReviewById(reviewId, currentUserId); // 단건 상세 조회
    }

    public Long create(Long userId, Long aniListId, String content, Double rating) {
        User user = userRepository.findById(userId) // 사용자 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        AniList aniList = aniListRepository.findById(aniListId) // 애니 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("aniList not found: " + aniListId));

        Review review = Review.builder() // 리뷰 엔티티 생성
                .user(user) // 연관: 작성자
                .aniList(aniList) // 연관: 대상 애니
                .content(content) // 내용(선택)
                .rating(rating) // 평점(선택)
                .status(ReviewStatus.ACTIVE) // 기본 상태: ACTIVE
                .build();

        return reviewRepository.save(review).getId(); // 저장 후 ID 반환
    }

    public void updateStatus(Long reviewId, ReviewStatus status) {
        int updated = reviewRepository.updateStatus(reviewId, status); // 상태 갱신(DML)
        if (updated == 0) throw new IllegalArgumentException("review not found: " + reviewId); // 없으면 예외
    }

    public void deleteHardByAniList(Long aniListId) {
        reviewRepository.deleteByAniListId(aniListId); // 특정 리뷰 하드 삭제(관리용)
    }
}
