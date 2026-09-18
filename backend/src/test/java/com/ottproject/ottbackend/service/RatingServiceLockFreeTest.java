package com.ottproject.ottbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.EntityTestFixtures;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.RatingQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.RatingRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 별점 경로가 애니 행에 쓰기 락을 걸지 않는지 고정한다.
 *
 * 왜 이걸 테스트로 박아두는가
 * - 별점은 사용자 경로이고, 애니 행을 잠글 이유가 없다. 여기서 애니는 Rating 의 FK 를 채우는 용도이고
 *   집계(rating/ratingCount)는 조건부 UPDATE 로 따로 나간다.
 * - findById 로 되돌리면 @Lock(PESSIMISTIC_WRITE) 가 다시 붙어, 별점을 남기는 동안 같은 작품의
 *   관리자 수정이 대기한다. 락은 있어도 겉으로 안 보여서 되돌려도 아무 테스트가 깨지지 않았다.
 * - AnimeCurationServiceTest.GetOne.usesLockFreeLookup 과 같은 방식이다(어느 조회 메서드를 쓰는가로 고정).
 */
@ExtendWith(MockitoExtension.class)
class RatingServiceLockFreeTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private RatingQueryMapper ratingQueryMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnimeRepository animeRepository;

    @InjectMocks
    private RatingService ratingService;

    private static final Long USER_ID = 1L;
    private static final Long ANIME_ID = 2L;

    @Test
    @DisplayName("별점 등록은 락 없는 조회를 쓰고, 집계는 대상 컬럼만 UPDATE 한다")
    void doesNotLockAnimeRow() {
        Anime anime = EntityTestFixtures.emptyAnime();
        User user = User.createLocalUser("rater@example.com", "encoded", "별점러");
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(animeRepository.findByIdWithoutLock(ANIME_ID)).willReturn(Optional.of(anime));
        given(ratingRepository.findByUserIdAndAnimeId(USER_ID, ANIME_ID)).willReturn(Optional.empty());

        ratingService.createOrUpdateRating(USER_ID, ANIME_ID, 4.0);

        verify(animeRepository, never()).findById(anyLong()); // FOR UPDATE 가 나가는 쪽
        verify(animeRepository, never()).save(any(Anime.class)); // 엔티티 저장은 version 을 올린다
        verify(animeRepository).updateRatingAggregates(anyLong(), any(), any());
    }
}
