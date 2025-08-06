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

import java.util.Collections;

@Service // spring Bean 으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class CustomUserDetailsService  implements UserDetailsService { // spring security 사용자 조회 서비스 구현

    private final UserRepository userRepository; // 사용자 데이버테이스 접근 Repository 주입

    @Override // UserDetailService 인터페이스 메드 재정의
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 이메일로 사용자 조회 (spring Security 는 username 으로 이메일을 전달)
        User user = userRepository.findByEmail(email) // DB 에서 이메일로 사용자 검색
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        // 소셜 로그인 사용자인지 확인 (소셜 로그인 사용자는 비밀번호가 null)
        boolean isSocialUser = user.getAuthProvider() != AuthProvider.LOCAL; // LOCAL이 아닌 경우 소셜 로그인 사용자

        // UserDetail 객체 생성 및 반환 (빌더 패턴 사용)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail()) // spring security 에서 사용할 사용자명 (이메일 사용)
                .password(isSocialUser ? "{noop}" + (user.getPassword() != null ? user.getPassword() : "") : user.getPassword()) // 소셜 로그인 사용자는 {noop} 접두사로 비밀번호 검증 건너뛰기, 자체 로그인 사용자는 암호화된 비밀번호 사용
                .disabled(!user.isEnabled()) // 계정 비활성화 여부 (User 엔티티의 enabled 의 반대값)
                .accountExpired(false) // 계정 만료 여부 (false:만료되지 않음)
                .credentialsExpired(false) // 자격 증명 만료 여부 (false:만료되지 않음)
                .accountLocked(false) // 계정 잠금 여부 (false: 잠금되지 않음)
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))) // 사용자 권한 설정
                .build(); // UserDetail 객체 생성 완료

    }
}
