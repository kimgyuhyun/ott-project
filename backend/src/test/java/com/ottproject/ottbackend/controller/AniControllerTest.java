package com.ottproject.ottbackend.controller; // 패키지 선언

import com.ottproject.ottbackend.entity.AniList;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.repository.AniListRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // 스프링 애플리케이션 전체 컨텍스트 로드(컨트롤러/서비스/리포지토리/MyBatis 포함)
@AutoConfigureMockMvc(addFilters = false) // MockMvc 주입 + 보안 필터 비활성화(인증 없이 호출)
@ActiveProfiles("test") // 테스트 프로파일(H2 등) 사용
@Transactional // 각 테스트 종료 시 롤백(데이터 격리)
class AniControllerIT { // AniController 통합 테스트 클래스

    @Autowired // 스프링이 MockMvc 빈을 주입(실제 MVC 파이프라인 호출)
    private MockMvc mockMvc; // HTTP 요청/응답 모킹 도구

    @Autowired // 스프링이 레포지토리 빈을 주입(JPA 로 시드 데이터 저장)
    private AniListRepository aniListRepository; // 목록 시드/검증용 JPA 레포지토리

    private AniList seedAni( // 목록 API 가 조회할 애니 시드 데이터 생성
                             String title, // 제목
                             AnimeStatus status, // 방영 상태
                             double rating, // 평점
                             int year, // 연도
                             boolean isPopular // 인기 여부
    ) {
        return aniListRepository.save( // JPA 저장(커밋 전 롤백 예정)
                AniList.builder() // 엔티티 빌더 시작
                        .title(title) // 제목
                        .posterUrl("http://img") // 포스터 URL(필수)
                        .totalEpisodes(12) // 총 화수(필수)
                        .status(status) // 방영 상태
                        .releaseDate(LocalDate.now()) // 방영 시작일
                        .endDate(null) // 종료일(선택)
                        .ageRating("ALL") // 연령 등급(필수)
                        .rating(rating) // 평점(필수)
                        .ratingCount(10) // 평점 수(필수)
                        .isExclusive(false) // 독점 여부
                        .isNew(false) // 신작 여부
                        .isPopular(isPopular) // 인기 여부
                        .isCompleted(status == AnimeStatus.COMPLETED) // 완결 여부
                        .isSubtitle(true) // 자막 여부
                        .isDub(true) // 더빙 여부
                        .isSimulcast(false) // 동시방영 여부
                        .broadcastDay("MON") // 방영 요일
                        .broadCastTime("20:00") // 방영 시각
                        .season("SPRING") // 시즌
                        .year(year) // 연도
                        .type("TV") // 타입
                        .duration(24) // 회당 길이
                        .source("ORIGINAL") // 원작
                        .country("KR") // 국가
                        .language("KO") // 언어
                        .isActive(true) // 활성 상태(목록 WHERE 조건)
                        .build() // 엔티티 생성
        ); // 저장된 엔티티 반환
    }

    @Test // 목록 API 통합 테스트(필터/정렬/페이지)
    void 목록_API_필터_정렬_페이지_OK() throws Exception { // given-when-then 패턴
        seedAni("A", AnimeStatus.COMPLETED, 4.8, 2020, true); // 인기/완결/평점4.8/2020(포함 기대)
        seedAni("B", AnimeStatus.ONGOING, 3.0, 2021, false); // 조건 불일치(제외 기대)

        mockMvc.perform( // 목록 API 호출
                        get("/api/anime") // 엔드포인트
                                .param("status", "COMPLETED") // 상태 필터
                                .param("minRating", "4.0") // 최소 평점
                                .param("year", "2020") // 연도 필터
                                .param("sort", "popular") // 정렬 키
                                .param("page", "0") // 페이지
                                .param("size", "10") // 페이지 크기
                )
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.total").value(1)) // 총 1건
                .andExpect(jsonPath("$.items[0].title").value("A")); // 첫 아이템 제목 검증
    }

    @Test // 상세 API 통합 테스트
    void 상세_API_OK() throws Exception { // 단건 상세 조회 정상 동작 검증
        var saved = seedAni("C", AnimeStatus.COMPLETED, 4.5, 2019, false); // 상세 대상 시드
        mockMvc.perform( // 상세 API 호출
                        get("/api/anime/{aniId}", saved.getId()) // 경로변수 aniId
                )
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.aniId").value(saved.getId())); // 응답의 aniId 일치 검증
    }
}