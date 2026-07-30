package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 데이터 수정 컨트롤러
 * 개발/테스트 환경에서만 사용
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final MembershipSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    
    /**
     * 환불된 결제에 대해 멤버십 구독을 수동으로 해지
     * 개발/테스트 환경에서만 사용
     */
    @PostMapping("/fix-refunded-subscriptions")
    @Transactional // payment.getUser() 가 지연 로딩이라 OSIV 없이는 트랜잭션 밖에서 못 읽는다
    public ResponseEntity<String> fixRefundedSubscriptions() {
        log.info("환불된 결제의 멤버십 구독 수정 시작");
        
        // 환불된 결제 조회
        List<Payment> refundedPayments = paymentRepository.findByStatus(PaymentStatus.REFUNDED);
        log.info("환불된 결제 수: {}", refundedPayments.size());
        
        final int[] fixedCount = {0};
        LocalDateTime now = LocalDateTime.now();
        
        for (Payment payment : refundedPayments) {
            Long userId = payment.getUser().getId();
            
            // 해당 사용자의 활성 구독 조회
            subscriptionRepository.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
                    .ifPresent(subscription -> {
                        // 구독 해지 처리(상태 + 해지 시각 + 자동갱신 중단)
                        subscription.applyImmediateCancellation(now);
                        subscriptionRepository.save(subscription);
                        
                        log.info("구독 해지 완료 - userId: {}, subscriptionId: {}, paymentId: {}", 
                                userId, subscription.getId(), payment.getId());
                        fixedCount[0]++;
                    });
        }
        
        String message = String.format("수정 완료: %d개 구독 해지 처리", fixedCount[0]);
        log.info(message);
        return ResponseEntity.ok(message);
    }
    
    /**
     * 특정 사용자의 멤버십 구독 상태 조회
     */
    @GetMapping("/user/{userId}/subscription")
    @Transactional(readOnly = true) // 아래 응답 구성이 지연 로딩 연관(플랜)을 읽는다
    public ResponseEntity<Object> getUserSubscription(@PathVariable Long userId) {
        LocalDateTime now = LocalDateTime.now();

        return subscriptionRepository.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
                .map(subscription -> {
                    // 엔티티를 그대로 직렬화하면 지연 로딩 연관까지 끌려나온다. 필요한 값만 옮겨 담는다.
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("subscriptionId", subscription.getId());
                    body.put("status", subscription.getStatus());
                    body.put("planCode", subscription.getMembershipPlan().getCode());
                    body.put("startAt", subscription.getStartAt());
                    body.put("endAt", subscription.getEndAt());
                    body.put("autoRenew", subscription.isAutoRenew());
                    body.put("nextBillingAt", subscription.getNextBillingAt());
                    return ResponseEntity.ok().body((Object) body);
                })
                .orElse(ResponseEntity.ok().body((Object) "활성 구독 없음"));
    }
}
