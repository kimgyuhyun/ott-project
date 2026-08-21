package com.ottproject.ottbackend.dto.admin;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자용 구독 상태 응답
 *
 * 큰 흐름
 * - 특정 사용자의 현재 유효 구독을 운영자가 확인할 때 쓴다.
 *
 * 왜 엔티티를 그대로 쓰지 않는가
 * - MembershipSubscription 을 직렬화하면 지연 로딩 연관(플랜)까지 끌려나온다.
 * - 변환을 서비스 트랜잭션 안에서 끝내면 컨트롤러에 트랜잭션을 열 이유가 사라진다.
 *
 * 필드 개요
 * - subscriptionId/status: 구독 식별과 현재 상태
 * - planCode: 연관 엔티티에서 꺼낸 플랜 코드
 * - startAt/endAt: 유효 구간
 * - autoRenew/nextBillingAt: 다음 청구 예정 여부와 시각
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminSubscriptionDto {

    private final Long subscriptionId;
    private final MembershipSubscriptionStatus status;
    private final String planCode;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final boolean autoRenew;
    private final LocalDateTime nextBillingAt;

    /**
     * 엔티티 → DTO 변환. 지연 로딩 연관(플랜)을 읽으므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param subscription 구독 엔티티
     * @return 응답용 DTO
     */
    public static AdminSubscriptionDto from(MembershipSubscription subscription) {
        return AdminSubscriptionDto.builder()
                .subscriptionId(subscription.getId())
                .status(subscription.getStatus())
                .planCode(subscription.getMembershipPlan().getCode())
                .startAt(subscription.getStartAt())
                .endAt(subscription.getEndAt())
                .autoRenew(subscription.isAutoRenew())
                .nextBillingAt(subscription.getNextBillingAt())
                .build();
    }
}
