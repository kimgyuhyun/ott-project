package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PlanChangeType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 사용자 구독 엔티티
 *
 * 큰 흐름
 * - 사용자의 플랜/상태/기간/갱신 정책을 관리한다.
 * - 자동갱신/말일해지/해지확정/다음결제 기준 시점을 추적한다.
 * - 결제 재시도(dunning) 현황을 보관한다.
 *
 * 필드 개요
 * - id/user/membershipPlan: 식별/소유자/플랜
 * - status: 구독 상태(ACTIVE/PAST_DUE/CANCELED 등)
 * - startAt/endAt: 기간
 * - autoRenew/cancelAtPeriodEnd/canceledAt/nextBillingAt: 정책/일정
 * - retryCount/maxRetry/lastRetryAt/lastError*: dunning 상태
 * - nextPlan/planChangeScheduledAt/changeType: 플랜 변경 예약 정보
 */
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_user", columnList = "user_id") // 사용자 인덱스
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipSubscription { // 멤버쉽 구독
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) // user_id 컬럼 매핑
    private User user; // 사용자 id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false) // plan_id 컬럼 매핑
    private MembershipPlan membershipPlan; // 플랜 id

    @Enumerated(EnumType.STRING) // 문자열 ENUM 보관
    @Column(nullable = false)
    private MembershipSubscriptionStatus status; // 상태

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 시작 시각

    @Column(name = "end_at", nullable = true)
    private LocalDateTime endAt; // 종료 시각(null=무기한)

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew; // 자동 갱신 여부

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd; // 말일 해지 예약 여부

    @Column(name = "canceled_at", nullable = true) // 해지 확정 시각 기록
    private LocalDateTime canceledAt; // 해지 확정 시각(상태 CANCELED 전환 시점)

    @Column(name = "next_billing_at", nullable = true)
    private LocalDateTime nextBillingAt; // 다음 결제(갱신) 기준 시점

    // ===== 정기결제 재시도(dunning) 정책 필드 =====
    @Column(name = "retry_count", nullable = false)
    private int retryCount; // 현재까지 재시도 횟수(소프트 디클라인 기준)

    @Column(name = "max_retry", nullable = false)
    private int maxRetry; // 최대 재시도 횟수(기본 3)

    @Column(name = "last_retry_at", nullable = true)
    private LocalDateTime lastRetryAt; // 마지막 재시도 시각

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode; // 마지막 실패 코드(게이트웨이)

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage; // 마지막 실패 메시지(게이트웨이)

    // ===== 플랜 변경 예약 필드 =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_plan_id")
    private MembershipPlan nextPlan; // 다음 결제일부터 적용될 플랜

    @Column(name = "plan_change_scheduled_at")
    private LocalDateTime planChangeScheduledAt; // 플랜 변경 예약일시

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private PlanChangeType changeType; // 변경 유형 (UPGRADE/DOWNGRADE)

    // ===== 정적 팩토리 메서드 =====

    /**
     * 멤버십 구독 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param membershipPlan 멤버십 플랜
     * @param startAt 시작 시각
     * @param endAt 종료 시각
     * @return 생성된 MembershipSubscription 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static MembershipSubscription createSubscription(User user, MembershipPlan membershipPlan, 
                                                           LocalDateTime startAt, LocalDateTime endAt) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (membershipPlan == null) {
            throw new IllegalArgumentException("멤버십 플랜은 필수입니다.");
        }
        if (startAt == null) {
            throw new IllegalArgumentException("시작 시각은 필수입니다.");
        }
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각 이후여야 합니다.");
        }

        // MembershipSubscription 엔티티 생성
        MembershipSubscription subscription = new MembershipSubscription();
        subscription.user = user;
        subscription.membershipPlan = membershipPlan;
        subscription.status = MembershipSubscriptionStatus.ACTIVE;
        subscription.startAt = startAt;
        subscription.endAt = endAt;
        subscription.autoRenew = true; // 기본값
        subscription.cancelAtPeriodEnd = false; // 기본값
        subscription.retryCount = 0; // 기본값
        subscription.maxRetry = 3; // 기본값

        return subscription;
    }

    // ===== 구독 정책 변경(사용자 주도) =====
    //
    // 이 엔티티의 전이에는 Payment 의 markAs* 같은 사전 상태 가드를 두지 않는다.
    // 유효 구독인지는 findActiveEffectiveByUser / findByIdForUpdate 로 읽어오는 쪽에서 이미 걸러지고,
    // 던닝(PAST_DUE→CANCELED)과 관리자 보정은 ACTIVE 가 아닌 행에서 출발하는 것이 정상 경로다.
    // 여기에 "ACTIVE 만 해지 가능" 같은 가드를 넣으면 그 경로들이 예외로 죽는다.

    /**
     * 말일 해지 예약 — 자동갱신을 끊고 만료일까지 혜택을 유지한다.
     * - autoRenew 와 cancelAtPeriodEnd 는 반드시 함께 바뀐다. 하나만 바꾸면
     *   findMyCancelledMembership(auto_renew=false AND cancel_at_period_end=true)이 이 구독을 놓친다.
     */
    public void scheduleCancellationAtPeriodEnd() {
        this.autoRenew = false;
        this.cancelAtPeriodEnd = true;
    }

    /** 정기결제 재개 — 해지 예약을 되돌린다(위와 같은 짝). */
    public void resumeAutoRenewal() {
        this.autoRenew = true;
        this.cancelAtPeriodEnd = false;
    }

    // ===== 청구 주기 =====

    /**
     * 다음 청구(갱신) 기준 시점 지정.
     * - 구독 생성 직후의 최초 앵커 지정과, 재시도 실패 후 스윕 안전망 예약에 쓴다.
     * - 상태와 짝이 아니다: nextBillingAt 은 "다음에 언제 볼지"일 뿐 상태를 뜻하지 않는다.
     */
    public void scheduleNextBillingAt(LocalDateTime at) {
        this.nextBillingAt = at;
    }

    /**
     * 청구 성공 반영 — 기간 연장과 던닝 상태 초기화를 한 번에 한다.
     * - endAt/nextBillingAt/status/retryCount/lastRetryAt/lastError* 는 전부 이 전이에 묶여 있다.
     *   특히 status 를 ACTIVE 로 되돌리면서 retryCount 를 리셋하지 않으면, 다음 실패 때
     *   남아 있던 카운트 때문에 재시도 기회 없이 곧바로 해지된다.
     * - 연장 후 종료일 계산은 플랜의 periodMonths 와 현재 시각이 함께 필요해 호출자가 넘긴다.
     */
    public void renewUntil(LocalDateTime newEndAt, LocalDateTime chargedAt) {
        if (newEndAt == null) {
            throw new IllegalArgumentException("연장 종료 시각은 필수입니다.");
        }
        this.endAt = newEndAt;
        this.nextBillingAt = newEndAt;
        this.status = MembershipSubscriptionStatus.ACTIVE;
        this.retryCount = 0;
        this.lastRetryAt = chargedAt;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    // ===== 던닝(정기결제 재시도) =====

    /**
     * 청구를 시도조차 못 한 채 연체로 둔다(결제수단 없음, 청구주기 앵커 없음).
     * - 시도가 없었으므로 lastRetryAt 과 retryCount 를 건드리지 않는다.
     *   여기서 카운트를 올리면 사용자가 카드를 등록하기도 전에 재시도 횟수가 소진돼 해지된다.
     */
    public void markPastDue() {
        this.status = MembershipSubscriptionStatus.PAST_DUE;
    }

    /**
     * 청구 거절 확정 반영 — 연체 전환과 실패 기록과 재시도 카운트 증가를 한 번에 한다.
     * @param attemptedAt 시도 시각
     * @param errorCode 게이트웨이 오류 코드
     * @param errorMessage 게이트웨이 오류 메시지
     * @return 증가된 재시도 횟수(호출자가 다음 재시도 예약 여부를 판단한다)
     */
    public int recordDeclinedCharge(LocalDateTime attemptedAt, String errorCode, String errorMessage) {
        if (attemptedAt == null) {
            throw new IllegalArgumentException("시도 시각은 필수입니다.");
        }
        this.status = MembershipSubscriptionStatus.PAST_DUE;
        this.lastRetryAt = attemptedAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.retryCount = this.retryCount + 1;
        this.maxRetry = 3; // 정책 고정(3회) — 생성 시 기본값을 매 실패마다 다시 확정한다
        return this.retryCount;
    }

    /**
     * 재시도 소진 해지 — 즉시 해지하면서 말일 해지 예약 플래그도 함께 세운다.
     * - applyImmediateCancellation 과 달리 cancelAtPeriodEnd 를 true 로 둔다. 결제 실패로 끊긴 구독은
     *   "해지된 멤버십" 화면(findMyCancelledMembership)에 노출돼야 사용자가 재개할 수 있다.
     * - 플랜 변경 예약은 함께 지운다(아래 applyImmediateCancellation 과 같은 이유).
     */
    public void cancelAfterDunningExhausted(LocalDateTime canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("해지 시각은 필수입니다.");
        }
        this.status = MembershipSubscriptionStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.autoRenew = false;
        this.cancelAtPeriodEnd = true;
        clearScheduledPlanChange();
    }

    // ===== 외부에서 이미 일어난 일 반영 =====
    // 웹훅과 환불과 관리자 보정이 "이미 확정된 결과"를 뒤늦게 기록하는 자리다.
    // Payment 의 apply* 와 같은 이유로 사전 상태를 요구하지 않는다 — 늦게 도착하거나 순서가 뒤바뀐
    // 이벤트에 예외를 던지면 PG 가 200 을 못 받아 재전송만 반복한다.

    /**
     * 결제 실패 반영 — 연체 전환과 실패 시각을 함께 기록한다.
     * - 재시도 카운트는 올리지 않는다. 던닝 진행은 배치가 판단한다(웹훅은 경고 표시만).
     */
    public void applyPaymentFailure(LocalDateTime failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("실패 시각은 필수입니다.");
        }
        this.status = MembershipSubscriptionStatus.PAST_DUE;
        this.lastRetryAt = failedAt;
    }

    /**
     * 즉시 해지 반영 — 환불 웹훅/환불 처리/관리자 보정이 쓰는 경로.
     * - 상태와 해지 시각과 자동갱신 중단은 반드시 함께 바뀐다.
     * - cancelAtPeriodEnd 는 건드리지 않는다. 환불로 끊긴 구독은 잔여기간 혜택도 사라지므로
     *   "말일까지 유지되는 해지 예약"과 같은 것으로 표시하면 안 된다.
     * - 플랜 변경 예약은 함께 지운다. CANCELED 는 resume 으로 되살릴 수 없는 종착 상태라
     *   (resume 은 ACTIVE + cancelAtPeriodEnd 인 구독만 받는다) 예약을 남겨둘 이유가 없고,
     *   남기면 배치가 끝난 구독의 플랜을 갈아끼우고 안내 메일까지 보낸다.
     */
    public void applyImmediateCancellation(LocalDateTime canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("해지 시각은 필수입니다.");
        }
        this.status = MembershipSubscriptionStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.autoRenew = false;
        clearScheduledPlanChange();
    }

    // ===== 플랜 변경 예약 =====

    /**
     * 플랜 변경 예약 — 대상 플랜과 적용 시점과 변경 유형은 셋이 한 벌이다.
     * - 하나라도 빠지면 배치(findSubscriptionsWithScheduledPlanChanges)가 예약을 못 찾거나
     *   적용 시점 없이 걸려 있는 유령 예약이 남는다.
     */
    public void schedulePlanChange(MembershipPlan nextPlan, LocalDateTime scheduledAt, PlanChangeType changeType) {
        if (nextPlan == null) {
            throw new IllegalArgumentException("변경할 플랜은 필수입니다.");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("플랜 변경 적용 시점은 필수입니다.");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("플랜 변경 유형은 필수입니다.");
        }
        this.nextPlan = nextPlan;
        this.planChangeScheduledAt = scheduledAt;
        this.changeType = changeType;
    }

    /** 플랜 변경 예약 해제 — 세 필드를 함께 지운다(하나라도 남으면 배치가 오작동한다). */
    public void clearScheduledPlanChange() {
        this.nextPlan = null;
        this.planChangeScheduledAt = null;
        this.changeType = null;
    }

    /**
     * 플랜 즉시 교체 — 예약분 적용(배치)과 차액 결제 업그레이드가 함께 쓴다.
     * - 교체와 동시에 예약을 해제한다. 예약을 남기면 배치가 같은 변경을 한 번 더 적용한다.
     */
    public void changePlanTo(MembershipPlan newPlan) {
        if (newPlan == null) {
            throw new IllegalArgumentException("변경할 플랜은 필수입니다."); // plan_id 는 NOT NULL
        }
        this.membershipPlan = newPlan;
        clearScheduledPlanChange();
    }
}


