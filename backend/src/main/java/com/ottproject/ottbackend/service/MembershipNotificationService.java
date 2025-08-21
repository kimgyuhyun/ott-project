package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.MembershipSubscription;
import com.ottproject.ottbackend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
}


