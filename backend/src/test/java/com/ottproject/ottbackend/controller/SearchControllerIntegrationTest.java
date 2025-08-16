package com.ottproject.ottbackend.controller; // 패키지 선언

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AnimeRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchController 통합 테스트
 * - 엔티티 시드(Anime) → MyBatis 검색 → 서비스/컨트롤러 응답까지 왕복 검증
 * - 자동완성(suggest)과 통합 검색(search) 모두 확인
 */
@SpringBootTest // 스프링 부트 통합 테스트 컨텍스트 로드
@AutoConfigureMockMvc(addFilters = false) // 보안 필터 비활성화로 인증 없이 호출 허용
@ActiveProfiles("test") // H2 등 테스트 프로파일 사용
@Transactional // 각 테스트 종료 시 롤백으로 데이터 격리
class SearchControllerIntegrationTest {

    @Autowired // 스프링이 MockMvc 빈 주입
    private MockMvc mockMvc; // HTTP 호출 도구

    @Autowired // 스프링이 JPA 리포지토리 주입
    private AnimeRepository animeRepository; // 시드 데이터 저장용 리포지토리

    private Anime seedAnime(String title) { // 시드 유틸 메서드 시작
        return animeRepository.save( // 엔티티 저장(JPA)
                Anime.builder() // 빌더 시작
                        .title(title) // 제목 세팅
                        .posterUrl("http://img") // 포스터 URL 세팅
                        .totalEpisodes(12) // 총 화수
                        .status(AnimeStatus.ONGOING) // 방영 상태
                        .releaseDate(LocalDate.now()) // 시작일
                        .endDate(null) // 종료일 없음
                        .ageRating("ALL") // 연령 등급
                        .rating(4.0) // 평점
                        .ratingCount(10) // 평점 수
                        .isExclusive(false) // 독점 아님
                        .isNew(false) // 신작 아님
                        .isPopular(true) // 인기 플래그
                        .isCompleted(false) // 미완결
                        .isSubtitle(true) // 자막 제공
                        .isDub(false) // 더빙 미제공
                        .isSimulcast(false) // 동시방영 아님
                        .broadcastDay("MON") // 방영 요일
                        .broadCastTime("20:00") // 방영 시각
                        .season("SPRING") // 시즌
                        .year(2024) // 연도
                        .type("TV") // 타입
                        .duration(24) // 분 단위 러닝타임
                        .source("ORIGINAL") // 원작 타입
                        .country("KR") // 제작국가 코드
                        .language("KO") // 언어 코드
                        .isActive(true) // 활성 작품
                        .build() // 엔티티 빌드
        ); // 저장된 엔티티 반환
    }

    @Test // 테스트 메서드 표시
    @DisplayName("자동완성: '짱구'로 제목 부분일치 시 10건 이내로 제목만 반환") // 시나리오 설명
    void suggest_titles_ok() throws Exception { // 자동완성 테스트 시작
        seedAnime("짱구는 못말려"); // 키워드에 매칭될 작품 시드
        seedAnime("짱구 극장판"); // 유사 제목 시드

        mockMvc.perform( // HTTP 요청 실행
                        get("/api/search/suggest") // 자동완성 엔드포인트 호출
                                .param("q", "짱구") // 쿼리 파라미터 q=짱구
                                .param("limit", "10") // 최대 10건
                                .accept(MediaType.APPLICATION_JSON) // JSON 응답 기대
                )
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$[0].title").exists()); // 첫 항목에 title 필드 존재
    }

    @Test // 테스트 메서드
    @DisplayName("통합 검색: 키워드/정렬/페이지 적용 시 PagedResponse<AnimeListDto> 반환") // 설명
    void search_list_ok() throws Exception { // 본검색 테스트 시작
        seedAnime("짱구는 못말려"); // 매칭 시드1
        seedAnime("짱구 극장판"); // 매칭 시드2

        mockMvc.perform( // HTTP 요청 실행
                        get("/api/search") // 통합 검색 엔드포인트
                                .param("query", "짱구") // 키워드
                                .param("sort", "popular") // 인기순 정렬
                                .param("page", "0") // 0페이지
                                .param("size", "10") // 페이지 크기 10
                                .accept(MediaType.APPLICATION_JSON) // JSON 응답 기대
                )
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1))) // 1건 이상
                .andExpect(jsonPath("$.items[0].title").exists()); // 첫 아이템에 title 존재
    }
}


