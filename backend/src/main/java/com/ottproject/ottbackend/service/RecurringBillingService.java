package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.Payment;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.enums.MembershipSubscriptionStatus;
import com.ottproject.ottbackend.enums.PaymentStatus;
import com.ottproject.ottbackend.mybatis.MembershipSubscriptionQueryMapper;
import com.ottproject.ottbackend.repository.MembershipSubscriptionRepository;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import com.ottproject.ottbackend.repository.PaymentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RecurringBillingService
 *
 * 큰 흐름
 * - 자동갱신 구독에 대해 nextBillingAt 도래 시 저장 결제수단으로 자동 청구를 시도한다(기본→보조 폴백).
 * - 성공 시 구독 기간/nextBillingAt 갱신, 실패 시 PAST_DUE 전환 및 재시도/해지 처리 정책을 수행한다.
 * - 실패 재시도는 RabbitMQ 지연 큐(TTL+DLX)로 건별 예약(1차 3h, 2차 24h)하고,
 *   스윕 배치는 메시지 유실 대비 안전망(nextBillingAt +3일)으로만 동작한다.
 * - 최대 재시도 횟수 소진 시 자동 해지 및 알림 메일을 발송한다.
 *
 * 이중 청구 방어 구조
 * - 모든 청구는 PG 호출 "전에" 결정적 merchant_uid(RebillMerchantUid)로 PENDING Payment 를 먼저 커밋한다.
 *   같은 시도는 같은 uid 이므로 payments 의 유니크 제약이 중복 배달의 두 번째 청구를 삽입 단계에서 떨어뜨린다.
 *   이게 1차 방어선이고, 비관적 락은 그 위에서 상태 전이를 직렬화하는 보조 수단이다.
 * - 그 PENDING 행은 동시에 회수 지점이기도 하다. 응답을 못 받거나 프로세스가 죽어도 행이 남아
 *   PaymentReconciliationService 가 아임포트에 역조회해 확정/실패로 정리한다.
 *
 * 메서드 개요
 * - runRecurringBilling: 정기결제 배치(스케줄)
 * - retryBilling: MQ 지연 메시지 도착 시 해당 구독 한 건만 재청구
 * - reconcileRebillPayment: 대사 배치가 찾아낸 재청구 PENDING 결제 확정
 */
@Slf4j // 로깅 추가
@Service // 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입
public class RecurringBillingService { // 정기결제 스케줄러 서비스
    private final MembershipSubscriptionRepository subscriptionRepository; // 구독 리포지토리
    private final PaymentMethodRepository paymentMethodRepository; // 결제수단 리포지토리
    private final PaymentRepository paymentRepository; // 결제 리포지토리
    private final PaymentGateway paymentGateway; // 결제 게이트웨이 추상화
    private final MembershipNotificationService notificationService; // 알림 메일 서비스
    private final MembershipSubscriptionQueryMapper membershipSubscriptionQueryMapper; // MyBatis 구독 조회 매퍼
    private final BillingRetryPublisher billingRetryPublisher; // 재시도 지연 메시지 발행(RabbitMQ)
    private final BillingAttemptRecorder attemptRecorder; // 청구 시도 선기록(REQUIRES_NEW)
    private final MeterRegistry meterRegistry; // 배치 생존 신호 게이지 등록용

    // 배치가 마지막으로 "끝까지" 완주한 시각(epoch seconds). 경보 BillingBatchStalled 가 이 값만 본다.
    //
    // 시작값을 0 이 아니라 기동 시각으로 두는 이유: 0 이면 배포 직후 time() - 0 이 곧바로 임계를 넘어
    // 매 배포마다 오탐이 뜬다. 기동 시각으로 두면 "방금 뜬 인스턴스는 아직 돌 차례가 아니다"가 되고,
    // cron 주기(6h)가 임계(8h)보다 짧으므로 정상이라면 임계에 닿기 전에 실제 완주가 값을 덮는다.
    //
    // 실패 시 갱신하지 않는 것이 요점이다. 매퍼가 던지든 PG 호출이 터지든 이 값은 그대로 늙어가고,
    // 8시간이 지나면 경보가 뜬다. 로그를 사람이 읽어야만 알 수 있던 것을 지표로 바꾸는 자리다.
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    // 마지막 배치에서 청구에 실패한 구독 수. 위 완주 신호와 반드시 짝으로 봐야 한다.
    //
    // 배치는 이제 구독 한 건이 터져도 나머지를 계속 청구한다(그래야 한 사람의 이상한 데이터가
    // 전체 매출을 막지 않는다). 그런데 그 격리는 완주 신호를 무디게 만든다 — 전원이 실패해도
    // 배치는 완주하므로 BillingBatchStalled 는 조용하다. 그 구멍을 이 값이 메운다.
    private final AtomicLong lastFailedItems = new AtomicLong();

    @PostConstruct
    void registerBatchGauges() {
        lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
        // 이름에 baseUnit 을 붙이지 않고 전체를 직접 적는다. Micrometer 가 baseUnit 을 이름 뒤에
        // 덧붙이는 규칙에 경보 식을 의존시키면, 나중에 단위를 바꿀 때 지표 이름이 조용히 바뀐다.
        Gauge.builder("billing.batch.last.success.timestamp.seconds", lastSuccessEpochSeconds, AtomicLong::get)
                .description("정기결제 배치가 마지막으로 완주한 시각(epoch seconds)")
                .register(meterRegistry);
        Gauge.builder("billing.batch.failed.items", lastFailedItems, AtomicLong::get)
                .description("마지막 정기결제 배치에서 청구에 실패한 구독 수")
                .register(meterRegistry);
    }

    // 단계별 트랜잭션을 프록시에 태우기 위한 자기 참조.
    // 재시도와 스윕 모두 "조회+가드 / PG 호출 / 결과 확정"을 서로 다른 트랜잭션으로 쪼개야 하는데,
    // 같은 빈 안에서 그냥 호출하면 프록시를 안 타서 @Transactional 이 무시된다.
    @Autowired
    @Lazy
    private RecurringBillingService self;

    /**
     * 정기결제 배치
     * - 30분마다 실행(고정 지연). 청구 대상은 next_billing_at 이 정하므로 실행 시각 자체는 정확할 필요가 없다.
     *
     * 왜 cron 이 아닌가
     * - 예전에는 6시간 정각(00/06/12/18시) cron 이었는데 실제로는 한 번도 정각에 돌지 않았다. 실측한 완주 시각이
     *   11:54:43 / 17:44:41 / 23:59:41 / 11:49:55 / 17:43:45 / 23:58:55 / 05:59:52 / 11:52:11 로,
     *   6시간 경계보다 8초에서 16분까지 매번 다르게 일렀다.
     * - cron 은 벽시계의 절대 시각을 목표로 잡고, 그 목표까지의 남은 시간을 단조시계로 센다. 개발 호스트가
     *   절전에 들어가면 게스트 벽시계는 뒤처지는데 카운트다운은 실제 시간만큼 흘러가므로, 벽시계가 아직
     *   목표에 닿기 전에 깨어난다. 절전 길이가 매번 달라서 편차 크기도 매번 다르다. 인스턴스 두 개가
     *   각자의 JVM 으로 함께 이르게 발화한 것이 같은 커널 시계를 공유한다는 증거다.
     * - 앱 안에서는 못 고친다. 벽시계가 틀린 상태에서 "정각에 정확히"는 성립하지 않는다. 그래서 정확한
     *   발화 시각에 기대는 설계를 버렸다. 고정 지연은 목표 시각이 없어 시계 오차의 영향을 받지 않고,
     *   주기를 짧게 두면 한 번 걸러도 30분 뒤에 따라잡는다(6시간 주기에서는 반나절이 밀렸다).
     * - 자주 돌아도 안전한 근거: 성공은 한 달, 거절은 3일(발행 실패 시 1일) 뒤로 next_billing_at 을 밀고,
     *   승인 여부 불명은 상태를 안 바꾸되 결정적 merchant_uid 의 유니크 제약이 재청구를 삽입에서 막는다.
     */
    @Scheduled(
            fixedDelayString = "${billing.sweep-interval-ms:1800000}",
            initialDelayString = "${billing.sweep-initial-delay-ms:120000}") // 기동 직후 몰리지 않게 2분 뒤 첫 실행
    // 다중 인스턴스 중복 청구 방지. 고정 지연이라 인스턴스마다 발화 시점이 어긋나므로 동시 진입은 드물지만,
    // 락이 없으면 겹칠 때 같은 구독을 둘이 청구한다.
    // lockAtMostFor 는 실행 주기보다 짧게 둔다 — 락을 쥔 인스턴스가 죽었을 때 이 값이 주기를 넘으면
    // 그 사이의 실행이 통째로 스킵된다. 대신 이 시간을 넘겨 도는 실행은 다른 인스턴스와 겹칠 수 있다.
    @SchedulerLock(
            name = "RecurringBillingService_runRecurringBilling",
            lockAtMostFor = "PT25M",
            lockAtLeastFor = "PT1M")
    // @Transactional 을 걸지 않는다. 배치 전체를 한 트랜잭션으로 묶으면 구독을 순회하며 PG 를
    // 호출하는 내내 커넥션 하나를 잡고 있고(응답이 느리면 그만큼 길어진다), 한 건이 DB 오류를 내면
    // 트랜잭션이 통째로 죽어 뒤 구독은 손도 못 댄다. 청구/연장의 원자성은 배치 단위가 아니라
    // 구독 단위로 필요한 것이므로 아래 3단계에서 건별로 잡는다 — 재시도 경로와 같은 모양이다.
    public void runRecurringBilling() { // 배치 진입점
        LocalDateTime now = LocalDateTime.now(); // 현재 시각
        log.info("정기결제 배치 시작 - {}", now);

        // 1. 플랜 변경 예약된 구독 처리
        processScheduledPlanChanges(now);

        // 2. MyBatis mapper를 사용하여 대상 구독만 효율적으로 조회
        List<MembershipSubscription> targetSubscriptions =
                membershipSubscriptionQueryMapper.findSubscriptionsForBilling(
                        List.of(
                                MembershipSubscriptionStatus.ACTIVE.name(),
                                MembershipSubscriptionStatus.PAST_DUE.name()),
                        now);

        log.info("처리 대상 구독 수: {}", targetSubscriptions.size());

        int failed = 0; // 이번 주기에 청구가 깨진 구독 수
        for (MembershipSubscription due : targetSubscriptions) { // 구독 순회
            try {
                billSubscription(due.getId(), now); // 청구 시도 + 성공/실패 처리(공통 로직)
            } catch (Exception e) {
                // 한 건의 실패로 나머지 구독의 청구를 막지 않는다. 대신 반드시 세어서 내보낸다 —
                // 여기서 로그만 남기고 넘어가면 배치는 완주하고 경보는 조용해진다(그게 감시를 없애는 길이다).
                failed++;
                log.error("구독 청구 실패 - subscriptionId: {}", due.getId(), e);
            }
        }
        lastFailedItems.set(failed);

        log.info("정기결제 배치 완료 - 처리된 구독: {}, 실패: {}", targetSubscriptions.size(), failed);

        // 완주 신호. 여기까지 왔다는 것은 배치가 순회를 끝까지 돌았다는 뜻이다(건별 실패는 위에서 센다).
        // 대상 조회나 플랜 변경 처리가 던지면 이 줄에 닿지 못하고 값이 늙는다 — 그게 경보의 근거다.
        lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
    }

    /**
     * MQ 지연 메시지 기반 건별 재시도 진입점
     * - 대기 큐 TTL 만료 후 도착한 메시지로 해당 구독만 재청구한다(전체 폴링 없음).
     * - 소비 시점 정합성 가드: 이미 복구/해지됐거나 스윕이 먼저 처리한 스테일 메시지는 건너뛴다.
     *
     * 3단계로 쪼갠 이유
     * - 예전에는 구독 행을 잠근 채로 외부 결제 API 를 호출해서 락 보유 시간이 PG 응답 시간에 묶여 있었다.
     *   이제 결정적 merchant_uid + 유니크 제약이 중복 청구를 막으므로, 락을 쥔 채 PG 를 부를 이유가 없다.
     * - 1단계(락+가드)와 3단계(결과 확정)만 트랜잭션이고, 2단계 PG 호출은 락도 트랜잭션도 밖이다.
     *   그래서 이 메서드 자체에는 @Transactional 을 걸지 않는다.
     */
    public void retryBilling(Long subscriptionId, int attempt) {
        ChargePlan plan;
        try {
            plan = self.prepareRetry(subscriptionId, attempt); // 1단계: 락 + 가드 (짧은 트랜잭션)
        } catch (PessimisticLockingFailureException e) { // NOWAIT: 다른 트랜잭션이 같은 구독을 처리 중
            // 실패가 아니라 중복/경합이다. 기다렸어도 가드에 걸려 버려질 메시지이므로 조용히 종료하고,
            // 혹시 정상 건이었다면 스윕 배치가 다음 주기에 복구한다. ERROR 로 올리면 오탐 알림이 된다.
            log.info("재시도 메시지 경합 skip(다른 트랜잭션이 처리 중) - subscriptionId: {}, attempt: {}", subscriptionId, attempt);
            return;
        }
        if (plan == null) { // 가드에 걸렸거나 결제수단이 없음
            return;
        }

        log.info("MQ 재시도 청구 시작 - subscriptionId: {}, attempt: {}", subscriptionId, attempt);
        ChargeOutcome outcome = attemptCharge(plan); // 2단계: 락·트랜잭션 밖에서 PG 호출

        try {
            self.finishCharge(subscriptionId, outcome, LocalDateTime.now()); // 3단계: 결과 확정 (짧은 트랜잭션)
        } catch (PessimisticLockingFailureException e) {
            // 이미 돈은 나갔지만 결제는 PENDING 으로 남아 있다. 대사 배치가 아임포트에 확인하고
            // reconcileRebillPayment 로 확정/연장까지 마무리하므로 유실되지 않는다.
            log.warn("재청구 결과 확정 지연(락 경합) - subscriptionId: {}, 대사 배치가 정리한다", subscriptionId);
        }
    }

    /**
     * 재시도 1단계 — 구독 행을 잠그고 가드를 통과시킨 뒤 청구 계획을 만든다.
     * - 락은 여기서만 잡고 바로 놓는다. 중복 배달의 실제 차단은 merchant_uid 유니크 제약이 한다.
     * @return 청구 계획, 청구하면 안 되는 상태면 null
     */
    @Transactional
    public ChargePlan prepareRetry(Long subscriptionId, int attempt) {
        MembershipSubscription sub =
                subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null); // 구독 조회(비관적 쓰기 락)
        if (sub == null) { // 삭제된 구독
            log.warn("재시도 대상 구독 없음 - subscriptionId: {}", subscriptionId);
            return null;
        }
        if (sub.getStatus() != MembershipSubscriptionStatus.PAST_DUE || !sub.isAutoRenew()) { // 이미 복구/해지됨
            log.info("재시도 불필요(상태 변경됨) - subscriptionId: {}, status: {}", subscriptionId, sub.getStatus());
            return null;
        }
        if (sub.getRetryCount() != attempt) { // 다른 경로(스윕)가 먼저 재시도한 스테일 메시지
            log.info(
                    "스테일 재시도 메시지 skip - subscriptionId: {}, messageAttempt: {}, currentRetryCount: {}",
                    subscriptionId,
                    attempt,
                    sub.getRetryCount());
            return null;
        }
        ChargePlan plan = planFor(sub);
        if (plan == null) { // 결제수단 없음
            sub.markPastDue(); // 연체 유지(시도가 없었으므로 카운트는 그대로)
        }
        return plan;
    }

    /**
     * 청구 3단계 — 청구 결과를 구독 상태에 반영한다(재시도·스윕 공통).
     * - 미확정/중복이면 구독 행을 읽을 필요조차 없다(상태를 건드리면 안 되는 케이스).
     */
    @Transactional
    public void finishCharge(Long subscriptionId, ChargeOutcome outcome, LocalDateTime now) {
        if (outcome.result() == ChargeOutcome.Result.AMBIGUOUS || outcome.result() == ChargeOutcome.Result.DUPLICATE) {
            return; // 아래 applyChargeOutcome 주석 참고
        }
        MembershipSubscription sub =
                subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("재청구 결과 반영 대상 구독 없음 - subscriptionId: {}", subscriptionId);
            return;
        }
        applyChargeOutcome(sub, outcome, now);
    }

    /**
     * 구독 1건 청구 + 성공/실패 처리 (스윕 배치 경로)
     * - 재시도 경로와 같은 3단계다: 계획 수립(트랜잭션) / PG 호출(트랜잭션 밖) / 결과 확정(트랜잭션).
     *   배치 트랜잭션 안에서 통째로 돌리던 것을 쪼갠 이유는 runRecurringBilling 주석 참고.
     * - 매퍼가 준 객체가 아니라 id 만 넘겨받는다. 그 객체는 대상 선별용이라 연관 엔티티가 id 만
     *   채워져 있고, 영속 상태가 아니라 상태를 바꿔도 저장되지 않는다.
     */
    private void billSubscription(Long subscriptionId, LocalDateTime now) {
        ChargePlan plan = self.prepareSweep(subscriptionId); // 1단계: 조회 + 계획 (짧은 트랜잭션)
        if (plan == null) { // 결제수단 없음 / 주기 앵커 없음 / 구독 사라짐
            return; // 다음 구독 처리
        }
        ChargeOutcome outcome = attemptCharge(plan); // 2단계: 트랜잭션 밖에서 PG 호출
        self.finishCharge(subscriptionId, outcome, now); // 3단계: 결과 확정 (짧은 트랜잭션)
    }

    /**
     * 스윕 1단계 — 구독을 JPA 로 읽어 청구 계획을 만든다.
     * - 락을 잡지 않는다. 재시도 경로와는 nextBillingAt(+3일)으로 시간 분리돼 있고, 그래도 겹치면
     *   결정적 merchant_uid 의 유니크 제약이 두 번째 청구를 삽입 단계에서 떨어뜨린다.
     * @return 청구 계획, 청구하면 안 되는 상태면 null
     */
    @Transactional
    public ChargePlan prepareSweep(Long subscriptionId) {
        MembershipSubscription sub =
                subscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) { // 조회 시점 이후에 삭제됨
            log.warn("청구 대상 구독 없음 - subscriptionId: {}", subscriptionId);
            return null;
        }
        ChargePlan plan = planFor(sub);
        if (plan == null) { // 결제수단 없음 / 주기 앵커 없음
            sub.markPastDue(); // 연체 전환(시도가 없었으므로 카운트는 그대로)
        }
        return plan;
    }

    /**
     * 청구 계획 수립 — 결제수단 목록과 merchant_uid 구성 요소를 모은다.
     * - 구독 엔티티에서 필요한 값을 미리 뽑아둔다. 재시도 경로는 트랜잭션이 닫힌 뒤에 이 계획으로 청구하므로
     *   지연 로딩 필드를 나중에 건드리면 안 된다.
     * @return 청구 계획, 청구가 불가능하면 null
     */
    private ChargePlan planFor(MembershipSubscription sub) {
        List<PaymentMethod> methods =
                paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(
                        sub.getUser().getId()); // 기본 우선 결제수단 목록(삭제 제외, 폴백 순회)
        if (methods.isEmpty()) { // 결제수단 없음
            return null;
        }
        // 청구주기 앵커: endAt 이 정답이다(청구 성공 시에만 움직임). nextBillingAt 은 실패할 때마다
        // 갱신되므로 앵커로 못 쓰고, 여기서는 endAt 이 없는 무기한/레거시 행의 폴백으로만 쓴다.
        LocalDateTime cycleAnchor = sub.getEndAt() != null ? sub.getEndAt() : sub.getNextBillingAt();
        if (cycleAnchor == null) {
            log.warn("청구주기 앵커 없음(endAt/nextBillingAt 모두 null) - subscriptionId: {}", sub.getId());
            return null;
        }
        return new ChargePlan(
                sub.getId(),
                sub.getUser().getId(),
                sub.getMembershipPlan().getId(),
                sub.getMembershipPlan().getPrice().getAmount(), // 청구 금액(월 기준)
                cycleAnchor,
                sub.getRetryCount(), // 이번 시도 번호(재시도 경로는 가드가 메시지 attempt 와 일치를 보장)
                methods);
    }

    /**
     * 결제수단을 순차로 시도한다(기본→보조 폴백).
     * - 트랜잭션을 스스로 관리하지 않는다. 재시도 경로는 트랜잭션 밖에서, 스윕은 배치 트랜잭션 안에서 호출한다.
     * - 각 시도는 PG 호출 전에 PENDING 을 먼저 커밋한다(BillingAttemptRecorder 주석 참고).
     */
    private ChargeOutcome attemptCharge(ChargePlan plan) {
        String currency = "KRW"; // 통화 코드
        String lastErrorCode = null; // 마지막 오류 코드
        String lastErrorMessage = null; // 마지막 오류 메시지

        for (PaymentMethod method : plan.methods()) { // 기본→보조 순차 시도
            String merchantUid =
                    RebillMerchantUid.create(plan.subscriptionId(), plan.cycleAnchor(), plan.attempt(), method.getId());

            Long paymentId;
            try {
                paymentId = attemptRecorder.openAttempt(
                        plan.userId(), plan.planId(), method.getId(), merchantUid, new Money(plan.amount(), currency));
            } catch (DataIntegrityViolationException dup) {
                if (attemptRecorder.isAlreadyDeclined(merchantUid)) {
                    // 직전 실행이 이 수단으로 시도했다 거절당한 뒤 끊긴 것이다(재배달/스윕 재진입).
                    // 여기서 중단하면 폴백이 영원히 이 수단에서 막혀 구독이 고착된다.
                    log.info(
                            "이미 거절된 결제수단 건너뜀 - subscriptionId: {}, merchantUid: {}",
                            plan.subscriptionId(),
                            merchantUid);
                    lastErrorCode = "ALREADY_DECLINED";
                    lastErrorMessage = "이전 실행에서 거절된 결제수단";
                    continue;
                }
                // 같은 시도가 지금 진행 중이거나 이미 청구됐다 = 중복 배달. 멈추지 않으면 그게 이중 청구다.
                log.info(
                        "중복 청구 시도 차단(merchant_uid 선점됨) - subscriptionId: {}, merchantUid: {}",
                        plan.subscriptionId(),
                        merchantUid);
                return ChargeOutcome.duplicate();
            }

            try { // 청구 시도
                PaymentGateway.ChargeResult cr = paymentGateway.chargeWithSavedMethod(
                        String.valueOf(plan.userId()), // 고객 식별자(예시: 내부 ID 사용)
                        method.getProviderMethodId(), // 저장 결제수단 ID
                        merchantUid, // 결정적 주문번호(중복 청구 시 게이트웨이도 거절)
                        plan.amount(), // 청구 금액
                        currency, // 통화
                        "Subscription renewal" // 설명
                        );
                return ChargeOutcome.charged(paymentId, cr);
            } catch (PaymentGateway.ChargeException ex) { // 게이트웨이 예외(유형 포함)
                if (ex.failureType == PaymentGateway.FailureType.AMBIGUOUS) {
                    // 승인됐는지 알 수 없다. 실패로 처리해 다음 시도를 예약하면 새 merchant_uid 로 또 청구된다.
                    // PENDING 을 남긴 채 중단하고, 대사 배치가 아임포트에 물어본 뒤 확정한다.
                    log.warn(
                            "재청구 결과 미확정 - subscriptionId: {}, merchantUid: {}, 사유: {}",
                            plan.subscriptionId(),
                            merchantUid,
                            ex.getMessage());
                    return ChargeOutcome.ambiguous();
                }
                attemptRecorder.markAttemptFailed(paymentId, LocalDateTime.now()); // 확정 실패로 닫는다
                lastErrorCode = ex.errorCode; // 코드 기록
                lastErrorMessage = ex.getMessage(); // 메시지 기록
            } catch (Exception ex) { // 게이트웨이 계약 밖의 예외
                // 승인 여부를 알 수 없으므로 확정 실패로 취급하면 안 된다(위와 같은 이유).
                log.warn(
                        "재청구 결과 미확정(예상 못한 예외) - subscriptionId: {}, merchantUid: {}",
                        plan.subscriptionId(),
                        merchantUid,
                        ex);
                return ChargeOutcome.ambiguous();
            }
        }

        return ChargeOutcome.declined(lastErrorCode, lastErrorMessage); // 모든 수단이 명시적으로 거절됨
    }

    /**
     * 청구 결과를 구독 상태에 반영한다(호출자의 트랜잭션 안에서 수행).
     * - 성공: 결제 확정 + 기간/next_billing_at 연장, 재시도 카운트 리셋
     * - 확정 실패: PAST_DUE 전환 후 MQ 지연 큐에 건별 재시도 예약(1차 3h, 2차 24h),
     *   3회 소진 시 해지+메일. 발행 실패 시 기존 스윕 방식(+1일)으로 폴백.
     * - 미확정/중복: 아무것도 바꾸지 않는다. retryCount 를 올리면 다음 시도가 새 merchant_uid 로
     *   또 청구돼 이중 청구가 되고, 상태를 바꾸면 대사가 확정할 때 기준이 흔들린다.
     */
    private void applyChargeOutcome(MembershipSubscription sub, ChargeOutcome outcome, LocalDateTime now) {
        switch (outcome.result()) {
            case AMBIGUOUS, DUPLICATE -> {
                return;
            }
            case CHARGED -> {
                // 결제 확정과 구독 연장을 같은 트랜잭션에서 끝낸다. 중간에 죽으면 결제는 PENDING 으로 남고
                // 대사 배치가 reconcileRebillPayment 로 둘 다 마무리한다.
                paymentRepository.findById(outcome.paymentId()).ifPresent(payment -> {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.markAsSucceeded(outcome.providerPaymentId(), outcome.paidAt());
                        payment.attachReceipt(outcome.receiptUrl());
                        paymentRepository.save(payment);
                    }
                });
                extendAfterSuccess(sub, now);
            }
            case DECLINED -> {
                // 연체 전환 + 실패 기록 + 재시도 카운트 증가를 한 번에(엔티티가 짝을 보장)
                int nextRetry = sub.recordDeclinedCharge(now, outcome.lastErrorCode(), outcome.lastErrorMessage());

                if (nextRetry >= 3) { // 최대 재시도 소진
                    sub.cancelAfterDunningExhausted(now); // 해지 + 해지 시각 + 자동갱신 중단 + 말일 해지 예약
                    // 알림: 결제 실패 누적 해지 안내 메일 발송
                    notificationService.sendCanceledDueToDunning(sub.getUser(), sub);
                } else {
                    // MQ 지연 큐에 건별 재시도 예약(1차: 3h, 2차: 24h 뒤 정확히 이 구독만 도착)
                    boolean scheduled = billingRetryPublisher.scheduleRetry(sub.getId(), nextRetry);
                    // 안전망: 메시지 유실 대비 스윕이 +3일 후 잡도록 예약(성공 시 nextBillingAt이 갱신돼 중복 없음).
                    // 발행 실패(브로커 장애) 시에는 기존 스윕 방식(+1일)으로 폴백한다.
                    sub.scheduleNextBillingAt(scheduled ? now.plusDays(3) : now.plusDays(1));
                }
            }
        }
    }

    /**
     * 청구 성공 후 구독 연장/복구 (정상 경로와 대사 확정 경로 공통)
     */
    private void extendAfterSuccess(MembershipSubscription sub, LocalDateTime now) {
        LocalDateTime start = sub.getEndAt() != null && sub.getEndAt().isAfter(now) ? sub.getEndAt() : now; // 연장 시작점 계산
        LocalDateTime newEnd = start.plusMonths(sub.getMembershipPlan().getPeriodMonths()); // 새 종료 시각 계산
        sub.renewUntil(newEnd, now); // 기간/청구일 갱신 + 상태 복구 + 던닝 기록 초기화
    }

    /**
     * 대사 배치가 찾아낸 재청구 PENDING 결제 확정
     *
     * - 체크아웃 경로(PaymentCommandService.markSucceededAndProvision)를 재청구에 쓸 수 없다.
     *   그 경로는 membershipCommandService.subscribe() 로 "새 구독"을 만든다. 재청구 대상 구독은
     *   PAST_DUE 라 연장 조건에도 안 걸려서, 연장돼야 할 원래 구독은 그대로 던닝을 돌다 해지되고
     *   그 옆에 고아 구독이 하나 더 생긴다(차액 결제를 대사에서 제외하는 것과 정확히 같은 이유).
     * - paid: 결제 확정 + 원래 구독 연장/복구. 정상 재청구 성공과 같은 결과로 수렴한다.
     * - failed/cancelled: 결제만 닫는다. 던닝 진행 여부는 재시도/스윕 경로가 판단한다.
     *
     * 구독 행은 비관적 락으로 읽는다. 경합하면 예외가 나면서 이 건 전체가 롤백되고(결제 확정도 함께),
     * 10분 뒤 다음 대사 주기에 다시 시도한다 — 반쯤 반영된 상태로 커밋되는 것보다 낫다.
     */
    @Transactional
    public boolean reconcileRebillPayment(Payment payment, PaymentGateway.ReconcileResult r, LocalDateTime now) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return false; // 이미 정리됨
        }
        Long subscriptionId = RebillMerchantUid.subscriptionIdOf(payment.getProviderSessionId());

        switch (r.status) {
            case PAID:
                long expected = (payment.getPrice() != null ? payment.getPrice().getAmount() : 0L); // 서버 확정 금액
                if (r.amount != expected) {
                    log.warn(
                            "재청구 대사 금액 불일치 - paymentId: {}, expected: {}, actual: {}",
                            payment.getId(),
                            expected,
                            r.amount);
                    return false; // 금액 불일치는 자동 확정하지 않음(수동 확인 대상)
                }
                payment.markAsSucceeded(r.providerPaymentId, now);
                if (r.receiptUrl != null) {
                    payment.attachReceipt(r.receiptUrl);
                }
                paymentRepository.save(payment);
                if (subscriptionId == null) {
                    log.error("재청구 대사 - merchant_uid 에서 구독 ID 추출 실패, 구독 연장 누락: {}", payment.getProviderSessionId());
                    return true; // 결제는 확정됐으므로 대사 대상에서는 빠진다
                }
                subscriptionRepository.findByIdForUpdate(subscriptionId).ifPresent(sub -> extendAfterSuccess(sub, now));
                log.info("대사로 재청구 확정 - paymentId: {}, subscriptionId: {}", payment.getId(), subscriptionId);
                return true;
            case FAILED:
                payment.markAsFailed(now);
                paymentRepository.save(payment);
                return true;
            case CANCELLED:
                payment.applyGatewayCancellation(now);
                paymentRepository.save(payment);
                return true;
            default:
                return false; // READY/UNKNOWN → 판정 불가, 미결 유지
        }
    }

    /**
     * 플랜 변경 예약된 구독 처리
     * - 다음 결제일이 도래한 플랜 변경 예약 구독들을 처리
     * - 배치 트랜잭션이 사라졌으므로 아래 save 는 건별로 커밋된다. 한 건이 실패해도 앞서 적용된
     *   플랜 변경이 되돌아가지 않고, 뒤이은 청구 순회가 터져도 마찬가지다(원래는 같이 롤백됐다).
     */
    private void processScheduledPlanChanges(LocalDateTime now) {
        log.info("플랜 변경 예약 구독 처리 시작 - {}", now);

        // 플랜 변경 예약된 구독 조회
        List<MembershipSubscription> scheduledPlanChanges =
                membershipSubscriptionQueryMapper.findSubscriptionsWithScheduledPlanChanges(
                        List.of(
                                MembershipSubscriptionStatus.ACTIVE.name(),
                                MembershipSubscriptionStatus.PAST_DUE.name()),
                        now);

        log.info("플랜 변경 예약 구독 수: {}", scheduledPlanChanges.size());

        for (MembershipSubscription subscription : scheduledPlanChanges) {
            try {
                MembershipSubscription applied = self.applyScheduledPlanChange(subscription.getId());
                if (applied == null) { // 적용할 예약이 남아 있지 않음
                    continue;
                }

                // 알림은 트랜잭션 밖에서 보낸다(ARCHITECTURE 4절). 위 조회가 user 와 새 플랜을
                // 함께 읽어 뒀으므로 준영속 상태에서도 그대로 읽힌다.
                notificationService.sendPlanChangeNotification(applied.getUser(), applied, applied.getMembershipPlan());

                log.info(
                        "플랜 변경 완료 - userId: {}, newPlan: {}",
                        applied.getUser().getId(),
                        applied.getMembershipPlan().getName());

            } catch (Exception e) {
                log.error(
                        "플랜 변경 처리 실패 - userId: {}, subscriptionId: {}",
                        subscription.getUser().getId(),
                        subscription.getId(),
                        e);
            }
        }

        log.info("플랜 변경 예약 구독 처리 완료 - 처리된 구독: {}", scheduledPlanChanges.size());
    }

    /**
     * 예약된 플랜 변경 적용 — 매퍼가 고른 구독을 JPA 로 다시 읽어 교체하고 커밋한다.
     *
     * 매퍼 객체를 그대로 쓰면 안 되는 이유는 d3db5e3 과 같은 뿌리다. resultMap 이 연관을 식별자만
     * 채우므로 user.email 이 null 이고, 알림 서비스는 수신자가 없으면 조용히 return 한다 —
     * 예외도 로그도 없이 안내 메일만 안 나갔다. 여기서 다시 읽어야 수신자가 채워진다.
     *
     * @return 변경이 적용된 관리 엔티티, 적용할 예약이 없으면 null
     */
    @Transactional
    public MembershipSubscription applyScheduledPlanChange(Long subscriptionId) {
        MembershipSubscription sub = subscriptionRepository
                .findWithUserAndNextPlanById(subscriptionId)
                .orElse(null);
        if (sub == null) { // 대상 조회 이후에 삭제됐거나 다른 경로가 예약을 이미 적용했다
            log.warn("플랜 변경 대상 구독 없음 - subscriptionId: {}", subscriptionId);
            return null;
        }
        // 교체와 예약 해제를 함께 한다 — 예약이 남으면 다음 배치가 또 적용한다.
        // 관리 엔티티라 변경 감지로 저장된다(save 불필요).
        sub.changePlanTo(sub.getNextPlan());
        return sub;
    }

    /**
     * 청구 계획 — 트랜잭션 밖에서 PG 를 호출하기 위해 구독에서 뽑아둔 값들.
     */
    public record ChargePlan(
            Long subscriptionId,
            Long userId,
            Long planId,
            long amount,
            LocalDateTime cycleAnchor,
            int attempt,
            List<PaymentMethod> methods) {}

    /**
     * 청구 결과
     * - CHARGED: 승인 확인됨
     * - DECLINED: 모든 결제수단이 명시적으로 거절됨(던닝 진행 대상)
     * - AMBIGUOUS: 승인 여부 불명(던닝 진행 금지, 대사가 판단)
     * - DUPLICATE: 같은 시도가 이미 기록됨(중복 배달, 아무것도 하지 않음)
     */
    public record ChargeOutcome(
            Result result,
            Long paymentId,
            String providerPaymentId,
            LocalDateTime paidAt,
            String receiptUrl,
            String lastErrorCode,
            String lastErrorMessage) {
        public enum Result {
            CHARGED,
            DECLINED,
            AMBIGUOUS,
            DUPLICATE
        }

        static ChargeOutcome charged(Long paymentId, PaymentGateway.ChargeResult cr) {
            return new ChargeOutcome(
                    Result.CHARGED, paymentId, cr.providerPaymentId, cr.paidAt, cr.receiptUrl, null, null);
        }

        static ChargeOutcome declined(String errorCode, String errorMessage) {
            return new ChargeOutcome(Result.DECLINED, null, null, null, null, errorCode, errorMessage);
        }

        static ChargeOutcome ambiguous() {
            return new ChargeOutcome(Result.AMBIGUOUS, null, null, null, null, null, null);
        }

        static ChargeOutcome duplicate() {
            return new ChargeOutcome(Result.DUPLICATE, null, null, null, null, null, null);
        }
    }
}
