package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.MembershipPlan;
import com.ottproject.ottbackend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * MembershipNotificationService
 *
 * 큰 흐름
 * - 구독 말일 해지 예약 및 재시도 실패로 인한 해지 등 주요 이벤트를 이메일로 안내한다.
 * - 운영에서는 HTML 템플릿/다국어/발송 모듈 분리를 권장한다.
 *
 * 메서드 개요
 * - sendCancelAtPeriodEnd: 말일 해지 예약 안내 메일 발송
 * - sendCanceledDueToDunning: 결제 실패 누적으로 인한 해지 안내 메일 발송
 * - sendPlanChangeNotification: 플랜 변경 완료 안내 메일 발송
 * - sendPlanChangeReminder: 플랜 변경 예정 안내 메일 발송 (스케줄러)
 */
@Service
@RequiredArgsConstructor
public class MembershipNotificationService { // 알림 메일 서비스

    private final JavaMailSender mailSender; // 메일 발송기

    @Value("${spring.mail.username:no-reply@example.com}")
    private String fromEmail; // 발신 이메일(설정 값)

    public void sendCancelAtPeriodEnd(User user, MembershipSubscription sub) { // 말일 해지 예약 안내
        if (user == null || user.getEmail() == null) return; // 수신자 확인
        SimpleMailMessage msg = new SimpleMailMessage(); // 텍스트 메일
        msg.setFrom(fromEmail); // 발신자
        msg.setTo(user.getEmail()); // 수신자
        msg.setSubject("[OTT] 멤버십 말일 해지 예약 안내"); // 제목
        msg.setText("안녕하세요, " + user.getName() + "님.\n\n" +
                "멤버십이 말일 해지로 예약되었습니다. 만료일까지는 혜택이 유지되며, 이후 자동 갱신되지 않습니다.\n" +
                "플랜: " + sub.getMembershipPlan().getName() + "\n" +
                (sub.getEndAt() != null ? ("만료일: " + sub.getEndAt()) : "") + "\n\n" +
                "감사합니다."); // 본문
        mailSender.send(msg); // 발송
    }

    public void sendCanceledDueToDunning(User user, MembershipSubscription sub) { // 연체로 인한 해지 안내
        if (user == null || user.getEmail() == null) return; // 수신자 확인
        SimpleMailMessage msg = new SimpleMailMessage(); // 텍스트 메일
        msg.setFrom(fromEmail); // 발신자
        msg.setTo(user.getEmail()); // 수신자
        msg.setSubject("[OTT] 결제 실패로 인한 멤버십 해지 안내"); // 제목
        msg.setText("안녕하세요, " + user.getName() + "님.\n\n" +
                "등록된 결제수단으로 여러 차례 결제 재시도를 진행했으나 모두 실패하여 멤버십이 해지되었습니다.\n" +
                "플랜: " + sub.getMembershipPlan().getName() + "\n" +
                "다시 구독하시려면 결제수단을 확인한 뒤 구독을 신청해주세요.\n\n" +
                "감사합니다."); // 본문
        mailSender.send(msg); // 발송
    }

    /**
     * 플랜 변경 완료 안내 메일 발송
     * - 다운그레이드 시 다음 결제일부터 적용 완료 안내
     */
    public void sendPlanChangeNotification(User user, MembershipSubscription subscription, MembershipPlan newPlan) {
        if (user == null || user.getEmail() == null) return; // 수신자 확인
        
        SimpleMailMessage msg = new SimpleMailMessage(); // 텍스트 메일
        msg.setFrom(fromEmail); // 발신자
        msg.setTo(user.getEmail()); // 수신자
        msg.setSubject("[OTT] 멤버십 플랜 변경 완료 안내"); // 제목
        msg.setText("안녕하세요, " + user.getName() + "님.\n\n" +
                "요청하신 멤버십 플랜 변경이 완료되었습니다.\n" +
                "새로운 플랜: " + newPlan.getName() + "\n" +
                "월 요금: " + newPlan.getPrice() + "원\n" +
                "적용일: " + LocalDateTime.now().toLocalDate() + "\n\n" +
                "새로운 플랜의 혜택을 즐겨보세요!\n\n" +
                "감사합니다."); // 본문
        mailSender.send(msg); // 발송
    }

    /**
     * 플랜 변경 예정 안내 메일 발송 (스케줄러)
     * - 다운그레이드 예정인 사용자에게 3일 전 안내
     */
    @Scheduled(cron = "0 0 9 * * *") // 매일 오전 9시 실행
    public void sendPlanChangeReminder() {
        // TODO: 플랜 변경 예정일이 3일 후인 구독들을 조회하여 안내 메일 발송
        // 현재는 기본 구조만 구현
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);
        
        // 실제 구현에서는 MyBatis 매퍼를 통해 3일 후 플랜 변경 예정 구독들을 조회
        // List<MembershipSubscription> upcomingChanges = membershipSubscriptionQueryMapper
        //     .findSubscriptionsWithPlanChangeInDays(threeDaysLater);
        
        // for (MembershipSubscription subscription : upcomingChanges) {
        //     sendPlanChangeReminderEmail(subscription.getUser(), subscription);
        // }
    }

    /**
     * 플랜 변경 예정 안내 메일 발송 (개별)
     */
    private void sendPlanChangeReminderEmail(User user, MembershipSubscription subscription) {
        if (user == null || user.getEmail() == null) return; // 수신자 확인
        
        SimpleMailMessage msg = new SimpleMailMessage(); // 텍스트 메일
        msg.setFrom(fromEmail); // 발신자
        msg.setTo(user.getEmail()); // 수신자
        msg.setSubject("[OTT] 멤버십 플랜 변경 예정 안내"); // 제목
        msg.setText("안녕하세요, " + user.getName() + "님.\n\n" +
                "요청하신 멤버십 플랜 변경이 3일 후에 적용됩니다.\n" +
                "새로운 플랜: " + subscription.getNextPlan().getName() + "\n" +
                "월 요금: " + subscription.getNextPlan().getPrice() + "원\n" +
                "적용일: " + subscription.getPlanChangeScheduledAt().toLocalDate() + "\n\n" +
                "변경을 취소하시려면 고객센터로 문의해주세요.\n\n" +
                "감사합니다."); // 본문
        mailSender.send(msg); // 발송
    }

    /**
     * 멤버십 정기결제 재시작 알림 메일 발송
     */
    public void sendResumeNotification(User user, MembershipSubscription subscription) {
        if (user == null || user.getEmail() == null) return; // 수신자 확인
        
        SimpleMailMessage msg = new SimpleMailMessage(); // 텍스트 메일
        msg.setFrom(fromEmail); // 발신자
        msg.setTo(user.getEmail()); // 수신자
        msg.setSubject("[OTT] 멤버십 정기결제 재시작 안내"); // 제목
        msg.setText("안녕하세요, " + user.getName() + "님.\n\n" +
                "멤버십 정기결제가 다시 시작되었습니다.\n" +
                "플랜: " + subscription.getMembershipPlan().getName() + "\n" +
                "다음 결제일: " + subscription.getNextBillingAt().toLocalDate() + "\n" +
                "멤버십이 자동으로 갱신됩니다.\n\n" +
                "감사합니다."); // 본문
        mailSender.send(msg); // 발송
    }
}


