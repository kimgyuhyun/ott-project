package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.entity.AniList;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.ReviewStatus;
import com.ottproject.ottbackend.repository.AniListRepository;
import com.ottproject.ottbackend.repository.ReviewRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest // 스프링 전체 컨텍스트 로드(컨트롤러→서비스→JPA/MyBatis→DB 왕복 흐름 검증)
@AutoConfigureMockMvc(addFilters = false) // 인증 필터 비활성화(HTTP 레이어를 단순화)
@ActiveProfiles("test") // 테스트 프로파일(H2) 사용
@Transactional // 각 테스트 종료 시 롤백 보장(테스트 간 데이터 격리)
class ReviewControllerTest { // 리뷰 컨트롤러 통합 테스트 클래스 시작

    @Autowired private MockMvc mockMvc; // HTTP 요청/응답 모킹 도구 주입
    @Autowired private UserRepository userRepository; // 사용자 JPA 레포지토리(시드/검증)
    @Autowired private AniListRepository aniListRepository; // 애니 JPA 레포지토리(시드)
    @Autowired private ReviewRepository reviewRepository; // 리뷰 JPA 레포지토리(DB 상태 검증)

    private User seedUser(String email) { // 사용자 시드 생성 헬퍼
        return userRepository.save( // JPA 저장 호출
                User.builder() // 빌더 시작
                        .email(email) // 이메일 세팅
                        .password("X") // 패스워드(테스트용)
                        .name("tester") // 이름
                        .build() // 엔티티 빌드
        ); // 저장 후 영속 엔티티 반환
    } // seedUser 끝

    private AniList seedAni() { // 애니 시드 생성 헬퍼
        return aniListRepository.save( // JPA 저장
                AniList.builder() // 빌더 시작
                        .title("Test Title") // 제목
                        .posterUrl("http://poster") // 포스터 URL
                        .totalEpisodes(12) // 총 화수
                        .status(AnimeStatus.ONGOING) // 방영 상태
                        .releaseDate(LocalDate.now()) // 시작일
                        .endDate(null) // 종료일 없음
                        .ageRating("ALL") // 연령 등급
                        .rating(4.2) // 초기 평점
                        .ratingCount(10) // 평점 수
                        .isExclusive(true) // 독점 여부
                        .isNew(false) // 신작 여부
                        .isPopular(false) // 인기 여부
                        .isCompleted(false) // 완결 여부
                        .isSubtitle(true) // 자막 여부
                        .isDub(false) // 더빙 여부
                        .isSimulcast(false) // 동시방영 여부
                        .broadcastDay("MON") // 방영 요일
                        .broadCastTime("20:00") // 방영 시각
                        .season("SPRING") // 시즌
                        .year(2024) // 연도
                        .type("TV") // 타입
                        .duration(24) // 회당 길이(분)
                        .source("ORIGINAL") // 원작 타입
                        .country("KR") // 국가
                        .language("KO") // 언어
                        .isActive(true) // 활성 여부
                        .build() // 엔티티 빌드
        ); // 저장 후 반환
    } // seedAni 끝

    private Long createReviewViaApi(AniList ani, User user, String content, double rating) throws Exception { // 리뷰를 API 로 생성하는 헬퍼(컨트롤러부터 시작 보장)
        String body = String.format("{\"aniId\":%d,\"content\":\"%s\",\"rating\":%.1f}", ani.getId(), content, rating); // CreateReviewRequestDto 요구사항에 맞춰 aniId/내용/평점 JSON 구성
        String idStr = mockMvc.perform( // HTTP 요청 실행
                        post("/api/anime/{aniId}/reviews", ani.getId()) // 엔드포인트(경로변수 aniId 포함)
                                .param("userId", String.valueOf(user.getId())) // 작성자 ID는 쿼리 파라미터로
                                .contentType(MediaType.APPLICATION_JSON) // JSON 타입 지정
                                .content(body) // 바디로 DTO 전달
                )
                .andExpect(status().isOk()) // 생성 성공(본문에 생성 PK 반환)
                .andReturn().getResponse().getContentAsString(); // 본문에서 생성된 PK 스트링 추출
        return Long.parseLong(idStr); // Long 변환 후 반환
        // JSON 바디 전달 → 컨트롤러 호출 → 서비스 로직 실행 → DB 저장 후 생성 PK 응답 수신
        // 반환된 PK로 이후 검증(조회, 수정, 삭제 등) 수행
    } // createReviewViaApi 끝

    @Test
    @DisplayName("리뷰 목록은 API 생성 후 MyBatis 로 조회된다") // CUD=JPA(API 경유), R=MyBatis(목록 XML) 검증
    void listReviews_viaApiSeed_ok() throws Exception { // 목록 테스트
        User user = seedUser("list@a.com"); // 사용자 시드
        AniList ani = seedAni(); // 애니 시드
        Long reviewId = createReviewViaApi(ani, user, "good", 4.5); // 컨트롤러부터 시작해 리뷰 생성
        mockMvc.perform( // 목록 조회(읽기는 MyBatis XML)
                        get("/api/anime/{aniId}/reviews", ani.getId()) // 애니 하위 리뷰 목록
                                .param("currentUserId", String.valueOf(user.getId())) // 좋아요 여부 계산용
                                .param("sort", "latest") // 최신순 정렬
                                .param("page", "0") // 0페이지
                                .param("size", "10") // 페이지 크기 10
                                .accept(MediaType.APPLICATION_JSON) // JSON 응답 기대
                )
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.total").value(1)) // 총 1건
                .andExpect(jsonPath("$.items[0].id").value(reviewId)); // 첫 아이템이 방금 생성된 리뷰
    } // listReviews_viaApiSeed_ok 끝

    @Test
    @DisplayName("리뷰 수정(본인) → 204, DB 반영 확인") // PUT /api/reviews/{id}
    void updateReview_allViaApi_ok() throws Exception { // 수정 플로우
        User user = seedUser("upd@a.com"); // 사용자 시드
        AniList ani = seedAni(); // 애니 시드
        Long reviewId = createReviewViaApi(ani, user, "orig", 3.0); // API 로 리뷰 생성
        String body = "{\"content\":\"edited\",\"rating\":4.0}"; // 수정 JSON 바디
        mockMvc.perform( // 수정 호출
                        put("/api/reviews/{id}", reviewId) // 리뷰 단건 수정 엔드포인트
                                .param("userId", String.valueOf(user.getId())) // 본인 검증(임시)
                                .contentType(MediaType.APPLICATION_JSON) // JSON
                                .content(body) // 수정 바디
                )
                .andExpect(status().isNoContent()); // 204 성공(본문 없음)
        Review updated = reviewRepository.findById(reviewId).orElseThrow(); // DB 에서 실제 값 확인(JPA 단순 조회)
        assertEquals("edited", updated.getContent()); // 내용 갱신 검증
        assertEquals(4.0, updated.getRating()); // 평점 갱신 검증
    } // updateReview_allViaApi_ok 끝

    @Test
    @DisplayName("리뷰 삭제(본인) → 목록에서 제외") // DELETE /api/reviews/{id}
    void deleteReview_soft_ok() throws Exception { // 소프트 삭제 플로우
        User user = seedUser("del@a.com"); // 사용자
        AniList ani = seedAni(); // 애니
        Long reviewId = createReviewViaApi(ani, user, "c", 3.0); // API 생성
        mockMvc.perform( // 삭제 호출
                        delete("/api/reviews/{id}", reviewId) // 단건 삭제(소프트)
                                .param("userId", String.valueOf(user.getId())) // 본인 확인
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 재호출
                        get("/api/anime/{aniId}/reviews", ani.getId())
                                .param("page", "0").param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // ACTIVE 필터로 제외됨
    } // deleteReview_soft_ok 끝

    @Test
    @DisplayName("리뷰 신고 → 목록에서 제외") // POST /api/reviews/{id}/report
    void reportReview_ok() throws Exception { // 신고 플로우
        User user = seedUser("rep@a.com"); // 사용자
        AniList ani = seedAni(); // 애니
        Long reviewId = createReviewViaApi(ani, user, "c", 3.0); // 생성
        mockMvc.perform( // 신고 호출
                        post("/api/reviews/{id}/report", reviewId) // 신고 엔드포인트
                                .param("userId", String.valueOf(user.getId())) // 신고자 ID
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 재호출
                        get("/api/anime/{aniId}/reviews", ani.getId())
                                .param("page", "0").param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // REPORTED 는 목록에서 제외
    } // reportReview_ok 끝

    @Test
    @DisplayName("리뷰 좋아요 토글 → true → false") // POST /api/reviews/{id}/like
    void toggleReviewLike_ok() throws Exception { // 좋아요 on/off 플로우
        User user = seedUser("like@a.com"); // 사용자
        AniList ani = seedAni(); // 애니
        Long reviewId = createReviewViaApi(ani, user, "c", 3.0); // 생성
        mockMvc.perform( // 첫 토글(on)
                        post("/api/reviews/{id}/like", reviewId)
                                .param("userId", String.valueOf(user.getId()))
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk()) // 200
                .andExpect(content().string("true")); // true(on)
        mockMvc.perform( // 두번째 토글(off)
                        post("/api/reviews/{id}/like", reviewId)
                                .param("userId", String.valueOf(user.getId()))
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk()) // 200
                .andExpect(content().string("false")); // false(off)
    } // toggleReviewLike_ok 끝
} // ReviewControllerTest 끝