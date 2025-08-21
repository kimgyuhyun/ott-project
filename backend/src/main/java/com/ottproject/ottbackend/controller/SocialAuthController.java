package com.ottproject.ottbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * SocialAuthController
 *
 * 큰 흐름
 * - OAuth2 소셜 로그인 상태/로그인 URL/사용자 정보/로그아웃을 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/oauth2/status: 인증 상태 + 로그인 URL 안내
 * - GET /api/oauth2/login-urls: 로그인 URL 목록
 * - GET /api/oauth2/user-info: 현재 사용자 정보
 * - POST /api/oauth2/logout: 로그아웃(컨텍스트 초기화)
 */
@Slf4j // Lombok 로깅 어노테이션
@RestController // REST API 컨트롤러
@RequestMapping("/api/oauth2") // 기본 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
@CrossOrigin(origins = "*") // CORS 설정 (개발용)
public class SocialAuthController {

    /**
     * 소셜 로그인 상태 확인 API
     * 현재 사용자가 소셜 로그인으로 인증되었는지 확인
     *
     * @return 로그인 상태 정보
     */
    @Operation(summary = "OAuth2 상태", description = "소셜 로그인 인증 상태와 로그인 URL을 제공합니다.")
    @ApiResponse(responseCode = "200", description = "정상")
    @GetMapping("/status") // GET 요청 처리 - /api/oauth2/status 경로로 접근
    public ResponseEntity<Map<String, Object>> getOAuth2Status() { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("OAuth2 로그인 상태 확인 요청"); // 로그 출력 - 요청 시작을 알림

        // Spring Security 컨텍스트에서 현재 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 응답 데이터를 담을 Map 객체 생성
        Map<String, Object> response = new HashMap<>();

        // 인증 상태 확인 (로그인되어 있는지 체크)
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getName())) { // anonymousUser 는 로그인되지 않은 유저
            // 로그인된 사용자인 경우 - 성공 응답 데이터 설정
            response.put("authenticated", true); // 인증 상태를 true 로 설정
            response.put("username", authentication.getName()); // 사용자명 (이메일) 설정
            response.put("authorities", authentication.getAuthorities()); // 사용자 권한 정보 설정
            response.put("principal", authentication.getPrincipal()); // 인증 주체 정보 설정
            response.put("message", "소셜 로그인이 완료되었습니다."); // 성공 메시지 설정

            // OAuth2 사용자인 경우 추가 정보 설정 (소셜 로그인 사용자만)
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                // OAuth2User 로 캐스팅하여 소셜 로그인 사용자 정보 추출
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User =
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                response.put("oauth2User", true); // OAuth2 사용자임을 표시
                response.put("provider", extractProvider(oauth2User.getAttributes())); // 소셜 로그인 제공자 설정
            }
        } else {
            // 로그인되지 않은 사용자인 경우 - 실패 응답 데이터 설정
            response.put("authenticated", false); // 인증 상태를 false 로 설정
            response.put("message", "소셜 로그인이 필요합니다."); // 안내 메시지 설정
            response.put("loginUrls", Map.of( // 소셜 로그인 URL들을 Map 으로 설정
                    "google", "/oauth2/authorization/google", // Google 로그인 URL
                    "kakao", "/oauth2/authorization/kakao", // Kakao 로그인 URL
                    "naver", "/oauth2/authorization/naver" // Naver 로그인 URL
            ));
        }

        return ResponseEntity.ok(response); // 200 OK 상태코드와 함께 응답 데이터 반환
    }

    /**
     * 소셜 로그인 URL 제공 API
     * 각 소셜 로그인 제공자의 로그인 URL을 제공
     * 프론트엔드에서 소셜 로그인 버튼을 만들 때 사용
     *
     * @return 소셜 로그인 URL들 (JSON 형태)
     */
    @Operation(summary = "OAuth2 로그인 URL", description = "각 소셜 로그인 진입 URL을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "정상")
    @GetMapping("/login-urls") // GET 요청 처리 - /api/oauth2/login-urls 경로로 접근
    public ResponseEntity<Map<String, Object>> getOAuth2LoginUrls() { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("OAuth2 로그인 URL 요청"); // 로그 출력 - 요청 시작을 알림

        // 응답 데이터를 담을 Map 객체 생성
        Map<String, Object> response = new HashMap<>();

        // 소셜 로그인 URL들을 Map으로 설정
        response.put("loginUrls", Map.of(
                "google", "/oauth2/authorization/google", // Google 로그인 URL
                "kakao", "/oauth2/authorization/kakao", // Kakao 로그인 URL
                "naver", "/oauth2/authorization/naver" // Naver 로그인 URL
        ));

        return ResponseEntity.ok(response); // 200 OK 상태코드와 함께 응답 데이터 반환
    }

    /**
     * 현재 로그인된 사용자 정보 조회 API
     * 소셜 로그인으로 인증된 사용자의 상세 정보를 조회
     *
     * @return 사용자 정보 (JSON 형태)
     */
    @Operation(summary = "OAuth2 사용자 정보", description = "현재 인증된 소셜 사용자 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "정상")
    @GetMapping("/user-info") // GET 요청 처리 - /api/oauth2/user-info 경로로 접근
    public ResponseEntity<Map<String, Object>> getCurrentUserInfo() { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("현재 사용자 정보 조회 요청"); // 로그 출력 - 요청 시작을 알림

        // Spring Security 컨텍스트에서 현재 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 사용자 정보를 담을 Map 객체 생성
        Map<String, Object> userInfo = new HashMap<>();

        // 인증 상태 확인 (로그인되어 있는지 체크)
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getName())) { // anonymousUser 는 로그인되지 않은 유저
            // 로그인된 사용자 정보 설정
            userInfo.put("authenticated", true); // 인증 상태를 true 로 설정
            userInfo.put("username", authentication.getName()); // 사용자명 (이메일) 설정
            userInfo.put("authorities", authentication.getAuthorities()); // 사용자 권한 정보 설정
            userInfo.put("principal", authentication.getPrincipal()); // 인증 주체 정보 설정

            // OAuth2 사용자의 경우 추가 정보 설정 (소셜 로그인 사용자만)
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                // OAuth2User 로 캐스팅하여 소셜 로그인 사용자 정보 추출
                org.springframework.security.oauth2.core.user.OAuth2User oAuth2User =
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                userInfo.put("oauth2User", true); // OAuth2 사용자임을 표시
                userInfo.put("provider", extractProvider(oAuth2User.getAttributes())); // 소셜 로그인 제공자 설정
                userInfo.put("attributes", oAuth2User.getAttributes()); // OAuth2 속성 정보 설정 (소셜 로그인 제공자에서 받은 정보)
            }
        } else {
            // 로그인되지 않은 경우 - 실패 응답 데이터 설정
            userInfo.put("authenticated", false); // 인증 상태를 false 로 설정
            userInfo.put("message", "로그인이 필요합니다."); // 안내 메시지 설정
            return ResponseEntity.status(401).body(userInfo); // 401 Unauthorized 상태코드와 함께 응답 데이터 반환
        }

        return ResponseEntity.ok(userInfo); // 200 OK 상태코드와 함께 사용자 정보 반환
    }

    /**
     * 소셜 로그인 로그아웃 API
     * 현재 세션을 무효화하여 로그아웃 처리
     *
     * @return 로그아웃 결과 (JSON 형태)
     */
    @Operation(summary = "OAuth2 로그아웃", description = "현재 인증 컨텍스트를 초기화합니다.")
    @ApiResponse(responseCode = "200", description = "정상")
    @PostMapping("/logout") // POST 요청 처리 - /api/oauth2/logout 경로로 접근
    public ResponseEntity<Map<String, Object>> logout() { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("OAuth2 로그아웃 요청"); // 로그 출력 - 요청 시작을 알림

        // 현재 Spring Security 컨텍스트를 클리어하여 로그아웃 처리
        SecurityContextHolder.clearContext(); // 인증 정보 삭제

        // 로그아웃 결과를 담을 Map 객체 생성
        Map<String, Object> response = new HashMap<>();

        // 로그아웃 결과 데이터 설정
        response.put("success", true); // 로그아웃 성공 상태 설정
        response.put("message", "로그아웃이 완료되었습니다."); // 성공 메시지 설정

        return ResponseEntity.ok(response); // 200 OK 상태코드와 함께 로그아웃 결과 반환
    }

    /**
     * 소셜 로그인 제공자 추출
     * OAuth2User attributes 에서 어떤 소셜 로그인을 사용했는지 판단
     *
     * @param attributes OAuth2User 의 속성 정보
     * @return 소셜 로그인 제공자 (google, kakao, naver, unknown)
     */
    private String extractProvider(Map<String, Object> attributes) { // private 메서드 - 클래스 내부에서만 사용
        if (attributes.containsKey("sub")) return "google"; // Google은 sub 필드가 있음
        if (attributes.containsKey("kakao_account")) return "kakao"; // Kakao 는 kakao_account 필드가 있음
        if (attributes.containsKey("response")) return "naver"; // Naver 는 response 필드가 있음
        return "unknown"; // 알 수 없는 제공자
    }
}