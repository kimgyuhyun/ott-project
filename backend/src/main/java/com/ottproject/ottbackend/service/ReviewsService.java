package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.entity.AnimeList;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.ReviewLike;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.ReviewStatus;
import com.ottproject.ottbackend.mybatis.ReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.AnimeListRepository;
import com.ottproject.ottbackend.repository.ReviewLikeRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final AnimeListRepository animeListRepository; // 애니 연관 검증/지정
    private final ReviewLikeRepository reviewLikeRepository; // 좋아요 CUD
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
        AnimeList animeList = animeListRepository.findById(aniListId) // 애니 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("animeList not found: " + aniListId));

        Review review = Review.builder() // 리뷰 엔티티 생성
                .user(user) // 연관: 작성자
                .animeList(animeList) // 연관: 대상 애니
                .content(content) // 내용(선택)
                .rating(rating) // 평점(선택)
                .status(ReviewStatus.ACTIVE) // 기본 상태: ACTIVE
                .build();

        return reviewRepository.save(review).getId(); // 저장 후 ID 반환
    }

    public void update(Long reviewId, Long userId, String content, Double rating) { // 본인 리뷰 수정
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId)); // 락 조회
        if (!review.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        if (content != null) review.setContent(content); // 내용 갱신
        if (rating != null) review.setRating(rating); // 평점 갱신
        reviewRepository.save(review); // 저장
    }

    public void deleteSoft(Long reviewId, Long userId) { // 본인 리뷰 소프트 삭제(상태 전환)
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId)); // 락 조회
        if (!review.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        reviewRepository.updateStatus(reviewId, ReviewStatus.DELETED); // 상태 전환
    }

    public void report(Long reviewId, Long userId) { // 리뷰 신고(누구나 가능)
        // 필요 시 사용자 존재만 검증
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: d" + userId));
        reviewRepository.updateStatus(reviewId, ReviewStatus.REPORTED); // 상태 전환
    }

    public Boolean toggleLike(Long reviewId, Long userId) {
        int deleted = reviewLikeRepository.deleteByUserIdAndReviewId(userId, reviewId);
        if (deleted > 0) return false;

        // on 시도
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));
        try {
            reviewLikeRepository.save(ReviewLike.builder().user(user).review(review).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            // 경합으로 이미 on -> 멱등성을 위해 off 로 수렴
            reviewLikeRepository.deleteByUserIdAndReviewId(userId, reviewId);
            return false;
        }
    }


    public void updateStatus(Long reviewId, ReviewStatus status) {
        int updated = reviewRepository.updateStatus(reviewId, status); // 상태 갱신(DML)
        if (updated == 0) throw new IllegalArgumentException("review not found: " + reviewId); // 없으면 예외
    }

    public void deleteHardByAniList(Long aniListId) {
        reviewRepository.deleteByAniListId(aniListId); // 특정 리뷰 하드 삭제(관리용)
    }
}
