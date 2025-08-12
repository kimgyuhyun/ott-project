package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.entity.*;
import com.ottproject.ottbackend.enums.*;
import com.ottproject.ottbackend.repository.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; // get/post/patch/delete
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*; // stat

@SpringBootTest // 전체 컨텍스트 로딩
@AutoConfigureMockMvc(addFilters = false) // 보안 비활성화
@ActiveProfiles("test") // H2 설정
@Transactional // 롤백
class CommentControllerTest {

    @Autowired private MockMvc mockMvc; // HTTP 호출 도구
    @Autowired private UserRepository userRepository; // 사용자 레포
    @Autowired private AniListRepository aniListRepository; // 애니 레포
    @Autowired private ReviewRepository reviewRepository; // 리뷰 레포
    @Autowired private CommentRepository commentRepository; // 댓글 레포

    private User seedUser(String email) { // 사용자 시드
        return userRepository.save(User.builder().email(email).password("X").name("tester").build()); // 저장
    }

    private AniList seedAni() { // 애니 시드(필수 컬럼 모두 채움)
        return aniListRepository.save(AniList.builder()
                .title("Test Title").posterUrl("http://poster").totalEpisodes(12)
                .status(AnimeStatus.ONGOING).releaseDate(LocalDate.now()).endDate(null)
                .isExclusive(true).isNew(false).isPopular(false).isCompleted(false)
                .isSubtitle(true).isDub(false).isSimulcast(false)
                .broadcastDay("MON").broadCastTime("20:00").season("SPRING")
                .year(2024).type("TV").duration(24).source("ORIGINAL")
                .country("KR").language("KO").isActive(true).build());
    }

    private Review seedReview(User user, AniList ani) { // 리뷰 시드
        return reviewRepository.save(Review.builder()
                .user(user).aniList(ani).status(ReviewStatus.ACTIVE)
                .content("seed").rating(3.5).build());
    }

    @Test
    @DisplayName("댓글 목록(MyBatis) 조회가 H2에서 동작한다")
    void listComments_ok() throws Exception {
        User user = seedUser("x@a.com"); // 사용자
        AniList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        commentRepository.save(Comment.builder() // 최상위 댓글 1건
                .user(user).review(review).content("c1").status(CommentStatus.ACTIVE).build());

        mockMvc.perform(get("/api/reviews/{reviewId}/comments", review.getId()) // GET
                        .param("page", "0").param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()) // 200
                        .andExpect(jsonPath("$.total").value(1)) // 총 1건
                        .andExpect(jsonPath("$.items.length()").value(1)); // 아이템 1
    }

    @Test
    @DisplayName("댓글 생성(JPA) 후 목록(MyBatis)에서 조회된다 -> 상태변경(PATCH) 후 목록에서 제외된다")
    void createThenPatchStatus_ok() throws Exception {
        User user = seedUser("y@a.com"); // 사용자
        AniList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰

        mockMvc.perform(post("/api/reviews/{reviewId}/comments", review.getId()) // 생성
                .param("userId", String.valueOf(user.getId()))
                .param("content", "hello"))
                .andExpect(status().isOk()); // 200
        mockMvc.perform(get("/api/reviews/{reviewId}/comments", review.getId()) // 생성 확인
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(1)); // 1건

        Long commentId = commentRepository.findAll().get(0).getId(); // 방금 생성된 댓글 ID

        mockMvc.perform(patch("/api/reviews/{reviewId}/comments/{commentId}/status",
                review.getId(), commentId) // 상태변경
                .param("status", CommentStatus.DELETED.name()))
                .andExpect(status().isNoContent()); // 204

        mockMvc.perform(get("/api/reviews/{reviewId}/comments", review.getId()) // 목록 확인
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // ACTIVE 필터에 걸려 0건
    }
    
    @Test
    @DisplayName("리뷰의 모든 댓글 일괄 삭제 후 목록이 비어있다")
    void deleteAllByReview_ok() throws Exception {
        User user = seedUser("z@a.com");
        AniList ani = seedAni();
        Review review = seedReview(user, ani);
        commentRepository.save(Comment.builder()
                .user(user).review(review).content("seed").status(CommentStatus.ACTIVE).build());
        
        mockMvc.perform(delete("/api/reviews/{reviewId}/comments", review.getId())) // 일괄 삭제
                .andExpect(status().isNoContent()); // 204
        
        mockMvc.perform(get("/api/reviews/{reviewId}/comments", review.getId()) // 목록 확인
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // 0건
    }
}