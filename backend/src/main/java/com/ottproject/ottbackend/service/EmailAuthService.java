package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AuthRegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EmailAuthService
 *
 * 큰 흐름
 * - 이메일 기반 회원가입/로그인/중복확인/탈퇴/비밀번호 변경을 처리한다.
 *
 * 메서드 개요
 * - register: 회원가입 처리
 * - login: 로그인 검증 후 사용자 반환
 * - checkEmailDuplicate: 이메일 중복 확인
 * - withdraw: 계정 비활성화(탈퇴)
 * - changePassword: 비밀번호 변경
 */
@Service // spring bean 으로 등록, 싱글턴 패턴
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
@Transactional // 클래스 레벨 트랜잭션 관리
public class EmailAuthService {

	private final UserService userService; // 사용자 서비스 주입
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder; // 비밀번호 암호화 주입
	private final com.ottproject.ottbackend.mappers.UserMapper userMapper; // 사용자 매퍼 주입

	// 회원가입 처리
	public UserResponseDto register(AuthRegisterRequestDto requestDto) { // 회원가입 요청 처리
		if (userService.existsByEmail(requestDto.getEmail())) { // 이미 가입된 이메일인지 확인
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."); // 중복 시 409 반환
		}
		User user = User.builder() // 사용자 생성
				.email(requestDto.getEmail())
				.password(requestDto.getPassword()) // 암호화는 UserService 에서 처리
				.name(requestDto.getName())
				.authProvider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.enabled(true)
				.build();

		User saveUser = userService.saveUser(user); // 저장
		return userMapper.toUserResponseDto(saveUser); // DTO 변환
	}

	// 로그인 처리
	public UserResponseDto login(String email, String password) {
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다."));
		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		if (!user.isEnabled()) {
			throw new RuntimeException("비활성화된 계정입니다.");
		}
		return userMapper.toUserResponseDto(user);
	}

	// 이메일 중복 확인
	public boolean checkEmailDuplicate(String email) { // 이메일 중복 확인
		return userService.existsByEmail(email);
	}

	// 회원탈퇴 처리
	public void withdraw(String email) {
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
		user.setEnabled(false);
		userService.saveUser(user);
	}

	// 비밀번호 변경 처리
	public void changePassword(String email, String currentPassword, String newPassword) {
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
		if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
			throw new RuntimeException("현재 비밀번호가 올바르지 않습니다.");
		}
		String encodeNewPassword = passwordEncoder.encode(newPassword); // 새 비밀번호 암호화
		user.setPassword(encodeNewPassword);
		userService.saveUser(user);
	}
}
