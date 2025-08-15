package com.ottproject.ottbackend.util;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증/세션 유틸리티
 * - 세션의 이메일로 현재 사용자 식별(ID 반환)
 * - 선택 로그인 시 사용자 ID 혹은 null 반환
 */
@Component // 스프링 빈 등록
@RequiredArgsConstructor // 생성자 주입
public class AuthUtil { // 인증/세션 유틸리티

    private final UserRepository userRepository; // 사용자 조회 리포지토리

    /**
     * 로그인 필수: 현재 사용자 ID 반환
     * @param session HTTP 세션(이메일 보관)
     * @return 현재 사용자 ID
     * @throws ResponseStatusException 401 미로그인/무효 사용자
     */
    public Long requireCurrentUserId(HttpSession session) { // 로그인 필수: 현재 사용자 ID 반환
        String email = (session != null) ? (String) session.getAttribute("userEmail") : null; // 세션에서 이메일 조회
        if (email == null || email.isEmpty()) { // 미로그인 또는 값 없음
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."); // 401 응답
        }
        User user = userRepository.findByEmail(email).orElseThrow( // 이메일로 사용자 조회
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다.") // 없으면 401
        );
        return user.getId(); // 사용자 ID 반환
    }

    /**
     * 로그인 선택: 사용자 ID 또는 null 반환
     * @param session HTTP 세션(이메일 보관)
     * @return 로그인 시 사용자 ID, 미로그인 시 null
     */
    public Long getCurrentUserIdOrNull(HttpSession session) { // 로그인 선택: 사용자 ID 또는 null
        String email = (session != null) ? (String) session.getAttribute("userEmail") : null; // 세션 이메일
        if (email == null || email.isEmpty()) { // 미로그인
            return null; // null 반환
        }
        return userRepository.findByEmail(email).map(User::getId).orElse(null); // 있으면 ID, 없으면 null
    }
}


