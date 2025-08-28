package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.UserSettingsDto;
import com.ottproject.ottbackend.service.SettingsService;
import com.ottproject.ottbackend.util.SecurityUtil;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SettingsController
 *
 * 큰 흐름
 * - 사용자 재생 환경 설정의 조회/갱신을 제공한다.
 *
 * 엔드포인트 개요
 * - GET /api/users/me/settings: 내 설정 조회
 * - PUT /api/users/me/settings: 내 설정 갱신(부분 갱신)
 */
@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
@org.springframework.web.bind.annotation.RequestMapping("/api/users/me")
public class SettingsController { // 사용자 설정 관리
	private final SettingsService service; // 사용자 재생 설정 조회/갱신 서비스
	private final SecurityUtil securityUtil; // 세션 사용자 식별 유틸
	private final UserRepository userRepository; // 사용자 정보 조회용

	/**
	 * 현재 사용자 재생 설정 조회
	 */
    @Operation(summary = "내 설정 조회", description = "현재 로그인 사용자의 재생 환경 설정을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/settings")
	public ResponseEntity<UserSettingsDto> get(HttpSession session) { // 세션 입력
		Long userId = securityUtil.requireCurrentUserId(session); // 사용자 확인(401 가능)
		return ResponseEntity.ok(service.get(userId)); // 설정 반환
	}
	/**
	 * 현재 사용자 재생 설정 갱신
	 */
    @Operation(summary = "내 설정 갱신", description = "현재 로그인 사용자의 재생 환경 설정을 갱신합니다.")
    @ApiResponse(responseCode = "204", description = "갱신 완료")
    @PutMapping("/settings")
	public ResponseEntity<Void> put(@RequestBody UserSettingsDto dto, HttpSession session) { // 바디 입력
		Long userId = securityUtil.requireCurrentUserId(session); // 사용자 확인
		service.update(userId, dto); // 부분 갱신 처리
		return ResponseEntity.noContent().build(); // 204 No Content
	}

	/**
	 * 현재 사용자 프로필 조회
	 */
    @Operation(summary = "내 프로필 조회", description = "현재 로그인 사용자의 프로필 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/profile")
	public ResponseEntity<Map<String, Object>> getProfile(HttpSession session) { // 세션 입력
		Long userId = securityUtil.requireCurrentUserId(session); // 사용자 확인(401 가능)
		
		// 사용자 정보 조회
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		User user = userOpt.get();
		Map<String, Object> profile = new HashMap<>();
		profile.put("id", user.getId());
		profile.put("email", user.getEmail());
		profile.put("name", user.getName());
		// 프론트 호환: username 별칭도 함께 제공
		profile.put("username", user.getName());
		profile.put("role", user.getRole().name());
		profile.put("authProvider", user.getAuthProvider().name());
		profile.put("emailVerified", user.isEmailVerified());
		profile.put("enabled", user.isEnabled());
		profile.put("createdAt", user.getCreatedAt());

		return ResponseEntity.ok(profile);
	}
}
