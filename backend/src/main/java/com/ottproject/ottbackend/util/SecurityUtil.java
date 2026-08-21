package com.ottproject.ottbackend.util;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * SecurityUtil
 *
 * 큰 흐름
 * - 세션의 이메일 정보를 기준으로 현재 사용자 식별을 도와준다.
 *
 * 메서드 개요
 * - requireCurrentUserId: 로그인 필수, 사용자 ID 반환(미로그인/무효 401)
 * - getCurrentUserIdOrNull: 로그인 선택, 사용자 ID 또는 null 반환
 */
@Component // 스프링 빈 등록
@RequiredArgsConstructor // 생성자 주입
public class SecurityUtil { // 인증/세션 유틸리티

    private final UserRepository userRepository; // 사용자 조회 리포지토리

    /**
     * 로그인 필수: 현재 사용자 ID 반환
     * @param session HTTP 세션(이메일 보관)
     * @return 현재 사용자 ID
     * @throws ResponseStatusException 401 미로그인/무효 사용자
     */
    public Long requireCurrentUserId(HttpSession session) { // 로그인 필수: 현재 사용자 ID 반환
        Long cachedId = (session != null) ? (Long) session.getAttribute("userId") : null; // 세션에 보관된 사용자 ID
        if (cachedId != null) { // 있으면 DB 조회 없이 반환
            return cachedId;
        }
        String email = (session != null) ? (String) session.getAttribute("userEmail") : null; // 세션에서 이메일 조회
        if (email == null || email.isEmpty()) { // 미로그인 또는 값 없음
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."); // 401 응답
        }
        User user = userRepository
                .findByEmail(email)
                .orElseThrow( // 이메일로 사용자 조회
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다.") // 없으면 401
                        );
        backfill(session, user); // 새 속성이 없는 기존 세션 백필
        return user.getId(); // 사용자 ID 반환
    }

    /**
     * 로그인 선택: 사용자 ID 또는 null 반환
     * @param session HTTP 세션(이메일 보관)
     * @return 로그인 시 사용자 ID, 미로그인 시 null
     */
    public Long getCurrentUserIdOrNull(HttpSession session) { // 로그인 선택: 사용자 ID 또는 null
        Long cachedId = (session != null) ? (Long) session.getAttribute("userId") : null; // 세션에 보관된 사용자 ID
        if (cachedId != null) { // 있으면 DB 조회 없이 반환
            return cachedId;
        }
        String email = (session != null) ? (String) session.getAttribute("userEmail") : null; // 세션 이메일
        if (email == null || email.isEmpty()) { // 미로그인
            return null; // null 반환
        }
        User user = userRepository.findByEmail(email).orElse(null); // 이메일로 사용자 조회
        if (user == null) { // 없으면 null
            return null;
        }
        backfill(session, user); // 새 속성이 없는 기존 세션 백필
        return user.getId(); // 사용자 ID 반환
    }

    /**
     * 새 속성이 없는 기존 세션에 사용자 식별자/권한을 채워 넣는다.
     * - 배포 시점에 이미 로그인해 있던 세션에는 userId/userRole 이 없으므로 첫 조회 때 보강한다.
     * - role 은 세션 직렬화 안전을 위해 name() 문자열로 저장한다.
     */
    private void backfill(HttpSession session, User user) { // 세션 백필
        if (session == null) { // 세션 없으면 할 일 없음
            return;
        }
        session.setAttribute("userId", user.getId()); // 사용자 ID 보관
        session.setAttribute("userRole", user.getRole().name()); // 권한 문자열 보관
    }
}
