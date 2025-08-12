package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.entity.*;
import com.ottproject.ottbackend.enums.*;
import com.ottproject.ottbackend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType; // 미디어 타입
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate; // 날짜 타입

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; // get/post/delete 등
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // status/json 검증

@SpringBootTest // 스프링 부트 전체 컨텍스트 로딩(서비스/레포/매퍼/XML 포함)
@AutoConfigureMockMvc(addFilters = false) // 보안 필터 비활성화(인증 없이 테스트)
@ActiveProfiles("test") // application-test.yml(H2) 적용
@Transactional // 각 테스트 후 롤백    
class ReviewControllerTest { // 리뷰 통합 테스트 클래스

    @Autowired private MockMvc mockMvc; // HTTP 요청/응답 테스트용
    @Autowired private UserRepository userRepository; // 사용자 레포(시드/검증)
    @Autowired private AniListRepository aniListRepository; // 애니 레포(시드)
    @Autowired private ReviewRepository reviewRepository; // 리뷰 레포(검증)
    
    private User seedUser(String email) { // 사용자 시드 헬퍼
        User u = User.builder() // 빌더로 생성
                .email(email) // 이메일
                .password("X") // 임시 값
                .name("tester") // 이름
                .build(); // 나머지 빌드는 @Builder.Default
        return userRepository.save(u); // 저장 후 반환
    }

    private AniList sendAni() { // 애니 시드 헬퍼(필수 컬럼 모두 채움)
        AniList a = AniList.builder()
                .title("Test Title")
                .posterUrl("http://poster")
                .totalEpisodes(12)
                .status(AnimeStatus.ONGOING)
                .releaseDate(LocalDate.now())
                .endDate(null)
                .ageRating("ALL")
                .rating(4.2)
                .ratingCount(10)
                .isExclusive(true)
                .isNew(false)
                .isPopular(false)
                .isCompleted(false)
                .isSubtitle(true)
                .isDub(false)
                .isSimulcast(false)
                .broadcastDay("MON")
                .broadCastTime("20:00")
                .season("SPRING")
                .year(2024)
                .type("TV")
                .duration(24)
                .source("ORIGINAL")
                .country("KR")
                .language("KO")
                .isActive(true)
                .build();
        return aniListRepository.save(a); // 저장
    }

    @Test
    @DisplayName("리뷰 목록(MyBatis) 조회가 실제 DB(H2)에서 동작한다")
    void listReviews_fromMyBatis_ok() throws Exception {
        User user = seedUser("a@a.com"); // 사용자 시드
        AniList ani = sendAni(); // 애니 시드

        Review review = Review.builder() // 리뷰 엔티티 생성
                .user(user) // 연관: 작성자
                .aniList(ani) // 연관: 애니
                .content("good") // 내용
                .rating(4.5) // 평점
                .status(ReviewStatus.ACTIVE) // 상태
                .build();
        review = reviewRepository.save(review); // 저장

        Long currentUserId = user.getId(); // 좋아요 여부 계산 파라미터
        mockMvc.perform( // GET 호출
                get("/api/anime/{aniId}/reviews", ani.getId()) // 경로변수 바인딩
                        .param("currentUserId", String.valueOf(currentUserId)) // 쿼리
                        .param("sort", "latest") // 정렬
                        .param("page", "0") // 페이지
                        .param("size", "10") // 크기
                        .accept(MediaType.APPLICATION_JSON) // JSON 기대
        )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(1)) // 총 개수 = 1
                .andExpect(jsonPath("$.items[0].id").value(review.getId())) // 첫 아이템 ID = 저장한 리뷰
                .andExpect(jsonPath("$.items[0].aniId").value(ani.getId())); // aniID 일치
    }


    @Test
    @DisplayName("리뷰 생성(JPA) 후 목록(MyBatis)에서 조회된다")
    void createReview_thenList_ok() throws Exception {
        User user = seedUser("b@a.com"); // 사용자 시드
        AniList ani = sendAni(); // 애니 시드

        mockMvc.perform( // POST 호출
                        post("/api/anime/{aniId}/reviews", ani.getId()) // 경로 애니 ID
                                .param("userId", String.valueOf(user.getId())) // 작성자
                                .param("content", "hello") // 내용
                                .param("rating", "4.0") // 평점
                )
                .andExpect(status().isOk()); // 200(생성 ID 본문)

        mockMvc.perform( // 생성 후 목록 확인
                        get("/api/anime/{aniId}/reviews", ani.getId())
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(1)); // 총 1건
    }

    @Test
    @DisplayName("특정 애니의 리뷰 일괄 삭제(DML) 후 목록이 비어있다")
    void deleteAllReviewsByAni_ok() throws Exception {
        User user = seedUser("c@a.com"); // 사용자
        AniList ani = sendAni(); // 애니
        Review review = Review.builder() // 리뷰 1건 저장
                .user(user).aniList(ani).status(ReviewStatus.ACTIVE)
                .content("X").rating(3.0).build();
        reviewRepository.save(review); // 저장

        mockMvc.perform(delete("/api/anime/{aniId}/reviews", ani.getId())) // 삭제
                .andExpect(status().isNoContent()); // 204

        mockMvc.perform(get("/api/anime/{aniId}/reviews", ani.getId()) // 목록 확인
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // 0건
    }
}