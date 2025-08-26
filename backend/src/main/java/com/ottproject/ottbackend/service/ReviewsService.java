package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.ReviewResponseDto;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.ReviewLike;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.ReviewStatus;
import com.ottproject.ottbackend.mybatis.CommunityReviewCommentQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.ReviewLikeRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ReviewsService
 *
 * 큰 흐름
 * - 리뷰 목록/상세 읽기(MyBatis)와 생성/수정/삭제/신고/좋아요 CUD(JPA)를 담당한다.
 *
 * 메서드 개요
 * - list/getOne: 목록/단건 조회
 * - create/update/deleteSoft/report: 리뷰 생성/수정/소프트 삭제/신고
 * - toggleLike: 좋아요 토글(멱등 수렴)
 * - updateStatus/deleteHardByAniList: 상태 갱신/작품 기준 하드 삭제
 */
@RequiredArgsConstructor // final 필드 주입용 생성자 자동 생성
@Service
@Transactional // 쓰기 메서드 트랜잭션 관리
public class ReviewsService {

    // MyBatis 조회 매퍼(목록/상세)
    private final CommunityReviewCommentQueryMapper reviewQueryMapper; // 읽기 전용(목록/상세/카운트)
    // JPA 저장/수정/삭제
    private final ReviewRepository reviewRepository; // 리뷰 CUD
    private final UserRepository userRepository; // 사용자 연관 검증/지정
    private final AnimeRepository animeListRepository; // 애니 연관 검증/지정
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
        Anime animeList = animeListRepository.findById(aniListId) // NEW 애니 조회(필수)
                .orElseThrow(() -> new IllegalArgumentException("animeList not found: " + aniListId));

        Review review = Review.builder() // 리뷰 엔티티 생성
                .user(user) // 연관: 작성자
                .anime(animeList) // NEW 연관: 대상 애니
                .content(content) // 내용(선택)
                .status(ReviewStatus.ACTIVE) // 기본 상태: ACTIVE
                .build();

        return reviewRepository.save(review).getId(); // 저장 후 ID 반환
    }

    public void update(Long reviewId, Long userId, String content, Double rating) { // 본인 리뷰 수정
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId)); // 락 조회
        if (!review.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        if (content != null) review.setContent(content); // 내용 갱신
        reviewRepository.save(review); // 저장
    }

    public void deleteSoft(Long reviewId, Long userId) { // 본인 리뷰 소프트 삭제(상태 전환)
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId)); // 락 조회
        if (!review.getUser().getId().equals(userId)) throw new SecurityException("forbidden"); // 소유자 검증
        review.setStatus(ReviewStatus.DELETED); // 상태 전환
        reviewRepository.save(review); // 저장
    }

    public void report(Long reviewId, Long userId) { // 리뷰 신고(누구나 가능)
        // 필요 시 사용자 존재만 검증
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: d" + userId));
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));
        review.setStatus(ReviewStatus.REPORTED);
        reviewRepository.save(review); // 저장
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


    public void updateStatus(Long reviewId, ReviewStatus status) { // 상태 갱신 공용
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("review not found: " + reviewId));
        review.setStatus(status);
        reviewRepository.save(review);
    }

    public void deleteHardByAniList(Long aniListId) { // 특정 애니의 모든 리뷰 하드 삭제
        reviewRepository.deleteByAnime_Id(aniListId); // 파생 삭제로 대체
    }
}
