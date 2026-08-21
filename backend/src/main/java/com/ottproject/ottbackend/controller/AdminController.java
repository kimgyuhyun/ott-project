package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.service.AdminSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자용 데이터 수정 컨트롤러
 * 개발/테스트 환경에서만 사용
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminSubscriptionService adminSubscriptionService;

    /**
     * 환불된 결제에 대해 멤버십 구독을 수동으로 해지
     * 개발/테스트 환경에서만 사용
     */
    @PostMapping("/fix-refunded-subscriptions")
    public ResponseEntity<String> fixRefundedSubscriptions() {
        log.info("환불된 결제의 멤버십 구독 수정 시작");

        int fixedCount = adminSubscriptionService.cancelSubscriptionsForRefundedPayments();

        String message = String.format("수정 완료: %d개 구독 해지 처리", fixedCount);
        log.info(message);
        return ResponseEntity.ok(message);
    }

    /**
     * 특정 사용자의 멤버십 구독 상태 조회
     */
    @GetMapping("/user/{userId}/subscription")
    public ResponseEntity<Object> getUserSubscription(@PathVariable Long userId) {
        return adminSubscriptionService
                .findActiveSubscription(userId)
                .map(dto -> ResponseEntity.ok((Object) dto))
                .orElseGet(() -> ResponseEntity.ok((Object) "활성 구독 없음"));
    }
}
