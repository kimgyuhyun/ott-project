package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AuthLoginRequestDto;
import com.ottproject.ottbackend.dto.ChangePasswordRequestDto;
import com.ottproject.ottbackend.dto.AuthRegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.service.EmailAuthService;
import com.ottproject.ottbackend.service.VerificationEmailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * EmailAuthController
 *
 * 큰 흐름
 * - 이메일 기반 회원가입/중복확인/로그인/로그아웃/비밀번호 변경/탈퇴와 이메일 인증(코드 발송/검증)을 제공한다.
 *
 * 엔드포인트 개요
 * - POST /api/auth/register: 회원가입
 * - GET /api/auth/check-email: 이메일 중복 확인
 * - GET /api/auth/health: 헬스체크
 * - POST /api/auth/login: 로그인(세션)
 * - POST /api/auth/logout: 로그아웃(세션 무효화)
 * - POST /api/auth/send-verification-code: 인증 코드 발송
 * - POST /api/auth/verify-code: 인증 코드 검증
 * - DELETE /api/auth/withdraw: 회원탈퇴(세션 사용자)
 * - PUT /api/auth/change-password: 비밀번호 변경(세션 사용자)
 */
@RestController // REST API 컨트롤러로 지정, @ResponseBody 를 모든 메서드에 자동 적용
@RequestMapping("/api/auth") // 모든 엔드포인트의 기본 경로를 /api/auth 로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class EmailAuthController {
    private final EmailAuthService emailAuthService; // 인증 서비스 주입 (회원가입, 로그인 등 비즈니스 로직 처리
    private final VerificationEmailService verificationEmailService; // 이메일 서비스 주입 (이메일 발송 처리)

    // 회원가입 API 엔드포인트 - POST /api/auth/register
    @Operation(summary = "회원가입", description = "이메일/비밀번호/프로필 정보로 신규 계정을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody AuthRegisterRequestDto requestDto) {
        //@Valid 로 입력 데이터 유효성 검증 (이메일 형식, 비밀번호 길이 등)
        //@RequestBody 로 JSON 형태의 요청 데이터를 DTO 객체로 변환
        UserResponseDto responseDto = emailAuthService.register(requestDto); // EmailAuthService 를 통해 회원가입 처리
        return ResponseEntity.ok(responseDto); // 200 OK 상태코드와 함께 생성된 사용자 정보 반환(비밀번호 제외)
    }

    // 이메일 중복 확인 API 엔드포인트 - GET /api/auth/check-email?email=test@example.com
    @Operation(summary = "이메일 중복 확인", description = "주어진 이메일이 사용 중인지 여부 반환")
    @ApiResponse(responseCode = "200", description = "중복 여부 반환")
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmailDuplicate(@Parameter(description = "이메일") @RequestParam String email) {
        //RequestParam 으로 URL 파라미터로 전달된 이메일을 받음
        boolean isDuplicate = emailAuthService.checkEmailDuplicate(email); // 이메일 중복 확인 처리
        return ResponseEntity.ok(isDuplicate); // true: 중복된 이메일, false: 사용 가능한 이메일
    }

    // API 상태 확인용 헬스체크
    @Operation(summary = "헬스체크", description = "인증 API 상태 확인")
    @ApiResponse(responseCode = "200", description = "정상")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth API is running!"); // API 서버가 정상 동작 중임을 알리는 메시지 반환
    }

    // 로그인 API 엔드포인트 - POST /api/auth/login
    @Operation(summary = "로그인", description = "세션 기반 로그인 수행")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = UserResponseDto.class)))
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(@Valid @RequestBody AuthLoginRequestDto requestDto, HttpSession session) {
        //@Valid 로 입력 데이터 유효성 검증 (이메일 형식, 필수 필드 등)
        //@RequestBody 로 JSON 형태의 로그인 정보를 AuthLoginRequestDto 객체로 변환
        // AuthLoginRequestDto: { "email": "test@example.com", "password": "123456" }
        UserResponseDto responseDto = emailAuthService.login(requestDto.getEmail(), requestDto.getPassword()); // emailAuthService 를 통해 로그인 처리

        // 로그인 성공 시 세션에 사용자 이메일 저장 (회원탈퇴, 비밀번호 변경 시 사용)
        session.setAttribute("userEmail", requestDto.getEmail()); // 세션에 사용자 이메일 저장

        return ResponseEntity.ok(responseDto); // 200 OK 상태 코드와 함께 로그인된 사용자 정보를 반환
    }

    // 로그아웃 API 엔드포인트 - POST /api/auth/logout
    @Operation(summary = "로그아웃", description = "세션 무효화")
    @ApiResponse(responseCode = "200", description = "성공")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        // HttpSession 객체를 통해 현재 사용자의 세션 정보에 접근
        session.invalidate(); // 세션 무효화 (로그아웃 처리 - 세션 데이터 삭제)
        return ResponseEntity.ok("로그아웃되었습니다."); // 200 OK 상태코드와 함께 로그아웃 성공 메시지 반환
    }


    // 이메일 인증 코드 발송 API - POST /api/auth/send-verification-code
    @Operation(summary = "이메일 인증코드 발송", description = "입력 이메일로 인증코드 전송")
    @ApiResponse(responseCode = "200", description = "발송됨")
    @PostMapping("/send-verification-code")
    public ResponseEntity<String> sendVerificationCode(@Parameter(description = "이메일") @RequestParam String email) {
        // @RequestParam 으로 URL 파라미터로 전달된 이메일을 받음
        verificationEmailService.sendVerificationEmail(email); // VerificationEmailService 를 통해 인증 코드 이메일 발송
        return ResponseEntity.ok("인증 코드가 발송되었습니다. 이메일을 확인해주세요."); // 200 OK 상태코드와 함께 발송 완료 메시지 반환
    }

    // 이메일 인증 코드 확인 API - POST /api/auth/verify-code
    @Operation(summary = "인증코드 검증", description = "이메일/코드로 인증 여부 확인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "실패")
    })
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@Parameter(description = "이메일") @RequestParam String email,
                                             @Parameter(description = "인증코드") @RequestParam String code) {
        // @RequestParam 으로 URL 파라미터로 전달된 이메일과 인증 코드를 받음
        boolean isVerified = verificationEmailService.verifyCode(email, code); // VerificationEmailService 를 통해 인증 코드 확인
        if (isVerified) {
            return ResponseEntity.ok("이메일 인증이 완료되었습니다."); // 인증 성공 시 200 OK
        } else {
            return ResponseEntity.badRequest().body("인증 코드가 올바르지 않습니다."); // 인증 실패 시 400 Bad Request
        }
    }

    // 회원탈퇴 API 엔드포인트 - DELETE /api/auth/withdraw
    @Operation(summary = "회원탈퇴", description = "세션 사용자 탈퇴")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 완료"),
            @ApiResponse(responseCode = "400", description = "로그인 필요")
    })
    @DeleteMapping("/withdraw")
    public ResponseEntity<String> withdraw(HttpSession session) {
        // HttpSession 객체를 통해 현재 사용자의 세션 정보에 접근
        String userEmail = (String) session.getAttribute("userEmail"); // 세션에서 사용자 이메일 가져오기
        if (userEmail == null) {
            return ResponseEntity.badRequest().body("로그인이 필요합니다."); // 로그인되지 않은 경우 400 bad Request
        }
        emailAuthService.withdraw(userEmail); // emailAuthService 를 통해 회원탈퇴 처리
        session.invalidate(); // 세션 무효화 (로그아웃 처리)
        return ResponseEntity.ok("회원탈퇴가 완료되었습니다."); // 200 OK 상태코드와 함께 탈퇴 완료 메시지 반환
    }

    // 비밀번호 변경 API 엔드포인트 - PUT /api/auth/change-password
    @Operation(summary = "비밀번호 변경", description = "세션 사용자 비밀번호 변경")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 완료"),
            @ApiResponse(responseCode = "400", description = "로그인 필요")
    })
    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(HttpSession session, @RequestBody ChangePasswordRequestDto requestDto) {
        //@RequestBody 로 JSON 형태의 비밀번호 변경 요청 데이터를 DTO 객체로 변환
        String userEmail = (String) session.getAttribute("userEmail"); // 세션에서 사용자 이메일 가져오기
        if (userEmail == null) {
            return ResponseEntity.badRequest().body("로그인이 필요합니다."); // 로그인되지 않은 경우 400 bad Request
        }

        emailAuthService.changePassword(userEmail, requestDto.getCurrentPassword(), requestDto.getNewPassword()); // EmailAuthService 를 통해 비밀번호 변경 처리
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다."); // 200 ok 상태코드와 함께 변경 완료
    }

}
