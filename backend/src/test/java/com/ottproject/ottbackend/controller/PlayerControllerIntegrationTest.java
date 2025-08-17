package com.ottproject.ottbackend.controller; // 테스트 클래스의 패키지 선언

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.entity.*;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PlayerController 통합 테스트
 * - 컨트롤러 → 서비스 → JPA/MyBatis → DB 왕복 시나리오 검증
 * - 스트림 URL/진행률/스킵/다음 화 기능을 실제 빈/DB로 테스트
 */
@SpringBootTest // 스프링 부트 컨텍스트를 로드하여 통합 테스트로 실행
@AutoConfigureMockMvc // MockMvc 자동 구성으로 컨트롤러 레벨 HTTP 호출 가능
@Transactional // 각 테스트를 트랜잭션으로 감싸 롤백되게 함(테스트간 데이터 고립)
class PlayerControllerIntegrationTest { // PlayerController에 대한 통합 테스트 클래스 시작

    @Autowired MockMvc mvc; // MockMvc: 컨트롤러 엔드포인트 호출용
    @Autowired ObjectMapper om; // ObjectMapper: JSON 직렬화/역직렬화

    @Autowired UserRepository userRepository; // 유저 리포지토리 의존성 주입
    // 통합 구조로 전환됨: AnimeDetailRepository 제거
    @Autowired EpisodeRepository episodeRepository; // 에피소드 리포지토리 의존성 주입
    @Autowired EpisodeSkipMetaRepository episodeSkipMetaRepository; // 스킵 메타 리포지토리 의존성 주입
    @Autowired EpisodeProgressRepository episodeProgressRepository; // 에피소드 진행률 리포지토리 의존성 주입
    @Autowired SkipUsageRepository skipUsageRepository; // 스킵 사용 이력 리포지토리 의존성 주입
    @Autowired
    MembershipSubscriptionRepository membershipSubscriptionRepository; // 구독 리포지토리 의존성 주입

    private static final String TEST_EMAIL = "user@test.com"; // 고정 테스트 이메일(세션 및 조회 공통 사용)

    @BeforeEach // 각 테스트 실행 직전에 공통 준비 수행
    void setUp() { // 보안 링크 서명에 필요한 시크릿 값을 고정 설정
        // secure_link 시크릿(테스트용 고정 값)
        System.setProperty("secure.link.secret", "test_secret");
    }

    /**
     * 테스트 유저 생성
     * @param member 멤버십 여부(true면 ACTIVE 구독 부여)
     */
    private User createUser(boolean member) { // 테스트용 유저를 생성하고, 필요 시 구독까지 부여
        User u = User.builder() // 유저 빌드
                .email(TEST_EMAIL) // 고정 이메일
                .name("Tester") // 이름
                .enabled(true) // 활성화
                .emailVerified(true) // 이메일 인증 완료
                .build(); // 빌더 종료로 User 인스턴스 생성
        u = userRepository.save(u); // 저장
        if (member) { // 멤버십 사용 시
            MembershipSubscription s = MembershipSubscription.builder() // 구독 빌드
                    .user(u) // 대상 유저
                    .membershipPlan( // 플랜(간단 생성)
                            MembershipPlan.builder() // 플랜 빌더
                                    .name("Premium") // 플랜명
                                    .code("PREMIUM") // 코드
                                    .maxQuality("1080p") // 허용 최대 화질
                                    .build() // MembershipPlan 인스턴스 생성
                    )
                    .status(MembershipSubscriptionStatus.ACTIVE) // 활성 구독
                    .startAt(LocalDateTime.now().minusDays(1)) // 시작일
                    .endAt(null) // 종료일 없음
                    .build(); // 구독 생성
            membershipSubscriptionRepository.save(s); // 구독 저장
        }
        return u; // 유저 반환
    }

    /**
     * 테스트 에피소드 생성(간단 AnimeDetail 포함)
     */
    private Episode createEpisode(int epNo) { // 에피소드를 Anime에 직접 연결해 생성
        Episode ep = Episode.builder() // 에피소드 빌드 시작
                .episodeNumber(epNo) // 화수 설정
                .title("EP" + epNo) // 제목 설정
                .thumbnailUrl("https://cdn.example.com/thumb.jpg") // 썸네일 URL 설정
                .videoUrl("https://cdn.example.com/vod/ani/1/ep" + epNo + "/master.m3u8") // 비디오 마스터 m3u8 경로 설정
                .isActive(true) // 활성 플래그 설정
                .isReleased(true) // 공개 플래그 설정
                // 테스트에서는 Anime 시드 없이 에피소드 단독 생성(연관 필요 시 별도 시드 추가)
                .build(); // 빌더 종료로 Episode 인스턴스 생성
        return episodeRepository.save(ep); // 저장/반환
    }

    /**
     * 로그인 세션 구성(userEmail 세션 키 설정)
     */
    private MockHttpSession loginSession() { // 세션에 userEmail을 설정해 로그인 상태를 모의
        MockHttpSession session = new MockHttpSession(); // 모의 세션
        session.setAttribute("userEmail", TEST_EMAIL); // 세션 이메일 주입
        return session; // 반환
    }

    @Test // 단위/통합 테스트 메서드 표시
    @DisplayName("스트림 URL - 1화는 무료(비멤버 720p 서명 URL)") // 시나리오 설명: 1화 무료, 비멤버는 720p 서명 URL 제공
    void streamUrl_freeEpisode_nonMember_720p() throws Exception { // 비멤버가 1화 스트림 URL을 요청했을 때 720p 제한을 검증
        createUser(false); // 비멤버 생성
        Episode ep1 = createEpisode(1); // 1화 생성(무료 범위)

        mvc.perform(get("/api/episodes/{id}/stream-url", ep1.getId()).session(loginSession())) // GET /api/episodes/{id}/stream-url 호출, pathVar로 ep1 ID 바인딩, 로그인 세션 주입
                .andExpect(status().isOk()) // 200 OK 기대(무료 1화)
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("master_720p.m3u8"))) // URL 내 720p 마스터 m3u8 포함 확인
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("e="))) // 만료 파라미터 e= 존재 여부 확인
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("st="))); // 서명 파라미터 st= 존재 여부 확인
    }

    @Test // 테스트 메서드
    @DisplayName("스트림 URL - 4화는 멤버십 필요(비멤버 403, 멤버 1080p)") // 시나리오 설명
    void streamUrl_episode4_requiresMembership() throws Exception { // 4화는 멤버십 필요: 비멤버 403, 멤버 1080p 접근 확인
        createUser(false); // 비멤버
        Episode ep4 = createEpisode(4); // 4화 생성(멤버십 필요)

        // 비멤버: 403
        mvc.perform(get("/api/episodes/{id}/stream-url", ep4.getId()).session(loginSession())) // 비멤버로 서명 URL 요청
                .andExpect(status().isForbidden()); // 403 Forbidden 기대

        // 멤버십 부여 후: 1080p(master.m3u8) 반환
        membershipSubscriptionRepository.deleteAll(); // 기존 구독 초기화
        createUser(true); // 재생성(같은 이메일)로 ACTIVE 구독 삽입

        mvc.perform(get("/api/episodes/{id}/stream-url", ep4.getId()).session(loginSession())) // 멤버십 적용 후 재요청
                .andExpect(status().isOk()) // 200
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("master.m3u8"))) // 1080p 포함 마스터
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("master_720p.m3u8")))); // 720p 한정 아님
    }

    @Test // 테스트 메서드
    @DisplayName("진행률 저장/조회 - upsert 후 단건 조회") // 진행률 upsert 후 단건 조회 검증
    void progress_upsert_and_get() throws Exception { // 진행률 저장 및 다음 화 조회/검증 흐름
        createUser(false); // 비멤버
        Episode ep1 = createEpisode(1); // 에피소드 생성

        String body = om.writeValueAsString(Map.of("positionSec", 123, "durationSec", 1000)); // 요청 바디
        
        // 진행률 저장(upsert) 호출
        mvc.perform(post("/api/episodes/{id}/progress", ep1.getId()) // POST /api/episodes/{id}/progress, 진행률 upsert 요청
                        .session(loginSession()) // 로그인 세션 부여(유저 식별)
                        .contentType(MediaType.APPLICATION_JSON) // Content-Type: application/json
                        .content(body)) // JSON 바디: positionSec/durationSec
                .andExpect(status().isOk()); // 200 OK(성공 저장)
        
        // 같은 상세에 다음 화(ep2) 추가하여 next 동작 대상 준비
        Episode ep2 = episodeRepository.save(Episode.builder() // 다음 화 저장 빌더 시작
                .episodeNumber(2) // 다음 화의 화수 설정
                .title("EP2") // 다음 화 제목 설정
                .thumbnailUrl("https://cdn.example.com/t2.jpg") // 다음 화 썸네일 URL 설정
                .videoUrl("https://cdn.example.com/vod/ani/1/ep2/master.m3u8") // 다음 화 m3u8 경로 설정
                .isActive(true) // 활성 플래그 설정
                .isReleased(true) // 공개 플래그 설정
                // 같은 Anime로 연결하고 싶다면 setAnime(ep1.getAnime()) 등으로 연결
                .build()); // 빌더 종료로 Episode 인스턴스 생성 후 저장
        
        // 다음 화 조회(현재 화 기준)
        mvc.perform(get("/api/episodes/{id}/next", ep1.getId())) // GET /api/episodes/{id}/next(현재 화의 다음 화 조회)
                .andExpect(status().isOk()) // 200 OK
                .andExpect(content().string(String.valueOf(ep2.getId()))); // 본문이 ep2 ID 인지 확인

        // 마지막 화에서는 다음 화 없음
        mvc.perform(get("/api/episodes/{id}/next", ep2.getId())) // GET /api/episodes/{id}/next(마지막 화에 대해 호출)
                .andExpect(status().isNoContent()); // 204 No Content 기대

        assertThat(episodeProgressRepository.findByUser_IdAndEpisode_Id( // DB 저장 확인: 특정 유저-에피소드 진행률 존재 여부
                userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId(), // 테스트 유저 ID 조회
                ep1.getId()) // 에피소드 ID
                .isPresent()) // Optional 존재 여부 확인
                .isTrue(); // true 기대(저장됨)
    }

    @Test // 테스트 메서드
    @DisplayName("진행률 벌크 조회 - 존재하는 항목만 맵에 포함") // 저장된 진행률만 응답 맵에 포함되는지 검증
    void progress_bulk() throws Exception { // 벌크 진행률 조회 케이스
        createUser(false); // 비멤버
        Episode ep1 = createEpisode(1);
        Episode ep2 = createEpisode(2);

        // 하나만 저장
        String body = om.writeValueAsString(Map.of("positionSec", 50, "durationSec", 500)); // ep1만 저장
        mvc.perform(post("/api/episodes/{id}/progress", ep1.getId()) // POST /api/episodes/{id}/progress, ep1 진행률 저장
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 타입 지정
                        .content(body)) // 바디: positionSec/durationSec
                .andExpect(status().isOk()); // 200 OK

        String bulkReq = om.writeValueAsString(Map.of("episodeIds", List.of(ep1.getId(), ep2.getId()))); // ep1, ep2 요청

        String resp = mvc.perform(post("/api/episodes/progress") // POST /api/episodes/progress 벌크 조회
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 타입 지정
                        .content(bulkReq)) // 바디: episodeIds 목록
                .andExpect(status().isOk()) // 200 OK
                .andReturn().getResponse().getContentAsString(); // 응답 본문 문자열 추출

        // ep1 키 존재, ep2 키는 미포함
        assertThat(resp) // 응답 문자열에 대해
                .contains("\"" + ep1.getId() + "\"") // ep1 키가 포함되어 있어야 함
                .doesNotContain("\"" + ep2.getId() + "\""); // ep2 키는 포함되지 않아야 함
    }

    @Test // 테스트 메서드
    @DisplayName("스킵 메타 조회/트래킹 - 메타 기본값, 트래킹은 비로그인 허용") // 스킵 메타 기본 조회와 트래킹 저장 흐름 검증
    void skips_meta_and_track() throws Exception { // 스킵 관련 API 통합 시나리오
        Episode ep1 = createEpisode(1); // 에피소드 생성

        // 메타 없으면 null 필드
        mvc.perform(get("/api/episodes/{id}/skips", ep1.getId())) // GET /api/episodes/{id}/skips, 메타 없음 케이스
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.introStart").doesNotExist()); // introStart 미존재 확인

        // 메타 생성 후 조회
        episodeSkipMetaRepository.save(EpisodeSkipMeta.builder() // 메타 생성/저장
                .episode(ep1) // 대상 에피소드 설정
                .introStart(0) // 오프닝 시작 초(초 단위)
                .introEnd(90) // 오프닝 종료 초
                .outroStart(1200) // 엔딩 시작 초
                .outroEnd(1260) // 엔딩 종료 초
                .build()); // 빌더 종료로 EpisodeSkipMeta 인스턴스 생성 후 저장

        mvc.perform(get("/api/episodes/{id}/skips", ep1.getId())) // 메타 생성 후 재조회
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.introStart").value(0)) // introStart=0 확인
                .andExpect(jsonPath("$.outroEnd").value(1260)); // outroEnd=1260 확인

        // 트래킹(비로그인 허용)
        String track = om.writeValueAsString(Map.of("type", "INTRO", "atSec", 10)); // INTRO 스킵 트래킹
        mvc.perform(post("/api/episodes/{id}/skips/track", ep1.getId()) // POST /api/episodes/{id}/skips/track, 스킵 트래킹 저장
                        .contentType(MediaType.APPLICATION_JSON) // JSON 타입
                        .content(track)) // 바디: type/atSec
                .andExpect(status().isAccepted()); // 202 Accepted

        assertThat(skipUsageRepository.findAll()) // 스킵 사용 이력 전체 조회
                .hasSize(1); // 정확히 1건 저장되었는지
        assertThat(skipUsageRepository.findAll().get(0).getUser()) // 첫 레코드의 유저 필드
                .isNull(); // 비로그인 허용 시 null 이어야 함
    }

    @Test // 테스트 메서드
    @DisplayName("다음 화 조회 - 존재하면 200(ID), 없으면 204") // 다음 화 존재/부재 시 응답 코드 확인
    void nextEpisode() throws Exception { // next API 동작 검증
        Episode ep1 = createEpisode(1); // 현재 화
        // 같은 상세에 다음 화 추가
        Episode ep2 = episodeRepository.save(Episode.builder() // 다음 화 저장
                .episodeNumber(2) // 화수 2
                .title("EP2") // 제목
                .thumbnailUrl("https://cdn.example.com/t2.jpg") // 썸네일
                .videoUrl("https://cdn.example.com/vod/ani/1/ep2/master.m3u8") // m3u8
                .isActive(true) // 활성
                .isReleased(true) // 공개
                // 같은 Anime에 속하게 하려면 setAnime(ep1.getAnime())와 동일하게 구성
                .build()); // 엔티티 생성/저장

        mvc.perform(get("/api/episodes/{id}/next", ep1.getId())) // GET /api/episodes/{id}/next
                .andExpect(status().isOk()) // 200 OK
                .andExpect(content().string(String.valueOf(ep2.getId()))); // 다음 화 ID 반환 확인

        mvc.perform(get("/api/episodes/{id}/next", ep2.getId())) // 마지막 화 기준 next 호출
                .andExpect(status().isNoContent()); // 204 No Content
    }
}


