package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * LocalUserDetailsService
 *
 * 큰 흐름
 * - 이메일을 기준으로 사용자를 조회하여 Spring Security `UserDetails`로 변환한다.
 * - 소셜 로그인 사용자는 비밀번호 검증을 우회하도록 구성한다.
 *
 * 메서드 개요
 * - loadUserByUsername: 이메일로 사용자 조회 후 `UserDetails` 생성
 */
@Service // spring Bean 으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class LocalUserDetailsService implements UserDetailsService { // spring security 사용자 조회 서비스 구현

	private final UserRepository userRepository; // 사용자 데이터베이스 접근 Repository 주입

	@Override // UserDetailsService 인터페이스 메서드 재정의
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

		boolean isSocialUser = user.getAuthProvider() != AuthProvider.LOCAL; // LOCAL 이 아닌 경우 소셜 로그인 사용자

		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getEmail()) // 사용자명
				.password(isSocialUser ? "{noop}" + (user.getPassword() != null ? user.getPassword() : "") : user.getPassword()) // 소셜이면 비번검증 skip
				.disabled(!user.isEnabled())
				.accountExpired(false)
				.credentialsExpired(false)
				.accountLocked(false)
				.authorities(java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
				.build();

	}
}
