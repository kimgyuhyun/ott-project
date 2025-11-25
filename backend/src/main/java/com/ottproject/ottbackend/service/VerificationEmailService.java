package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VerificationEmailService
 *
 * 큰 흐름
 * - 인증 코드 메일 발송/검증과 비밀번호 재설정 메일 발송을 제공한다.
 * - 실제 운영에서는 Redis 등 외부 저장소/레이트리밋을 권장한다.
 *
 * 메서드 개요
 * - sendVerificationEmail: 인증 코드 발송
 * - verifyCode: 인증 코드 검증
 * - isEmailVerified: 인증 여부 확인
 * - sendPasswordResetEmail: 비밀번호 재설정 메일 발송
 */
@Service // 스프링에 Bean에 Service로 등록함 싱글턴 패턴을 사용 / 싱글턴 패턴은 클래스의 인스턴스가 하나만 생성되도록 보장하는것것
@RequiredArgsConstructor // final 필드만 생성자에 파라미터로 받는 생성자를 생성함
public class VerificationEmailService {

    private final JavaMailSender mailSender; // spring boot mail 자동 주입 (SMTP 서버 연결용)

    @Value("${spring.mail.username}") // application-dev.yml 에서 발신자 이메일 주소 주입
    // @Value 애너테이션은 Spring이 설정 파일 yml에 정의된 해당 키의 값을 읽어서 필드에 주입해줌줌
    private String fromEmail; // 발신자 이메일 주소

    private final ConcurrentHashMap<String, String> emailVerificationCodes = new ConcurrentHashMap<>();
    // concurrentHashMap는 자바에서 제공하는 표준 라이브러리고  emailVerificationCodes는 
    // 이메일 주소(키)와 인증 코드(값)을 저장하는 스레드 안전맵이고 동시 접근에 안전함
    private final ConcurrentHashMap<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();
    // verifieldEmails는 이메일 주소와 인증 완료 여부를 저장하는 스레드 안전 맵임 값이 true면 이메일이 인증 완료된 상태
    // 두 필드 모두 fianl이라 생성 후 재할당되지 않고 RequriedArgsConstructor로 생성자 주입이 되지 않으므로 직접 초기화됨
    // 동시 접근에서 HashMap을 쓰면 데이터 손실이나 예외가 발생할 수 있고, 이때 concurrentHashMap을 사용하면 안전하게 동작함
    // 실제 운영에서는 Redis 같은 외부 저장소를 써야함 메모리 기만 맵은 서버 재시작 시 데이터가 살지고, 여러 서버 인스턴스간 공유가안됨

    public void sendVerificationEmail(String to) {
        // 인증 코드 발송 메서드 파라미터로 String 타입을 받고 리턴값은 없음
        String verificationCode = generateVerificationCode();
        // 6자리 인증 코드 메서드를 호출해서 생성된 코드를 verificationCode 변수에 할당함
        emailVerificationCodes.put(to, verificationCode);
        // 스레드안전맵에 이메일 주소와 인증 코드를 저장함
        SimpleMailMessage message = new SimpleMailMessage();
        // SimpleMailMessage는 Spring FrameWork의 메일 지원 라이브러리고 Spring Mail 모듈의 클래스임
        // 간단한 텍스트 이메일을 구성하는 데 사용하고 HTML이나 첨부파일 없이 순수 텍스트 보낼 떄 사용함
        message.setFrom(fromEmail); // 발신자 이메일 주소를 설정함
        message.setTo(to); // 수신자 이메일 주소를 설정함
        message.setSubject("OTT 프로젝트 인증 코드"); // 메일 제목을 설정함
        message.setText("이메일 인증 코드: " + verificationCode + "\n\n"
                + "이 코드를 입력하여 이메일 인증을 완료해주세요.\n"
                + "인증 코드는 10분간 유효합니다."); // 메일 본문을 설정함

        mailSender.send(message); // SMTP 서버를 통해 이메일 발송함
    }


    public boolean verifyCode(String email, String code) {
        // 인증 코드 검증 메서드 파라미터로 email과 code를 String 타입으로 받고 boolean 타입을 반환함
        String storedCode = emailVerificationCodes.get(email);
        // 스레드안전맵에 저장된 이메일주소에 해당되는 인증코드를 조회해서 storedCode 변수에 할당함
        if (storedCode != null && storedCode.equals(code)) {
            // 만약 stroedCode가 null이 아니고 이메일의 값으로 입력된 인증코드와 전달받은 code와 일치하면
            verifiedEmails.put(email, true); // email과 true를 스레드안전맵에 저장함
            emailVerificationCodes.remove(email); // 사용된 인증 코드를 제거함
            return true; // 인증 코드 검증 성공하면 true를 반환함
        }
        return false; // 인증 코드 검증 실패하면 false를 반환함
    }

    public boolean isEmailVerified(String email) {
        // 이메일 인증여부 확인하는 메서드 파라미터로 email을 String 타입으로 받고 boolean 타입을 반환함
        return verifiedEmails.getOrDefault(email, false);
        // getOrDefault 메서드는 맵에 키가 존재하면 그 키를 반환하고 없으면 디폴트 값을 반환함 여기서는 false를 반환함
        // verifyCode 메서드에서 성공하면 맵에 email과 true를 저장하지만 실패하면 아무값도 저장하지 않기에 가능한 로직
        // 메모리도 아낄수있음
    }

    private String generateVerificationCode() {
        // 6자리 인증 코드 생성 메서드 (0~9 숫자로 구성) 외부에선 쓰지 않으므로 private로 선언 String타입을 반환함
        Random random = new Random(); // 랜덤 객체 생성
        StringBuilder code = new StringBuilder(); // 인증 코드를 넣을 빈 버퍼 생성성
        // StringBuilder는 문자열을 붙이거나 수정할 때 쓰는 가변(mutable) 버퍼임
        // 인증코드를 만드는 과정에서 매번 새로운 String을 생성하는 대신 StringBuilder에 문자를 순차적으로 추가한 뒤 마지막에
        // 한 번만 String으로 변환함
        for (int i = 0; i < 6; i++) {
            // 0 ~ 5 반복 즉 6번 반복 0 1 2 3 4 5
            code.append(random.nextInt(10));
            // random.nextInt(10)은 0 ~ 9 사이의 랜덤 숫자 하나를 생성함
            // 그걸 code 버퍼에 추가함 이걸 6번 반복하니 0~9사이의 랜덤 숫자가 6개 생성되서 쌓이는것
        }
        return code.toString(); // 버퍼에 쌓인 6자리 숫자를 String으로 변환해서 반환함
    }

    public void sendPasswordResetEmail(String to, String resetToken) {
        // 비밀번호 재설정 메일 발송 메서드 파라미터로 to와 resetToken을 String 타입으로 받고 리턴값은 없음
        // resetToken에는 비밀번호 재설정 요청을 대표하는 고유한 보유 문자열이 담겨 들어옴
        SimpleMailMessage message = new SimpleMailMessage();
        // SimpleMailMessage는 Spring FrameWork의 메일 지원 라이브러리고 Spring Mail 모듈의 클래스임
        // 간단한 텍스트 이메일을 구성하는 데 사용하고 HTML이나 첨부파일 없이 순수 텍스트 보낼 떄 사용함
        message.setFrom(fromEmail); // 발신자 이메일 주소를 설정함
        message.setTo(to); // 수신자 이메일 주소를 설정함
        message.setSubject("OTT 프로젝트 비밀번호 재설정"); // 메일 제목을 설정함
        String origin = System.getenv("FRONTEND_ORIGIN"); // env파일에 프론트엔드 서비스의 public 주소를 저장
        // System.getenv()는 JVM이 실행 중인 환경에서 설정된 시스템 환경 변수를 조회함
        // 여기서는 비밀번호 재설정 링크를 만들 때 먼저 프론트엔드 서비스의 pbulic 주소(FRONTEND_ORIGIN)을 조회하고
        // 값이 있으면 오리진 주소를 쓰고 없으면 다른값으로 대체함
        if (origin == null || origin.isBlank()) {
            // 만약 origin이 null이거나 빈 문자열이면
            origin = System.getenv("BACKEND_PUBLIC_ORIGIN");
            // BACKEND_PUBLIC_ORIGIN 환경 변수에 저장된 백엔드 서비스의 public 주소를 조회하고 할당함
        }
        // origin에 값이 프론트 오리진, 백엔드 오리진, 아니면 아예 값이 없는 상태로 실행
        String resetBase = (origin != null && !origin.isBlank()) ? origin : ("http://" + "127.0.0.1:8090");
        // origin에 값이 null이 아니고 origin이 빈문자열이 아니면, 즉 값이 있으면
        // 프론트나 백엔드 오리진값이면 그대로 쓰고 아예 값이 없는 상태면 127.0.0.1:8090 주소를 씀
        message.setText("비밀번호를 재설정하려면 다음 링크를 클릭하세요:\n\n"
                + resetBase.replaceAll("/+$", "") + "/api/auth/reset-password?token=" + resetToken + "\n\n"
                + "이 링크는 1시간 동안 유효합니다.");
                // repleaceAll("/+$", "")는 정규식을 이용해 URL 끝에 붙어있는 슬래시를 모두 잘라내는 코드임
                // /+$ 정규식 뜻은 문자열 끝($)에서 하나 이상의 슬래시(/+)를 찾는 패턴
                // 이걸 인자로 태워보내면 찾은 슬래쉬들을 빈 문자열로 대체함 즉 제거하는것
                // 그다음 URL 끝에 resetToken을 파라미터로 추가하고 1시간동안 유효하다는걸 알림
        mailSender.send(message); // SMTP 서버를 통해 이메일 발송함
    }
}
