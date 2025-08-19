package com.ottproject.ottbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.UserRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MembershipController 통합 테스트
 * - 컨트롤러 → 서비스 → JPA/MyBatis → DB 왕복 시나리오 검증
 * - 플랜 목록/내 상태/구독 신청/해지(멱등키) 흐름 확인
 */
@SpringBootTest // 스프링 부트 애플리케이션 컨텍스트를 로드해 통합 환경 구성
@AutoConfigureMockMvc // MockMvc 자동 구성으로 HTTP 요청을 모의 실행
@Transactional // 각 테스트 후 롤백으로 DB 오염 방지
class MembershipControllerIntegrationTest { // MembershipController 통합 테스트 클래스 정의

    @Autowired MockMvc mvc; // MockMvc 주입: 컨트롤러 엔드포인트 호출용
    @Autowired ObjectMapper om; // ObjectMapper 주입: JSON 직렬화/역직렬화

    @Autowired UserRepository userRepository; // User 리포지토리 주입
    @Autowired MembershipPlanRepository planRepository; // MembershipPlan 리포지토리 주입
    @Autowired MembershipSubscriptionRepository subRepository; // MembershipSubscription 리포지토리 주입
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository; // 멱등키 리포지토리 주입

    private static final String TEST_EMAIL = "member@test.com"; // 테스트 고정 이메일 상수

    private MockHttpSession loginSession() { // 로그인 세션 헬퍼 메서드 정의
        MockHttpSession session = new MockHttpSession(); // MockHttpSession 인스턴스 생성
        session.setAttribute("userEmail", TEST_EMAIL); // 세션에 userEmail 설정해 로그인 상태 모의
        return session; // 구성된 세션 반환
    }

    @BeforeEach // 각 테스트 실행 전 공통 초기화 수행
    void setupUser() { // 사용자/데이터 초기화 메서드
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) { // 테스트 유저가 없으면
            User u = User.builder() // User 빌더 시작
                    .email(TEST_EMAIL) // 이메일 설정
                    .name("Tester") // 이름 설정
                    .enabled(true) // 활성화 플래그 설정
                    .emailVerified(true) // 이메일 인증 완료 플래그 설정
                    .build(); // User 인스턴스 생성
            userRepository.save(u); // 사용자 저장
        }
        idempotencyKeyRepository.deleteAll(); // 멱등키 테이블 초기화
        subRepository.deleteAll(); // 구독 테이블 초기화
        planRepository.deleteAll(); // 플랜 테이블 초기화
    }

    private MembershipPlan savePlan(String code, String name, int price, int months, int streams, String quality) { // 플랜 저장 헬퍼
        return planRepository.save(MembershipPlan.builder() // 플랜 빌더 시작 후 저장
                .code(code) // 코드 설정
                .name(name) // 이름 설정
                .monthlyPrice(price) // 월 가격 설정
                .periodMonths(months) // 기간(월) 설정
                .concurrentStreams(streams) // 동시접속 수 설정
                .maxQuality(quality) // 최대 화질 설정
                .build()); // 플랜 엔티티 저장 및 반환
    }

    @Test // 테스트 메서드 표시
    @DisplayName("플랜 목록 - MyBatis 정렬 및 필드 매핑 검증") // 시나리오 설명 주석
    void plans_list() throws Exception { // 플랜 목록 API 검증 테스트 메서드
        savePlan("BASIC", "Basic", 7900, 1, 1, "720p"); // BASIC 플랜 저장
        savePlan("PREMIUM", "Premium", 12900, 1, 4, "1080p"); // PREMIUM 플랜 저장

        mvc.perform(get("/api/memberships/plans")) // 플랜 목록 GET 호출
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$[0].name").value("Basic")) // 첫 항목 이름 Basic 검증
                .andExpect(jsonPath("$[0].monthlyPrice").value(7900)) // 첫 항목 가격 7900 검증
                .andExpect(jsonPath("$[1].name").value("Premium")) // 둘째 항목 이름 Premium 검증
                .andExpect(jsonPath("$[1].monthlyPrice").value(12900)); // 둘째 항목 가격 12900 검증
    }

    @Test // 테스트 메서드 표시
    @DisplayName("내 멤버십 - 구독 없음 → EXPIRED 응답") // 시나리오 설명 주석
    void myMembership_expired_when_no_subscription() throws Exception { // 구독이 없을 때 EXPIRED 반환 검증
        mvc.perform(get("/api/users/me/membership").session(loginSession())) // 내 멤버십 GET 호출(로그인 세션 포함)
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$.status").value("EXPIRED")) // 상태 EXPIRED 검증
                .andExpect(jsonPath("$.autoRenew").value(false)); // 자동갱신 false 검증
    }

    @Test // 테스트 메서드 표시
    @DisplayName("구독 신청 → 최신 상태 반환(활성)") // 시나리오 설명 주석
    void subscribe_then_status_active() throws Exception { // 구독 신청 후 상태 ACTIVE 반환 검증
        savePlan("PREMIUM", "Premium", 12900, 1, 4, "1080p"); // PREMIUM 플랜 저장

        String body = om.writeValueAsString(Map.of("planCode", "PREMIUM")); // 요청 바디 JSON 생성

        mvc.perform(post("/api/memberships/subscribe") // 구독 신청 POST 호출
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 콘텐츠 타입 지정
                        .content(body)) // 요청 바디 설정
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$.status").value("ACTIVE")) // ACTIVE 상태 검증
                .andExpect(jsonPath("$.planCode").value("PREMIUM")) // 플랜 코드 PREMIUM 검증
                .andExpect(jsonPath("$.autoRenew").value(true)); // 자동갱신 true 검증

        var userId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId(); // 사용자 ID 조회
        assertThat(subRepository.findTopByUser_IdOrderByStartAtDesc(userId)).isPresent(); // 최근 구독 존재 검증
    }

    @Test // 테스트 메서드 표시
    @DisplayName("구독 해지(말일) - 멱등키 적용: 첫 요청만 키 저장, 상태는 CANCELED로 표시") // 시나리오 설명 주석
    void cancel_with_idempotency() throws Exception { // 해지 API 멱등성 및 상태 노출 검증
        MembershipPlan plan = savePlan("PREMIUM", "Premium", 12900, 1, 4, "1080p"); // 플랜 생성/저장
        User u = userRepository.findByEmail(TEST_EMAIL).orElseThrow(); // 사용자 조회
        subRepository.save(MembershipSubscription.builder() // 구독 엔티티 생성/저장
                .user(u) // 대상 사용자 설정
                .membershipPlan(plan) // 플랜 설정
                .status(MembershipSubscriptionStatus.ACTIVE) // 상태 ACTIVE 설정
                .startAt(LocalDateTime.now().minusDays(1)) // 시작 시각 어제로 설정
                .endAt(LocalDateTime.now().plusDays(20)) // 종료 시각 +20일 설정
                .autoRenew(true) // 자동갱신 true 설정
                .cancelAtPeriodEnd(false) // 말일 해지 예약 해제 상태로 설정
                .build()); // 빌더 종료

        String idem = "idem-123"; // 멱등키 값 정의
        String req = om.writeValueAsString(Map.of("idempotencyKey", idem)); // 멱등키 포함 요청 바디 생성

        mvc.perform(post("/api/memberships/cancel") // 해지 POST 호출(1차)
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 콘텐츠 타입 지정
                        .content(req)) // 요청 바디 설정
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$.status").value("CANCELED")) // 상태 CANCELED 노출 검증(말일 해지 예약 표시 정책)
                .andExpect(jsonPath("$.autoRenew").value(false)); // 자동갱신 false 검증

        assertThat(idempotencyKeyRepository.findByKeyValue(idem)).isPresent(); // 멱등키 저장 여부 검증

        mvc.perform(post("/api/memberships/cancel") // 해지 POST 호출(동일 키 재요청)
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 콘텐츠 타입 지정
                        .content(req)) // 동일 바디 재전송
                .andExpect(status().isOk()) // 200 OK 유지
                .andExpect(jsonPath("$.status").value("CANCELED")); // 상태 동일 노출 검증

        assertThat(idempotencyKeyRepository.findAll().stream() // 저장된 멱등키 목록 스트림화
                .filter(k -> idem.equals(k.getKeyValue())).count()) // 같은 키값 개수 카운트
                .isEqualTo(1); // 1개만 존재해야 함(중복 저장 방지 확인)
    }
}


