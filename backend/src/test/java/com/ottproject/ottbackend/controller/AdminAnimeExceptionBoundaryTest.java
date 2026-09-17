package com.ottproject.ottbackend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.exception.AnimeVersionConflictException;
import com.ottproject.ottbackend.exception.GlobalExceptionHandler;
import com.ottproject.ottbackend.service.AnimeCurationService;
import com.ottproject.ottbackend.service.AnimeEnhancementService;
import com.ottproject.ottbackend.service.SimpleAnimeDataCollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 관리자 동기화 API 의 예외 → 응답 경계 테스트
 *
 * 지키려는 규칙
 * - 응답 바디에 원본 예외 메시지를 싣지 않는다.
 *   이 엔드포인트들은 Jikan(외부 API)과 DB 를 직접 건드리므로, 잡아서 메시지를 붙이면
 *   상류 응답이나 SQL 오류 원문이 관리자 화면까지 그대로 흘러간다.
 * - 예외 → 응답 변환은 전역 처리기가 한다. 그래서 컨트롤러 + 전역 처리기를 함께 태운다.
 *
 * 성공/실패 판정(success=false, 200)은 예외가 아니라 정상 결과이므로 그대로 둔다 — 여기서 보지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class AdminAnimeExceptionBoundaryTest {

    @Mock
    private SimpleAnimeDataCollectorService collectorService;

    @Mock
    private AnimeEnhancementService animeEnhancementService;

    @Mock
    private AnimeCurationService animeCurationService;

    @InjectMocks
    private AdminAnimeController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("단건 동기화 실패 응답에 예외 메시지가 새지 않는다")
    void singleSyncFailureDoesNotLeakExceptionMessage() throws Exception {
        given(collectorService.collectAnime(anyLong()))
                .willThrow(new RuntimeException("데이터베이스 오류 발생: duplicate key value violates \"anime_mal_id_key\""));

        mvc.perform(post("/api/admin/anime/sync/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("anime_mal_id_key"))))
                .andExpect(content().string(not(containsString("duplicate key"))));
    }

    @Test
    @DisplayName("일괄 동기화 실패 응답에 예외 메시지가 새지 않는다")
    void bulkSyncFailureDoesNotLeakExceptionMessage() throws Exception {
        given(collectorService.collectPopularAnime(anyInt()))
                .willThrow(new RuntimeException("배치 수집 실패: 429 Too Many Requests from https://api.jikan.moe/v4"));

        mvc.perform(post("/api/admin/anime/sync-popular?limit=10"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("api.jikan.moe"))))
                .andExpect(content().string(not(containsString("배치 수집 실패"))));
    }

    @Test
    @DisplayName("단건 큐레이션 수정에 version 이 없으면 400 이고 서비스까지 가지 않는다")
    void updateWithoutVersionIsBadRequest() throws Exception {
        mvc.perform(patch("/api/admin/anime/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\"}"))
                .andExpect(status().isBadRequest());

        verify(animeCurationService, never()).update(anyLong(), any());
    }

    /**
     * 서비스 비교와 커밋 사이 경합은 Hibernate 가 커밋 시점에 ObjectOptimisticLockingFailureException 으로 알린다.
     * 처리기에 매핑이 없으면 handleAny 로 떨어져 500 이 되고, 관리자는 새로고침 안내 대신 서버 오류를 본다.
     */
    @Test
    @DisplayName("커밋 시점 버전 충돌은 409 로 응답하고 내부 메시지를 싣지 않는다")
    void commitTimeVersionConflictIsConflict() throws Exception {
        given(animeCurationService.update(anyLong(), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(Anime.class, 1L));

        mvc.perform(patch("/api/admin/anime/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(content().string(not(containsString("com.ottproject"))));
    }

    @Test
    @DisplayName("서비스가 판정한 버전 불일치는 409 로 응답한다")
    void serviceVersionConflictIsConflict() throws Exception {
        given(animeCurationService.update(anyLong(), any())).willThrow(new AnimeVersionConflictException(1L, 0L, 1L));

        mvc.perform(patch("/api/admin/anime/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }
}
