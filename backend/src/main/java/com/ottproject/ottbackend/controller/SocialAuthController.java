package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
public class SocialAuthController {

    private final UserRepository userRepository; // 사용자 데이터베이스 접근

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
            // 로그인된 사용자인 경우 - 성공 응답 데이터 설정(닉네임은 DB에서)
            response.put("authenticated", true);
            response.put("authorities", authentication.getAuthorities());
            response.put("principal", authentication.getPrincipal());
            response.put("message", "소셜 로그인이 완료되었습니다.");

            String authEmail = authentication.getName();
            User dbUserOrNull = userRepository.findByEmail(authEmail).orElse(null);
            if (dbUserOrNull != null) {
                response.put("username", dbUserOrNull.getName());
                response.put("email", dbUserOrNull.getEmail());
                response.put("id", dbUserOrNull.getId());
            } else {
                response.put("username", authEmail);
                response.put("email", authEmail);
            }

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
                    "google", "/login/oauth2/authorization/google", // Google 로그인 URL
                    "kakao", "/login/oauth2/authorization/kakao", // Kakao 로그인 URL
                    "naver", "/login/oauth2/authorization/naver" // Naver 로그인 URL
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

        // 소셜 로그인 URL들을 상대 경로로 설정 (현업 표준)
        response.put("loginUrls", Map.of(
                "google", "/login/oauth2/authorization/google", // Google 로그인 URL
                "kakao", "/login/oauth2/authorization/kakao", // Kakao 로그인 URL
                "naver", "/login/oauth2/authorization/naver" // Naver 로그인 URL
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
            // 로그인된 사용자 정보 설정 (DB 닉네임을 최우선으로 반환)
            userInfo.put("authenticated", true);
            userInfo.put("authorities", authentication.getAuthorities());
            userInfo.put("principal", authentication.getPrincipal());

            String authEmail = authentication.getName();
            User dbUserOrNull = userRepository.findByEmail(authEmail).orElse(null);
            if (dbUserOrNull != null) {
                userInfo.put("username", dbUserOrNull.getName());
                userInfo.put("email", dbUserOrNull.getEmail());
                userInfo.put("id", dbUserOrNull.getId());
            } else {
                // fallback
                userInfo.put("username", authEmail);
                userInfo.put("email", authEmail);
            }

            // OAuth2 사용자의 경우 추가 정보 설정 (소셜 로그인 사용자만)
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                // OAuth2User 로 캐스팅하여 소셜 로그인 사용자 정보 추출
                org.springframework.security.oauth2.core.user.OAuth2User oAuth2User =
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                userInfo.put("oauth2User", true); // OAuth2 사용자임을 표시
                userInfo.put("provider", extractProvider(oAuth2User.getAttributes())); // 소셜 로그인 제공자 설정
                Map<String, Object> attrs = new HashMap<>(oAuth2User.getAttributes());
                // 세션에 존재하는 표시명(닉네임) 오버라이드가 있으면 반영
                try {
                    jakarta.servlet.http.HttpSession session =
                            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                                    .getRequest().getSession(false);
                    String displayOverride = session != null ? (String) session.getAttribute("displayNameOverride") : null;
                    if (displayOverride != null && !displayOverride.isBlank()) {
                        attrs.put("userName", displayOverride);
                        attrs.put("name", displayOverride);
                    } else {
                        // 세션 오버라이드가 없으면 DB의 현재 값을 우선 반영
                        Object userIdObj = attrs.get("userId");
                        if (userIdObj instanceof Number) {
                            Long uid = ((Number) userIdObj).longValue();
                            userRepository.findById(uid).ifPresent(u -> {
                                if (u.getName() != null && !u.getName().isBlank()) {
                                    attrs.put("userName", u.getName());
                                    attrs.put("name", u.getName());
                                }
                            });
                        } else if (dbUserOrNull != null && dbUserOrNull.getName() != null && !dbUserOrNull.getName().isBlank()) {
                            attrs.put("userName", dbUserOrNull.getName());
                            attrs.put("name", dbUserOrNull.getName());
                        }
                    }
                } catch (Exception ignore) { }
                userInfo.put("attributes", attrs); // OAuth2 속성 정보 설정 (소셜 로그인 제공자에서 받은 정보)

                // 신규 사용자 플래그를 attributes 에 보강 (세션/속성 어디서든 읽을 수 있게)
                boolean isNewByAttr = Boolean.TRUE.equals(oAuth2User.getAttribute("isNewUser"));
                try {
                    jakarta.servlet.http.HttpSession session =
                            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                                    .getRequest().getSession(false);
                    Boolean isNewBySession = session != null ? (Boolean) session.getAttribute("isNewUser") : null;
                    userInfo.put("isNewUser", isNewBySession != null ? isNewBySession : isNewByAttr);
                } catch (Exception e) {
                    userInfo.put("isNewUser", isNewByAttr);
                }
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
    public ResponseEntity<Map<String, Object>> logout(jakarta.servlet.http.HttpServletRequest request) { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("OAuth2 로그아웃 요청"); // 로그 출력 - 요청 시작을 알림

        // 세션 무효화 및 시큐리티 컨텍스트 초기화
        try {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate(); // 세션 무효화
            }
        } catch (Exception ignore) { }
        SecurityContextHolder.clearContext(); // 인증 정보 삭제

        // 로그아웃 결과를 담을 Map 객체 생성
        Map<String, Object> response = new HashMap<>();

        // 로그아웃 결과 데이터 설정
        response.put("success", true); // 로그아웃 성공 상태 설정
        response.put("message", "로그아웃이 완료되었습니다."); // 성공 메시지 설정

        return ResponseEntity.ok(response); // 200 OK 상태코드와 함께 로그아웃 결과 반환
    }

    /**
     * 닉네임 업데이트 API
     * 현재 로그인된 사용자의 닉네임을 업데이트
     *
     * @param nicknameRequest 닉네임 업데이트 요청 객체
     * @return 업데이트 결과 (JSON 형태)
     */
    @Operation(summary = "닉네임 업데이트", description = "현재 인증된 사용자의 닉네임을 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "정상")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @PutMapping("/nickname") // PUT 요청 처리 - /api/oauth2/nickname 경로로 접근
    @Transactional // 트랜잭션 처리
    public ResponseEntity<Map<String, Object>> updateNickname(@RequestBody Map<String, String> nicknameRequest) { // HTTP 응답을 위한 ResponseEntity 반환
        log.info("닉네임 업데이트 요청 수신"); // 로그 출력 - 요청 시작을 알림

        // Spring Security 컨텍스트에서 현재 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 응답 데이터를 담을 Map 객체 생성
        Map<String, Object> response = new HashMap<>();

        // 인증 상태 확인 (로그인되어 있는지 체크)
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getName())) { // anonymousUser 는 로그인되지 않은 유저
            response.put("success", false); // 실패 상태 설정
            response.put("message", "로그인이 필요합니다."); // 안내 메시지 설정
            return ResponseEntity.status(401).body(response); // 401 Unauthorized 상태코드와 함께 응답 데이터 반환
        }

        // 닉네임 추출 및 검증
        String newNickname = nicknameRequest.get("nickname");
        if (newNickname == null || newNickname.trim().isEmpty()) {
            response.put("success", false); // 실패 상태 설정
            response.put("message", "닉네임을 입력해주세요."); // 안내 메시지 설정
            return ResponseEntity.badRequest().body(response); // 400 Bad Request 상태코드와 함께 응답 데이터 반환
        }

        newNickname = newNickname.trim(); // 공백 제거
        
        // 닉네임 길이 검증 (2-20자)
        if (newNickname.length() < 2 || newNickname.length() > 20) {
            response.put("success", false); // 실패 상태 설정
            response.put("message", "닉네임은 2자 이상 20자 이하로 입력해주세요."); // 안내 메시지 설정
            return ResponseEntity.badRequest().body(response); // 400 Bad Request 상태코드와 함께 응답 데이터 반환
        }

        try {
            // OAuth2 사용자 정보에서 userId 추출
            Long userId = null;
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                // OAuth2User 로 캐스팅하여 소셜 로그인 사용자 정보 추출
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User =
                        (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();
                userId = (Long) oauth2User.getAttributes().get("userId"); // 사용자 ID 추출
            }

            if (userId == null) {
                response.put("success", false); // 실패 상태 설정
                response.put("message", "사용자 정보를 찾을 수 없습니다."); // 안내 메시지 설정
                return ResponseEntity.badRequest().body(response); // 400 Bad Request 상태코드와 함께 응답 데이터 반환
            }

            // 사용자 조회 및 닉네임 업데이트
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                response.put("success", false); // 실패 상태 설정
                response.put("message", "사용자를 찾을 수 없습니다."); // 안내 메시지 설정
                return ResponseEntity.badRequest().body(response); // 400 Bad Request 상태코드와 함께 응답 데이터 반환
            }

            // 닉네임 업데이트
            String oldNickname = user.getName();
            user.setName(newNickname);
            userRepository.save(user);

            // 세션/시큐리티 컨텍스트의 OAuth2 attributes도 최신화(프론트 즉시 반영)
            try {
                if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                    // 1) 세션 플래그
                    // mutable map 이 아닐 수 있어 세션 플래그로 알리고, user-info 에서 name 을 DB에서 읽게 함
                    jakarta.servlet.http.HttpSession session =
                            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                                    .getRequest().getSession(false);
                    if (session != null) {
                        session.setAttribute("displayNameOverride", newNickname);
                        session.setAttribute("isNewUser", Boolean.FALSE);
                    }

                    // 2) SecurityContext 의 Principal 을 교체하여 즉시 반영
                    if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oAuth2Token) {
                        org.springframework.security.oauth2.core.user.OAuth2User principal = oAuth2Token.getPrincipal();
                        java.util.Map<String, Object> newAttrs = new java.util.HashMap<>(principal.getAttributes());
                        newAttrs.put("userName", newNickname);
                        newAttrs.put("name", newNickname);
                        org.springframework.security.oauth2.core.user.DefaultOAuth2User newPrincipal =
                                new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                                        oAuth2Token.getAuthorities(), newAttrs, "userEmail");
                        org.springframework.security.core.Authentication refreshed =
                                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                                        newPrincipal, oAuth2Token.getAuthorities(), oAuth2Token.getAuthorizedClientRegistrationId());
                        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(refreshed);
                    }
                }
            } catch (Exception ignore) { }

            log.info("닉네임 업데이트 완료 - 사용자ID: {}, 기존: {}, 신규: {}", userId, oldNickname, newNickname);

            // 성공 응답 데이터 설정
            response.put("success", true); // 성공 상태 설정
            response.put("message", "닉네임이 성공적으로 업데이트되었습니다."); // 성공 메시지 설정
            response.put("newNickname", newNickname); // 새로운 닉네임 정보 포함

            return ResponseEntity.ok(response); // 200 OK 상태코드와 함께 성공 응답 반환

        } catch (Exception e) {
            log.error("닉네임 업데이트 중 오류 발생: {}", e.getMessage(), e);
            response.put("success", false); // 실패 상태 설정
            response.put("message", "닉네임 업데이트 중 오류가 발생했습니다."); // 오류 메시지 설정
            return ResponseEntity.internalServerError().body(response); // 500 Internal Server Error 상태코드와 함께 오류 응답 반환
        }
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