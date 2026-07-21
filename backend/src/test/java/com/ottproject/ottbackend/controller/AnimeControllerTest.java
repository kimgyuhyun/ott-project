package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AnimeDetailDto;
import com.ottproject.ottbackend.service.AnimeCacheService;
import com.ottproject.ottbackend.service.FavoriteAnimeService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AnimeController.detail 단위 테스트
 *
 * 여기서 고정하는 규칙(캐시 도입 후 상세 조회 계약)
 * - 공용부는 캐시(getDetailPublic)에서 가져오고, isFavorited 는 요청마다 따로 합성한다.
 * - 찜한 유저와 안 한 유저는 값이 다르고, 비로그인은 false 다(userId=null 을 그대로 넘긴다).
 * - 작품이 없으면(공용부 null) 찜 조회 없이 null 을 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
class AnimeControllerTest {

    @Mock private SecurityUtil securityUtil;
    @Mock private AnimeCacheService animeCacheService;
    @Mock private FavoriteAnimeService favoriteAnimeService;
    @Mock private HttpSession session;

    @InjectMocks private AnimeController controller;

    private static final Long ANI_ID = 1L;

    private AnimeDetailDto publicDto() {
        return AnimeDetailDto.builder().aniId(ANI_ID).title("제목").build();
    }

    @Test
    @DisplayName("찜한 로그인 유저는 isFavorited=true 로 합성된다")
    void favoritedUserGetsTrue() {
        given(securityUtil.getCurrentUserIdOrNull(session)).willReturn(10L);
        given(animeCacheService.getDetailPublic(ANI_ID)).willReturn(publicDto());
        given(favoriteAnimeService.isFavorited(ANI_ID, 10L)).willReturn(true);

        AnimeDetailDto result = controller.detail(ANI_ID, session);

        assertThat(result).isNotNull();
        assertThat(result.getIsFavorited()).isTrue();
    }

    @Test
    @DisplayName("찜하지 않은 로그인 유저는 isFavorited=false")
    void notFavoritedUserGetsFalse() {
        given(securityUtil.getCurrentUserIdOrNull(session)).willReturn(20L);
        given(animeCacheService.getDetailPublic(ANI_ID)).willReturn(publicDto());
        given(favoriteAnimeService.isFavorited(ANI_ID, 20L)).willReturn(false);

        AnimeDetailDto result = controller.detail(ANI_ID, session);

        assertThat(result.getIsFavorited()).isFalse();
    }

    @Test
    @DisplayName("비로그인은 userId=null 을 그대로 넘겨 false 가 된다")
    void anonymousGetsFalse() {
        given(securityUtil.getCurrentUserIdOrNull(session)).willReturn(null);
        given(animeCacheService.getDetailPublic(ANI_ID)).willReturn(publicDto());
        given(favoriteAnimeService.isFavorited(ANI_ID, null)).willReturn(false); // 서비스가 null 을 값싼 false 로 처리

        AnimeDetailDto result = controller.detail(ANI_ID, session);

        assertThat(result.getIsFavorited()).isFalse();
        verify(favoriteAnimeService).isFavorited(eq(ANI_ID), eq((Long) null));
    }

    @Test
    @DisplayName("작품이 없으면 찜 조회 없이 null 을 돌려준다")
    void missingAnimeReturnsNull() {
        given(securityUtil.getCurrentUserIdOrNull(session)).willReturn(10L);
        given(animeCacheService.getDetailPublic(ANI_ID)).willReturn(null);

        AnimeDetailDto result = controller.detail(ANI_ID, session);

        assertThat(result).isNull();
        verify(favoriteAnimeService, never()).isFavorited(ANI_ID, 10L);
    }
}
