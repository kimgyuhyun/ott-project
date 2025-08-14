    package com.ottproject.ottbackend.config;

    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.mail.javamail.JavaMailSender;
    import org.springframework.mail.javamail.JavaMailSenderImpl;

    import java.util.Properties;

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

            return mailSender; // 설정된 JavaMailSender 반환

        }
    }
