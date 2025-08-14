package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.UserSettingsDto;
import com.ottproject.ottbackend.service.SettingsService;
import com.ottproject.ottbackend.util.AuthUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 재생 환경 설정 API
 * - 현재 사용자 설정 조회/갱신
 */
@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
public class SettingsController { // 사용자 재생 설정
	private final SettingsService service; private final AuthUtil authUtil; // 의존성

	/**
	 * 현재 사용자 재생 설정 조회
	 */
	@GetMapping("/api/users/me/settings")
	public ResponseEntity<UserSettingsDto> get(HttpSession session) { // 세션 입력
		Long userId = authUtil.requireCurrentUserId(session); // 사용자 확인(401 가능)
		return ResponseEntity.ok(service.get(userId)); // 설정 반환
	}
	/**
	 * 현재 사용자 재생 설정 갱신
	 */
	@PutMapping("/api/users/me/settings")
	public ResponseEntity<Void> put(@RequestBody UserSettingsDto dto, HttpSession session) { // 바디 입력
		Long userId = authUtil.requireCurrentUserId(session); // 사용자 확인
		service.update(userId, dto); // 부분 갱신 처리
		return ResponseEntity.noContent().build(); // 204 No Content
	}
}
