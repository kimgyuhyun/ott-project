package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
 * - 트랜잭션 안에서 부른 메일은 커밋이 끝난 뒤에 보내고, 롤백되면 보내지 않는다(ARCHITECTURE 4절).
 * - 커밋 뒤의 발송 실패는 호출부로 나가지 않는다. 이미 커밋된 해지·연체 해지는 되돌릴 수 없다.
 * - 트랜잭션 밖(결제 이벤트 컨슈머의 영수증)에서는 바로 보내고 실패를 그대로 던진다. 컨슈머의 재시도가 그 예외에 기댄다.
 *
 * 결함 배경(2026-09-26)
 * - 해지·재개·연체 해지 메일을 트랜잭션 안에서 바로 보냈다. SMTP 가 실패하면 해지까지 롤백됐고,
 *   SMTP 가 응답하지 않으면 트랜잭션이 커넥션과 구독 행 잠금을 쥔 채 기다렸다.
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
    @DisplayName("트랜잭션 안에서 부르면 커밋 전에는 보내지 않고, 커밋 뒤에 보낸다")
    void sendsOnlyAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        notificationService.sendCancelAtPeriodEnd(user, sub);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        commit();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("커밋 뒤 발송이 실패해도 예외가 호출부로 나가지 않는다")
    void doesNotPropagateSendFailureAfterCommit() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        TransactionSynchronizationManager.initSynchronization();

        assertThatCode(() -> {
                    notificationService.sendCanceledDueToDunning(user, sub);
                    commit();
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void doesNotSendWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        notificationService.sendResumeNotification(user, sub);
        rollback();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 바로 보내고, 실패하면 예외를 그대로 던진다")
    void sendsImmediatelyAndPropagatesFailureOutsideTransaction() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.sendPaymentReceipt(user, "BASIC", 9900L, LocalDateTime.now()))
                .isInstanceOf(MailSendException.class);
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
