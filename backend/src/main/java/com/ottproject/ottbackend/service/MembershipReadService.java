package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MembershipPlanDto;
import com.ottproject.ottbackend.dto.UserMembershipDto;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.mybatis.MembershipQueryMapper;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 멤버십 읽기 서비스
 * - 플랜 목록(MyBatis)
 * - 내 멤버십 상태(JPA)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipReadService { // 멤버십 읽기
    private final MembershipQueryMapper membershipQueryMapper; // MyBatis 매퍼(플랜 목록)
    private final MembershipSubscriptionRepository membershipSubscriptionRepository; // 구독 JPA

    /**
     * 플랜 목록 조회
     */
    public List<MembershipPlanDto> listPlans() { // 플랜 목록
        return membershipQueryMapper.listPlans(); // 매퍼 호출
    }

    /**
     * 내 멤버십 상태 조회
     * - 활성 구독이 있고 기간 유효하면 상세 정보, 없으면 만료 상태 반환
     */
    public UserMembershipDto getMyMembership(Long userId) { // 내 멤버십 상태
        LocalDateTime now = LocalDateTime.now(); // 기준 시각
        var opt = membershipSubscriptionRepository.findActiveEffectiveByUser( // 짧은 JPQL 메서드
                userId, MembershipSubscriptionStatus.ACTIVE, now
        );
        if (opt.isEmpty()) { // 없음
            var dto = new UserMembershipDto(); // DTO
            dto.status = MembershipSubscriptionStatus.EXPIRED; // 만료
            dto.autoRenew = false; // 자동갱신 없음
            return dto; // 반환
        }
        MembershipSubscription sub = opt.get(); // 구독
        var dto = new UserMembershipDto(); // DTO
        dto.planCode = sub.getMembershipPlan().getCode(); // 코드
        dto.planName = sub.getMembershipPlan().getName(); // 이름
        dto.endAt = sub.getEndAt(); // 만료일
        dto.autoRenew = sub.isAutoRenew(); // 자동갱신
        dto.status = sub.isCancelAtPeriodEnd() ? MembershipSubscriptionStatus.CANCELED : sub.getStatus(); // 말일해지 표기
        return dto; // 반환
    }
}
