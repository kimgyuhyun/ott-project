package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.ChangePasswordRequestDto;
import com.ottproject.ottbackend.dto.LoginRequestDto;
import com.ottproject.ottbackend.dto.RegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.service.AuthService;
import com.ottproject.ottbackend.service.EmailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인증/계정 컨트롤러
 * - 회원가입/중복확인/로그인/로그아웃/비밀번호변경/탈퇴
 * - 이메일 인증 코드 발송/검증
 */
@RestController // REST API 컨트롤러로 지정, @ResponseBody 를 모든 메서드에 자동 적용
@RequestMapping("/api/auth") // 모든 엔드포인트의 기본 경로를 /api/auth 로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
@CrossOrigin(origins = "*") // CORS 설정 (개발용) - 모든 도메인에서의 요청 허용
public class AuthController {
    private final AuthService authService; // 인증 서비스 주입 (회원가입, 로그인 등 비즈니스 로직 처리
    private final EmailService emailService; // 이메일 서비스 주입 (이메일 발송 처리)

    // 회원가입 API 엔드포인트 - POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto) {
        //@Valid 로 입력 데이터 유효성 검증 (이메일 형식, 비밀번호 길이 등)
        //@RequestBody 로 JSON 형태의 요청 데이터를 DTO 객체로 변환
        UserResponseDto responseDto = authService.register(requestDto); // AuthService 를 통해 회원가입 처리
        return ResponseEntity.ok(responseDto); // 200 OK 상태코드와 함께 생성된 사용자 정보 반환(비밀번호 제외)
    }

    // 이메일 중복 확인 API 엔드포인트 - GET /api/auth/check-email?email=test@example.com
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmailDuplicate(@RequestParam String email) {
        //RequestParam 으로 URL 파라미터로 전달된 이메일을 받음
        boolean isDuplicate = authService.checkEmailDuplicate(email); // 이메일 중복 확인 처리
        return ResponseEntity.ok(isDuplicate); // true: 중복된 이메일, false: 사용 가능한 이메일
    }

    // API 상태 확인용 헬스체크
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth API is running!"); // API 서버가 정상 동작 중임을 알리는 메시지 반환
    }

    // 로그인 API 엔드포인트 - POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto, HttpSession session) {
        //@Valid 로 입력 데이터 유효성 검증 (이메일 형식, 필수 필드 등)
        //@RequestBody 로 JSON 형태의 로그인 정보를 LoginRequestDto 객체로 변환
        // LoginRequestDto: { "email": "test@example.com", "password": "123456" }
        UserResponseDto responseDto = authService.login(requestDto.getEmail(), requestDto.getPassword()); // authService 를 통해 로그인 처리

        // 로그인 성공 시 세션에 사용자 이메일 저장 (회원탈퇴, 비밀번호 변경 시 사용)
        session.setAttribute("userEmail", requestDto.getEmail()); // 세션에 사용자 이메일 저장

        return ResponseEntity.ok(responseDto); // 200 OK 상태 코드와 함께 로그인된 사용자 정보를 반환
    }

    // 로그아웃 API 엔드포인트 - POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        // HttpSession 객체를 통해 현재 사용자의 세션 정보에 접근
        session.invalidate(); // 세션 무효화 (로그아웃 처리 - 세션 데이터 삭제)
        return ResponseEntity.ok("로그아웃되었습니다."); // 200 OK 상태코드와 함께 로그아웃 성공 메시지 반환
    }


    // 이메일 인증 코드 발송 API - POST /api/auth/send-verification-code
    @PostMapping("/send-verification-code")
    public ResponseEntity<String> sendVerificationCode(@RequestParam String email) {
        // @RequestParam 으로 URL 파라미터로 전달된 이메일을 받음
        emailService.sendVerificationEmail(email); // EmailService 를 통해 인증 코드 이메일 발송
        return ResponseEntity.ok("인증 코드가 발송되었습니다. 이메일을 확인해주세요."); // 200 OK 상태코드와 함께 발송 완료 메시지 반환
    }

    // 이메일 인증 코드 확인 API - POST /api/auth/verify-code
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestParam String email, @RequestParam String code) {
        // @RequestParam 으로 URL 파라미터로 전달된 이메일과 인증 코드를 받음
        boolean isVerified = emailService.verifyCode(email, code); // EmailService 를 통해 인증 코드 확인
        if (isVerified) {
            return ResponseEntity.ok("이메일 인증이 완료되었습니다."); // 인증 성공 시 200 OK
        } else {
            return ResponseEntity.badRequest().body("인증 코드가 올바르지 않습니다."); // 인증 실패 시 400 Bad Request
        }
    }

    // 회원탈퇴 API 엔드포인트 - DELETE /api/auth/withdraw
    @DeleteMapping("/withdraw")
    public ResponseEntity<String> withdraw(HttpSession session) {
        // HttpSession 객체를 통해 현재 사용자의 세션 정보에 접근
        String userEmail = (String) session.getAttribute("userEmail"); // 세션에서 사용자 이메일 가져오기
        if (userEmail == null) {
            return ResponseEntity.badRequest().body("로그인이 필요합니다."); // 로그인되지 않은 경우 400 bad Request
        }
        authService.withdraw(userEmail); // authService 를 통해 회원탈퇴 처리
        session.invalidate(); // 세션 무효화 (로그아웃 처리)
        return ResponseEntity.ok("회원탈퇴가 완료되었습니다."); // 200 OK 상태코드와 함께 탈퇴 완료 메시지 반환
    }

    // 비밀번호 변경 API 엔드포인트 - PUT /api/auth/change-password
    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(HttpSession session, @RequestBody ChangePasswordRequestDto requestDto) {
        //@RequestBody 로 JSON 형태의 비밀번호 변경 요청 데이터를 DTO 객체로 변환
        String userEmail = (String) session.getAttribute("userEmail"); // 세션에서 사용자 이메일 가져오기
        if (userEmail == null) {
            return ResponseEntity.badRequest().body("로그인이 필요합니다."); // 로그인되지 않은 경우 400 bad Request
        }

        authService.changePassword(userEmail, requestDto.getCurrentPassword(), requestDto.getNewPassword()); // AuthService 를 통해 비밀번호 변경 처리
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다."); // 200 ok 상태코드와 함께 변경 완료
    }

}
