package com.ottproject.ottbackend.security;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * SessionAuthenticationFilter
 *
 * 큰 흐름
 * - HttpSession 에 저장된 userEmail 로 사용자를 조회하고,
 *   Spring Security 컨텍스트에 인증 정보를 설정한다.
 * - 이렇게 하면 세션 기반 로그인 후 보호된 엔드포인트도 Security 에서 인증된 것으로 인식한다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 이미 인증된 경우는 통과
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object emailObj = session.getAttribute("userEmail");
                if (emailObj instanceof String email && !email.isEmpty()) {
                    User user = userRepository.findByEmail(email).orElse(null);
                    if (user != null) {
                        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                email, null, Collections.singletonList(authority)
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}


