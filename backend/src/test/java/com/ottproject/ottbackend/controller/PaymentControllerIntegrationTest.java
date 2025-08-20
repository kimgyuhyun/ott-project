package com.ottproject.ottbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.repository.IdempotencyKeyRepository;
import com.ottproject.ottbackend.repository.MembershipPlanRepository;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PaymentController 통합 테스트
 *
 * 전체 흐름(Javadoc):
 * - 목표: 컨트롤러 → 서비스 → JPA(MySQL/H2) & MyBatis → DB 까지 왕복하는 실제 트랜잭션/조회 흐름을 검증합니다.
 * - 시나리오1: 체크아웃 생성 → redirectUrl & paymentId 응답 → Payment(PENDING) 저장/세션ID/멱등키 저장 확인
 * - 시나리오2: 웹훅 성공 수신 → Payment(SUCCEEDED)로 상태 전이 + 멤버십 구독 활성/연장 트리거 → 최신 멤버십 API 로 ACTIVE 검증
 * - 시나리오3: 결제 이력 조회(MyBatis) → SUCCEEDED 건이 최신순으로 노출되는지 검증
 * - 데이터 준비: 테스트 전 사용자/플랜/멱등키/구독/결제 테이블 정리, 필요시 엔티티 생성 헬퍼 사용
 */
@SpringBootTest // 스프링 컨텍스트 전체 로드로 통합 환경 구성
@AutoConfigureMockMvc // MockMvc 자동 설정
@Transactional // 테스트마다 롤백하여 격리
class PaymentControllerIntegrationTest { // 테스트 클래스 정의 시작

    @Autowired MockMvc mvc; // MockMvc 주입(HTTP 호출 모의)
    @Autowired ObjectMapper om; // JSON 직렬화/역직렬화 도우미

    @Autowired UserRepository userRepository; // 사용자 리포지토리
    @Autowired MembershipPlanRepository planRepository; // 플랜 리포지토리
    @Autowired PaymentRepository paymentRepository; // 결제 리포지토리
    @Autowired MembershipSubscriptionRepository subscriptionRepository; // 구독 리포지토리
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository; // 멱등키 리포지토리

    private static final String TEST_EMAIL = "pay_tester@test.com"; // 고정 테스트 사용자 이메일

    private MockHttpSession loginSession() { // 로그인 세션 생성 헬퍼
        MockHttpSession session = new MockHttpSession(); // Mock 세션 인스턴스 생성
        session.setAttribute("userEmail", TEST_EMAIL); // 세션에 이메일 심어 인증 상태 모의
        return session; // 세션 반환
    }

    @BeforeEach // 각 테스트 전에 공용 데이터 초기화
    void setup() { // 사용자/테이블 초기화 로직
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) { // 테스트 사용자 없으면
            User u = User.builder() // User 빌더 시작
                    .email(TEST_EMAIL) // 이메일 설정
                    .name("PayTester") // 이름 설정
                    .enabled(true) // 활성화
                    .emailVerified(true) // 이메일 인증 완료
                    .build(); // User 인스턴스 생성
            userRepository.save(u); // 사용자 저장
        }
        idempotencyKeyRepository.deleteAll(); // 멱등키 테이블 비우기
        subscriptionRepository.deleteAll(); // 구독 테이블 비우기
        planRepository.deleteAll(); // 플랜 테이블 비우기
        paymentRepository.deleteAll(); // 결제 테이블 비우기
    }

    private MembershipPlan savePlan(String code, String name, int price, int months) { // 플랜 저장 헬퍼
        return planRepository.save(MembershipPlan.builder() // 플랜 빌더 및 저장
                .code(code) // 코드 설정
                .name(name) // 이름 설정
                .price(new com.ottproject.ottbackend.entity.Money((long) price, "KRW")) // 월 가격(VO)
                .periodMonths(months) // 기간(월) 설정
                .concurrentStreams(2) // 동시접속 수 설정
                .maxQuality("1080p") // 최대 화질 설정
                .build()); // 저장 후 엔티티 반환
    }

    @Test // 테스트 메서드 마커
    @DisplayName("체크아웃 생성 → redirectUrl, paymentId 반환 및 Payment 저장") // 시나리오 설명
    void checkout_create_returns_redirect_and_saves_payment() throws Exception { // 체크아웃 생성 흐름 검증
        savePlan("PREMIUM", "Premium", 12900, 1); // 테스트 플랜 저장

        String idem = "idem-ck-001"; // 멱등키 정의
        String body = om.writeValueAsString(Map.of( // 요청 바디 JSON 직렬화
                "planCode", "PREMIUM", // 플랜 코드 지정
                "idempotencyKey", idem // 멱등키 전달
        )); // JSON 문자열 완성

        String resJson = mvc.perform(post("/api/payments/checkout") // 체크아웃 API 호출
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 콘텐츠 타입
                        .content(body)) // 요청 바디 설정
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$.redirectUrl").isString()) // redirectUrl 존재 검증
                .andExpect(jsonPath("$.paymentId").isNumber()) // paymentId 존재 검증
                .andReturn().getResponse().getContentAsString(); // 응답 바디 문자열 획득

        Map<?,?> res = om.readValue(resJson, Map.class); // 응답 JSON 파싱
        Long paymentId = ((Number) res.get("paymentId")).longValue(); // paymentId 추출

        Payment payment = paymentRepository.findById(paymentId).orElseThrow(); // Payment 단건 조회
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING); // 상태 PENDING 검증
        assertThat(payment.getProviderSessionId()).isNotBlank(); // 세션ID 저장 검증
        assertThat(idempotencyKeyRepository.findByKeyValue(idem)).isPresent(); // 멱등키 저장 검증
    }

    @Test // 테스트 메서드 마커
    @DisplayName("웹훅 성공 → Payment=SUCCEEDED, 구독 활성/연장, 이력 조회에 반영") // 시나리오 설명
    void webhook_success_updates_payment_and_membership_and_history() throws Exception { // 웹훅 성공 흐름 검증
        savePlan("BASIC", "Basic", 7900, 1); // 테스트 플랜 저장

        // 1) 체크아웃 생성
        String resJson = mvc.perform(post("/api/payments/checkout") // 체크아웃 API 호출
                        .session(loginSession()) // 로그인 세션 부여
                        .contentType(MediaType.APPLICATION_JSON) // JSON 타입 지정
                        .content(om.writeValueAsString(Map.of("planCode", "BASIC")))) // 요청 바디 구성
                .andExpect(status().isOk()) // 200 OK 기대
                .andReturn().getResponse().getContentAsString(); // 응답 바디 추출
        Long paymentId = ((Number) om.readTree(resJson).get("paymentId").numberValue()).longValue(); // paymentId 파싱

        // 2) 웹훅 성공 반영 (raw body + signature header)
        String event = om.writeValueAsString(Map.of( // 웹훅 이벤트 JSON 직렬화
                "eventId", "evt-123", // 이벤트 멱등키
                "providerPaymentId", "imp_0001", // 외부 결제 ID
                "providerSessionId", "imp_session_0001", // 외부 세션 ID
                "status", "SUCCEEDED", // 이벤트 상태
                "amount", 7900, // 금액
                "currency", "KRW", // 통화
                "receiptUrl", "https://receipt/import/imp_0001", // 영수증 URL
                "occurredAt", "2025-01-01T12:00:00" // 발생 시각
        )); // JSON 문자열 완료

        // 테스트용 서명: 개발 프로필에서는 서명 비활성화일 수 있으므로 헤더 없이도 통과하지만,
        // 서명이 활성화된 환경을 가정해 임의의 서명 헤더를 추가합니다(실제 키는 환경변수 사용).
        mvc.perform(post("/api/payments/" + paymentId + "/webhook") // 웹훅 수신 API 호출
                        .contentType(MediaType.APPLICATION_JSON) // JSON 타입 지정
                        .header("X-Iamport-Signature", "test-signature") // 모의 서명 헤더
                        .content(event)) // 이벤트 페이로드 전달
                .andExpect(status().isOk()); // 200 OK 기대

        // 3) Payment 상태 확인
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(); // Payment 단건 조회
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED); // 상태 SUCCEEDED 검증
        assertThat(payment.getPaidAt()).isNotNull(); // 결제 완료 시각 기록 검증
        assertThat(payment.getProviderPaymentId()).isEqualTo("imp_0001"); // 외부 결제 ID 검증

        // 4) 내 멤버십 활성 확인 (최신 상태 API)
        mvc.perform(get("/api/users/me/membership").session(loginSession())) // 내 멤버십 API 호출
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$.status").value("ACTIVE")) // ACTIVE 상태 노출 검증
                .andExpect(jsonPath("$.planCode").value("BASIC")); // 플랜 코드 노출 검증

        // 5) 결제 이력 조회(MyBatis)
        mvc.perform(get("/api/payments/history").session(loginSession())) // 결제 이력 API 호출
                .andExpect(status().isOk()) // 200 OK 기대
                .andExpect(jsonPath("$[0].planCode").value("BASIC")) // 최신 항목 플랜 코드 검증
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED")) // 상태 노출 검증
                .andExpect(jsonPath("$[0].amount").value(7900)); // 금액 노출 검증
    }

    @Test
    @DisplayName("웹훅 실패/취소/환불 전이 → 구독 상태 반영")
    void webhook_failed_canceled_refunded_membership_transitions() throws Exception {
        savePlan("BASIC", "Basic", 7900, 1);
        String resJson = mvc.perform(post("/api/payments/checkout")
                        .session(loginSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("planCode", "BASIC"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long paymentId = ((Number) om.readTree(resJson).get("paymentId").numberValue()).longValue();

        // 1) FAILED → 구독 PAST_DUE
        String failed = om.writeValueAsString(Map.of(
                "eventId", "evt-fail-1",
                "providerPaymentId", "imp_fail_1",
                "providerSessionId", "imp_session_0001",
                "status", "FAILED",
                "amount", 7900,
                "currency", "KRW",
                "occurredAt", "2025-01-02T12:00:00"
        ));
        mvc.perform(post("/api/payments/" + paymentId + "/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Iamport-Signature", "test-signature")
                        .content(failed))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/membership").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAST_DUE"));

        // 2) CANCELED → 말일 해지 예약(autoRenew=false, cancelAtPeriodEnd=true)
        String canceled = om.writeValueAsString(Map.of(
                "eventId", "evt-cancel-1",
                "providerPaymentId", "imp_cancel_1",
                "providerSessionId", "imp_session_0001",
                "status", "CANCELED",
                "amount", 7900,
                "currency", "KRW",
                "occurredAt", "2025-01-03T12:00:00"
        ));
        mvc.perform(post("/api/payments/" + paymentId + "/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Iamport-Signature", "test-signature")
                        .content(canceled))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/membership").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.autoRenew").value(false));

        // 3) REFUNDED → 즉시 해지(CANCELED)
        String refunded = om.writeValueAsString(Map.of(
                "eventId", "evt-refund-1",
                "providerPaymentId", "imp_refund_1",
                "providerSessionId", "imp_session_0001",
                "status", "REFUNDED",
                "amount", 7900,
                "currency", "KRW",
                "occurredAt", "2025-01-04T12:00:00"
        ));
        mvc.perform(post("/api/payments/" + paymentId + "/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Iamport-Signature", "test-signature")
                        .content(refunded))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/membership").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.autoRenew").value(false));
    }
}


