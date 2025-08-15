package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.entity.AnimeList;
import com.ottproject.ottbackend.entity.Comment;
import com.ottproject.ottbackend.entity.Review;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.enums.CommentStatus;
import com.ottproject.ottbackend.enums.ReviewStatus;
import com.ottproject.ottbackend.repository.AnimeListRepository;
import com.ottproject.ottbackend.repository.CommentRepository;
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
@AutoConfigureMockMvc(addFilters = false) // 인증 필터 비활성화
@ActiveProfiles("test") // 테스트 프로파일 사용(H2)
@Transactional // 각 테스트 케이스 종료 후 롤백
class CommentControllerTest { // 댓글/대댓글 컨트롤러 통합 테스트 클래스

    @Autowired private MockMvc mockMvc; // HTTP 호출 도구
    @Autowired private UserRepository userRepository; // 사용자 시드/검증
    @Autowired private AnimeListRepository animeListRepository; // 애니 시드
    @Autowired private ReviewRepository reviewRepository; // 리뷰 시드/검증
    @Autowired private CommentRepository commentRepository; // DB 상태 검증

    private User seedUser(String email) { // 사용자 시드 생성
        return userRepository.save( // JPA 저장
                User.builder().email(email).password("X").name("tester").build() // 엔티티 빌드
        ); // 저장 후 반환
    } // seedUser 끝

    private AnimeList seedAni() { // 애니 시드 생성
        return animeListRepository.save( // JPA 저장
                AnimeList.builder()
                        .title("Test Title").posterUrl("http://poster").totalEpisodes(12)
                        .status(AnimeStatus.ONGOING).releaseDate(LocalDate.now()).endDate(null)
                        .isExclusive(true).isNew(false).isPopular(false).isCompleted(false)
                        .isSubtitle(true).isDub(false).isSimulcast(false)
                        .broadcastDay("MON").broadCastTime("20:00").season("SPRING")
                        .year(2024).type("TV").duration(24).source("ORIGINAL")
                        .country("KR").language("KO").isActive(true).build()
        ); // 저장 후 반환
    } // seedAni 끝

    private Review seedReview(User user, AnimeList ani) { // 리뷰 시드 생성(댓글 상위 리소스)
        return reviewRepository.save( // JPA 저장
                Review.builder()
                        .user(user).animeList(ani).status(ReviewStatus.ACTIVE)
                        .content("seed").rating(3.5).build()
        ); // 저장 후 반환
    } // seedReview 끝

    private Long createCommentViaApi(Review review, User user, String content) throws Exception { // 최상위 댓글을 API 로 생성하는 헬퍼
        String body = String.format("{\"content\":\"%s\"}", content);
        String idStr = mockMvc.perform( // HTTP 실행
                        post("/api/reviews/{reviewId}/comments", review.getId()) // 최상위 댓글 생성 엔드포인트
                                .sessionAttr("userEmail", user.getEmail()) // 세션 사용자
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body) // 내용(JSON 바디)
                )
                .andExpect(status().isOk()) // 200 OK + 생성 PK
                .andReturn().getResponse().getContentAsString(); // 생성 PK 문자열 추출
        return Long.parseLong(idStr); // Long 변환 후 반환
        // userId, content 를 쿼리 파라미터로 전달 → 컨트롤러 호출 → 서비스 로직 실행 → 리포지토리 save(...)로 DB 저장 → 응답 본문에 생성 PK 수신
        // 반환된 PK를 이용해 이후 조회/수정/삭제 등 검증 수행
    } // createCommentViaApi 끝

    private Long createReplyViaApi(Long parentCommentId, Long userId, String content) throws Exception { // 대댓글을 API 로 생성하는 헬퍼
        String body = String.format("{\"content\":\"%s\"}", content);
        String idStr = mockMvc.perform( // HTTP 실행
                        post("/api/comments/{commentId}/replies", parentCommentId) // 대댓글 생성 엔드포인트
                                .sessionAttr("userEmail", "u@a.com") // 세션 사용자(임의 값)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body) // 대댓글 내용(JSON 바디)
                )
                .andExpect(status().isOk()) // 200 OK + 생성 PK
                .andReturn().getResponse().getContentAsString(); // 생성 PK 추출
        return Long.parseLong(idStr); // Long 변환
    } // createReplyViaApi 끝

    @Test
    @DisplayName("댓글 목록(MyBatis) 조회가 H2에서 동작한다") // 목록 XML 동작 검증
    void listComments_ok() throws Exception { // 최상위 댓글 목록 테스트
        User user = seedUser("x@a.com"); // 사용자 시드
        AnimeList ani = seedAni(); // 애니 시드
        Review review = seedReview(user, ani); // 리뷰 시드
        createCommentViaApi(review, user, "c1"); // API로 최상위 댓글 1건 생성
        mockMvc.perform( // 목록 조회
                        get("/api/reviews/{reviewId}/comments", review.getId()) // 엔드포인트
                                .param("page", "0").param("size", "10") // 페이징
                                .accept(MediaType.APPLICATION_JSON) // JSON
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(1)) // 총 1건
                .andExpect(jsonPath("$.items.length()").value(1)); // 아이템 1개
    } // listComments_ok 끝

    @Test
    @DisplayName("댓글 생성 → 상태변경(PATCH) → 목록 제외") // 생성/상태변경/목록 제외 플로우
    void createThenPatchStatus_ok() throws Exception { // 관리/모더레이션 엔드포인트 검증
        User user = seedUser("y@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long commentId = createCommentViaApi(review, user, "hello"); // 최상위 댓글 생성
        mockMvc.perform( // 목록 확인
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page", "0").param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(1)); // 1건
        mockMvc.perform( // 상태변경: DELETED 전환
                        patch("/api/reviews/{reviewId}/comments/{commentId}/status", review.getId(), commentId)
                                .param("status", CommentStatus.DELETED.name())
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 재호출
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page", "0").param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // ACTIVE 필터로 0건
    } // createThenPatchStatus_ok 끝

    @Test
    @DisplayName("리뷰의 모든 댓글 일괄 삭제 후 목록이 비어있다") // DELETE /api/reviews/{reviewId}/comments
    void deleteAllByReview_ok() throws Exception { // 일괄 삭제
        User user = seedUser("z@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        createCommentViaApi(review, user, "seed"); // 댓글 생성
        mockMvc.perform( // 일괄 삭제 호출
                        delete("/api/reviews/{reviewId}/comments", review.getId())
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 확인
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page", "0").param("size", "10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // 0건
    } // deleteAllByReview_ok 끝

    @Test
    @DisplayName("댓글 수정(본인) → 204, 목록에 변경 내용 반영") // PUT /api/comments/{id}
    void updateComment_ok() throws Exception { // 본인 수정 플로우
        User user = seedUser("c-upd@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long commentId = createCommentViaApi(review, user, "orig"); // 댓글 생성
        String body = "{\"content\":\"edited\"}"; // 수정 바디
        mockMvc.perform( // 수정 호출
                        put("/api/comments/{id}", commentId)
                                .sessionAttr("userEmail", user.getEmail())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isNoContent()); // 204
        Comment updated = commentRepository.findById(commentId).orElseThrow(); // DB에서 내용 확인
        assertEquals("edited", updated.getContent()); // 내용 검증
    } // updateComment_ok 끝

    @Test
    @DisplayName("댓글 삭제(본인) → 목록에서 제외") // DELETE /api/comments/{id}
    void deleteComment_soft_ok() throws Exception { // 소프트 삭제
        User user = seedUser("c-del@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long commentId = createCommentViaApi(review, user, "seed"); // 댓글 생성
        mockMvc.perform( // 삭제 호출
                        delete("/api/comments/{id}", commentId)
                                .sessionAttr("userEmail", user.getEmail())
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 확인
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page","0").param("size","10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // 제외됨
    } // deleteComment_soft_ok 끝

    @Test
    @DisplayName("댓글 신고 → 목록에서 제외") // POST /api/comments/{id}/report
    void reportComment_ok() throws Exception { // 신고 플로우
        User user = seedUser("c-rep@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long commentId = createCommentViaApi(review, user, "seed"); // 댓글 생성
        mockMvc.perform( // 신고 호출
                        post("/api/comments/{id}/report", commentId)
                                .sessionAttr("userEmail", user.getEmail())
                )
                .andExpect(status().isNoContent()); // 204
        mockMvc.perform( // 목록 확인
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page","0").param("size","10")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.total").value(0)); // 신고된 댓글 제외
    } // reportComment_ok 끝

    @Test
    @DisplayName("댓글 좋아요 토글 → true → false") // POST /api/comments/{id}/like
    void toggleCommentLike_ok() throws Exception { // 좋아요 토글
        User user = seedUser("c-like@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long commentId = createCommentViaApi(review, user, "seed"); // 댓글 생성
        mockMvc.perform( // 첫 토글(on)
                        post("/api/comments/{id}/like", commentId)
                                .sessionAttr("userEmail", user.getEmail())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk()) // 200
                .andExpect(content().string("true")); // on
        mockMvc.perform( // 두번째 토글(off)
                        post("/api/comments/{id}/like", commentId)
                                .sessionAttr("userEmail", user.getEmail())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk()) // 200
                .andExpect(content().string("false")); // off
    } // toggleCommentLike_ok 끝

    @Test
    @DisplayName("대댓글 생성 → 대댓글 목록에서 조회된다") // POST/GET /api/comments/{commentId}/replies
    void replies_create_and_list_ok() throws Exception { // 대댓글 플로우
        User user = seedUser("c-reply@a.com"); // 사용자
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(user, ani); // 리뷰
        Long parentId = createCommentViaApi(review, user, "parent"); // 부모 댓글 생성
        Long replyId = createReplyViaApi(parentId, user.getId(), "child"); // 대댓글 생성
        mockMvc.perform( // 대댓글 목록 호출
                        get("/api/comments/{commentId}/replies", parentId)
                                .sessionAttr("userEmail", user.getEmail())
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.length()").value(1)) // 한 건
                .andExpect(jsonPath("$[0].id").value(replyId)) // 방금 생성된 대댓글
                .andExpect(jsonPath("$[0].parentId").value(parentId)); // 부모 ID 일치
    } // replies_create_and_list_ok 끝

    @Test
    @DisplayName("댓글 정렬 best → 좋아요 많은 댓글이 먼저 온다") // sort=best 분기 검증
    void comment_sort_best_ok() throws Exception { // MyBatis 정렬 분기 테스트
        User u1 = seedUser("s1@a.com"); // 사용자1
        User u2 = seedUser("s2@a.com"); // 사용자2
        AnimeList ani = seedAni(); // 애니
        Review review = seedReview(u1, ani); // 리뷰
        Long c1 = createCommentViaApi(review, u1, "a"); // 댓글1
        Long c2 = createCommentViaApi(review, u1, "b"); // 댓글2
        mockMvc.perform(post("/api/comments/{id}/like", c2).sessionAttr("userEmail", u1.getEmail())); // c2 like by u1
        mockMvc.perform(post("/api/comments/{id}/like", c2).sessionAttr("userEmail", u2.getEmail())); // c2 like by u2
        mockMvc.perform( // best 정렬 목록 호출
                        get("/api/reviews/{reviewId}/comments", review.getId())
                                .param("page","0").param("size","10").param("sort","best")
                )
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.items[0].id").value(c2)); // 좋아요 더 많은 c2가 첫 번째
    } // comment_sort_best_ok 끝
} // CommentControllerTest 끝