package com.ottproject.ottbackend.service;


import com.ottproject.ottbackend.dto.*;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.mybatis.AnimeQueryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat; // 가독성 좋은 검증 API(AssertJ)
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // Mockito 확장 활성화: @Mock/@InjectMocks 초기화 자동 수행
class AnimeQueryServiceTest { //서비스 유닛 테스트(비즈니스 로직 검증)

    @Mock // 테스트 중 실제 구현 대신 사용할 모의 객체 선언
    private AnimeQueryMapper mapper; // DB 접근을 수행하는 MyBatis 매퍼를 모킹하여 DB 의존 제거

    @InjectMocks // 서비스는 @InjectMocks 로 실제 메서드 동작
    private AnimeQueryService service; // 비즈니스 로직을 가진 서비스(테스트 타깃)

    @Test // 케이스1: 목록 조회 시 페이징 응답이 올바른지
    void 목록_조회_페이지응답() {
        var item = AnimeListDto.builder() // 가짜 목록 아이템
                .aniId(1L).title("짱구") // 식별자/제목
                .posterUrl("img.jpg") // 포스터
                .rating(4.5).ratingCount(10) // 평점/평가 수
                .animeStatus(AnimeStatus.COMPLETED).year(2020).type("TV") // 상태/연도/타입
                .isDub(true).isSubtitle(true).isExclusive(false) // 배지 플래그
                .isNew(false).isPopular(true).isCompleted(true) // 배지 플래그
                .build(); // DTO 생성

        when(mapper.findAniList(
                any(),        // NEW status
                anyList(),    // NEW genreIds
                anyInt(),     // NEW genreCount
                anyList(),    // NEW tagIds
                any(),        // NEW minRating
                any(),        // NEW year
                any(),        // NEW isDub
                any(),        // NEW isSubtitle
                any(),        // NEW isExclusive
                any(),        // NEW isCompleted
                any(),        // NEW isNew
                any(),        // NEW isPopular
                anyString(),  // NEW sort
                anyInt(),     // NEW limit
                anyInt()      // NEW offset
        )).thenReturn(List.of(item));

        when(mapper.countAniList(
                any(),      // NEW status
                anyList(),  // NEW genreIds
                anyInt(),   // NEW genreCount
                anyList(),  // NEW tagIds
                any(),      // NEW minRating
                any(),      // NEW year
                any(),      // NEW isDub
                any(),      // NEW isSubtitle
                any(),      // NEW isExclusive
                any(),      // NEW isCompleted
                any(),      // NEW isNew
                any()       // NEW isPopular
        )).thenReturn(1L);

        var res = service.list(
                null, null, null, null,
                null, null, null, null, null, null,
                "id", 0, 20,
                null // NEW tagIds
        );

        assertThat(res.getItems()).hasSize(1);
        assertThat(res.getTotal()).isEqualTo(1); // total 검증
        assertThat(res.getItems().get(0).getTitle()).isEqualTo("짱구"); // 값 검증
    }
    
    @Test // 케이스2 상세 조회 시 연관 목록(에피소드/장르/제작사)이 세팅되는지
    void 상세_조회_연관목록_조립() {
        var base = AnimeDetailDto.builder() // 상세/헤더/기본 정보 스텁
                .aniId(1L).detailId(10L).title("짱구")
                .build(); // DTO 생성
        var episodes = List.of(EpisodeDto.builder() // 에피소드 리스트 스텁
                .id(1L).episodeNumber(1).title("1화").build());
        var genres = List.of(GenreSimpleDto.builder() // 장르 리스트 스텁
                .id(1L).name("일상").color("#fff").build());
        var studios = List.of(StudioSimpleDto.builder() // 제작사 리스트 스텁
                .id(1L).name("신에이동화").country("JP").build());
        
        when(mapper.findAniDetailByAniId(1L)).thenReturn(base); // 상세 본문 스텁
        when(mapper.findEpisodesByAniId(1L)).thenReturn(episodes); // 에피소드 스텁
        when(mapper.findGenresByAniId(1L)).thenReturn(genres); // 장르 스텁
        when(mapper.findStudiosByAniId(1L)).thenReturn(studios); // 제작사 스텁
        
        var dto = service.detail(1L); // 테스트 대상 메메서드 호출
        
        assertThat(dto.getTitle()).isEqualTo("짱구"); // 기본 필드 유저 검증
        assertThat(dto.getEpisodes()).hasSize(1); // 에피소드 조립 검증
        assertThat(dto.getGenres()).hasSize(1); // 장르 조립 검증
        assertThat(dto.getStudios()).hasSize(1); // 제작사 조립 검증
    }
}