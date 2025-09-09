package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
                        // 구독 해지 처리
                        subscription.setStatus(MembershipSubscriptionStatus.CANCELED);
                        subscription.setAutoRenew(false);
                        subscription.setCanceledAt(now);
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
    public ResponseEntity<Object> getUserSubscription(@PathVariable Long userId) {
        LocalDateTime now = LocalDateTime.now();
        
        return subscriptionRepository.findActiveEffectiveByUser(userId, MembershipSubscriptionStatus.ACTIVE, now)
                .map(subscription -> ResponseEntity.ok().body((Object) subscription))
                .orElse(ResponseEntity.ok().body((Object) "활성 구독 없음"));
    }
}
