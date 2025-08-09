package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AniDetailDto;
import com.ottproject.ottbackend.dto.AniListDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.enums.AnimeStatus;
import com.ottproject.ottbackend.service.AniQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import static org.hamcrest.Matchers.is; // JSONPath 매칭을 위한 matcher
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // GET 요청 빌더
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath; // JSONPath 검증기
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // HTTP 상태 검증기

@WebMvcTest(AniController.class) // 웹 레이어만 로드(해당 컨트롤러./컨트롤러 어드바이스/ 컨버터 등), 서비스/레포는 로드 안함
@AutoConfigureMockMvc(addFilters = false) // 시큐리티 필터 비활성화(인증 없이 테스트)
class AniControllerTest { // 컨트롤러 단위(슬라이스) 테스트 클래스

    @Autowired // 스프링이 테스트 컨텍스트에서 MockMvc 객체를 주입
    MockMvc mvc; // MockMvc 주입 -> HTTP 요청/응답을 모의로 수행

    @MockBean // 스프링 컨테이너에 모의 서비스 빈을 등록(컨트롤러가 주입받는 실제 빈을 대체)
    private AniQueryService queryService; // 컨트롤러 의존 서비스(모킹): DB 접근 없이 시나리오 검증 가능

    @Test // 하나의 테스트 케이스로 인식
    void 목록_API_200() throws Exception { // 목록 엔드포인트가 200 과 올바른 페이로드를 반환하는지 검증
        var item= AniListDto.builder() // DTO 빌더로 가짜 응답 아이템 구성
                .aniId(1L) // 식별자
                .title("짱구는 못말려") // 제목
                .posterUrl("img.jpg") // 포스터 URL
                .rating(4.8).ratingCount(22) // 평점/평가 수
                .isDub(true).isSubtitle(true).isExclusive(false) // 배치 플래그
                .isNew(false).isPopular(true).isCompleted(true) // 배치 플래그
                .animeStatus(AnimeStatus.COMPLETED).year(2019).type("TV") // 상태/연도/타입
                .build(); // DTO 인스턴스 생성

        Mockito.when( // 모의 서비스에 대한 스텁 정의: List(...)호출 시
                queryService.list(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())
        ).thenReturn(new PagedResponse<>(List.of(item), 1, 10, 20)); // 페이징 응답 반환

        mvc.perform(get("/api/anime")) // GET /api/anime 요청을 모의 전송
                .andExpect(status().isOk()) // HTTP 200 여부 검증
                .andExpect(jsonPath("$.items[0].title", is("짱구는 못말려"))) // 첫 아이템의 title 값 검증
                .andExpect(jsonPath("$.total", is(1))); // total 개수 검증
    }

    @Test // 두 번째 테스트 케이스
    void 상세_API_200() throws Exception { // 상세 엔드포인트가 200과 올바른 페이로드를 반환하는지 검증
        var detail = AniDetailDto.builder() // 상세 DTO 가짜 데이터
                .aniId(1L).detailId(10L).title("짱구는 못말려")
                .build(); // DTO 생성

        Mockito.when(queryService.detail(1L)) // detail(1L) 호출 시
                .thenReturn(detail); // detail DTO 반환하도록 스텁

        mvc.perform(get("/api/anime/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("짱구는 못말려")));
    }
}