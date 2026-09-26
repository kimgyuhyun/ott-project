package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.Money;
import com.ottproject.ottbackend.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * MembershipNotificationService 발송 시점 단위 테스트
 *
 * 지키려는 규칙
 * - 트랜잭션 밖(결제 이벤트 컨슈머의 영수증)에서는 바로 보내고 실패를 그대로 던진다. 컨슈머의 재시도가 그 예외에 기댄다.
 *
 * 결함 기록(2026-09-26)
 * - 트랜잭션 안에서 불려도 메일을 그 자리에서 보낸다. SMTP 가 실패하면 예외가 호출부로 나가 해지·연체 해지
 *   트랜잭션을 롤백시키고, 롤백돼도 이미 보낸 메일은 되돌릴 수 없다. SMTP 가 응답하지 않으면 그 트랜잭션이
 *   커넥션과 구독 행 잠금을 쥔 채 기다린다.
 * - 아래 "(현재 결함)" 테스트는 이 동작을 기록한다. 수정 커밋이 뒤집는다.
 *
 * 트랜잭션은 TransactionSynchronizationManager 로 흉내 낸다. 스프링은 커밋과 롤백 때 등록된 콜백의
 * afterCommit·afterCompletion 을 부르므로, 테스트도 그 콜백을 직접 부른다.
 */
@ExtendWith(MockitoExtension.class)
class MembershipNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MembershipNotificationService notificationService;

    private User user;
    private MembershipSubscription sub;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("member@example.com", "pw", "회원");
        MembershipPlan plan = MembershipPlan.createBasicPlan("BASIC", "기본 플랜", new Money(9900L, "KRW"), 1);
        sub = MembershipSubscription.createSubscription(
                user,
                plan,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().plusDays(20));
        sub.scheduleNextBillingAt(LocalDateTime.now().plusDays(20)); // 재개 안내 본문이 다음 결제일을 읽는다
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서 불러도 커밋 전에 바로 보낸다(현재 결함)")
    void sendsBeforeCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        notificationService.sendCancelAtPeriodEnd(user, sub);

        // 결함: 커밋이 끝나기 전에 이미 나갔다
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("트랜잭션 안에서 발송이 실패하면 예외가 호출부로 나가 그 트랜잭션을 롤백시킨다(현재 결함)")
    void propagatesSendFailureInsideTransaction() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        TransactionSynchronizationManager.initSynchronization();

        // 결함: 연체 해지를 커밋하기 전에 예외가 나가, 해지까지 롤백된다
        assertThatThrownBy(() -> notificationService.sendCanceledDueToDunning(user, sub))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("롤백돼도 메일은 이미 나간 뒤다(현재 결함)")
    void hasAlreadySentWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        notificationService.sendResumeNotification(user, sub);
        rollback();

        // 결함: 재개가 롤백됐는데 재개 안내는 나갔다
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 바로 보내고, 실패하면 예외를 그대로 던진다")
    void sendsImmediatelyAndPropagatesFailureOutsideTransaction() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.sendPaymentReceipt(user, "BASIC", 9900L, LocalDateTime.now()))
                .isInstanceOf(MailSendException.class);
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
