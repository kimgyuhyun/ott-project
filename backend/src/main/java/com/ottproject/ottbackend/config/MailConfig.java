package com.ottproject.ottbackend.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 메일 발송 설정 구성 클래스
 * - JavaMailSender Bean 등록
 * - SMTP 인증/STARTTLS 등 메일 전송 속성 구성
 */
@Configuration // spring 설정 클래스로 등록
public class MailConfig {

    @Value("${spring.mail.host}") // application-dev.yml 에서 메일 호스트 주입
    private String host;

    @Value("${spring.mail.port}") // application-dev.yml 에서 메일 포트 주입
    private int port;

    @Value("${spring.mail.username}") // application-dev.yml 에서 메일 사용자명 주입
    private String username;

    @Value("${spring.mail.password}") // application-dev.yml 에서 메일 비밀번호 주입
    private String password;

    // [SECURITY 2026-08-07] 아웃바운드 목적지 허용목록. 백엔드는 더 이상 egress 망에 있지
    // 않아 SMTP 도 프록시를 거쳐야 나간다. HTTP 프록시(squid)는 SMTP 를 나르지 못하므로
    // 메일만 SOCKS5 로 보낸다.
    //   - SOCKS 를 고른 이유: 목적지 호스트명이 그대로 전달돼 STARTTLS 인증서 검증이
    //     지금과 똑같이 동작한다. TCP 릴레이로 바꾸면 검증이 깨진다.
    //   - JVM 전역 -DsocksProxyHost 를 쓰지 않는 이유: java.net.Socket 레벨에 걸려서
    //     JDBC·Kafka·Redis·RabbitMQ 연결까지 전부 SOCKS 로 끌고 간다.
    //   - 이 클래스에 넣는 이유: 아래 javaMailSender() 가 Properties 를 직접 채우므로
    //     application-*.yml 의 spring.mail.properties.* 는 무시된다.
    @Value("${mail.socks.host:}") // 비어 있으면 지금처럼 직통(dev 스택 동작 보존)
    private String socksHost;

    @Value("${mail.socks.port:1080}")
    private String socksPort;

    /**
     * JavaMailSender Bean 생성
     * - 호스트/포트/계정 설정 주입
     * - SMTP 인증 및 STARTTLS 활성화
     */
    @Bean // javaMailSender Bean 등록 (이메일 발송을 위한 핵심 컴포넌트)
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl(); // JavaMail 구현체 생성
        mailSender.setHost(host); // SMTP 서버 호스트 설정
        mailSender.setPort(port); // SMTP 서버 포트 설정
        mailSender.setUsername(username); // 발송자 이메일 주소 설정
        mailSender.setPassword(password); // 발송자 비밀번호 설정

        Properties props = mailSender.getJavaMailProperties(); // 메일 서버 속성 설정
        props.put("mail.transport.protocol", "smtp"); // 전송 프로토콜을 SMTP 로 설정
        props.put("mail.smtp.auth", "true"); // SMTP 인증 활성화
        props.put("mail.smtp.starttls.enable", "true"); // STARTTLS 암호화 활성화
        props.put("mail.smtp.ssl.enable", "false"); // SSL 비활성화 (STARTTLS 사용)
        props.put("mail.smtp.ssl.trust", host); // SSL 인증서 신뢰 설정
        props.put("mail.smtp.auth.mechanisms", "LOGIN"); // 인증 메커니즘을 LOGIN 으로 설정
        props.put("mail.smtp.auth.login.disable", "false"); // LOGIN 인증 활성화
        props.put("mail.smtp.auth.plain.disable", "false"); // PLAIN 인증 활성화
        props.put("mail.debug", "true"); // 메일 디버그 모드 활성화 (발송 과정 로그 확인)

        if (socksHost != null && !socksHost.isBlank()) { // 값이 없으면 직통 유지
            props.put("mail.smtp.socks.host", socksHost); // SOCKS5 프록시 경유
            props.put("mail.smtp.socks.port", socksPort);
        }

        return mailSender; // 설정된 JavaMailSender 반환
    }
}
