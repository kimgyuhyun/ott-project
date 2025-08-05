package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.RegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import com.ottproject.ottbackend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
인증 관련 비즈니스 로직을 처리하는 서비스
로그인, 회원가입, 소셜 로그인 처리
 */
@Service // spring bean 으로 등록, 싱글턴 패턴
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
@Transactional // 클래스 레벨 트랜잭션 관리
public class AuthService {

    private final UserService userService; // 사용자 서비스 주입
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 주입
    private final UserMapper userMapper; // 사용자 매퍼 주입

    // 회원가입 처리
    public UserResponseDto register(RegisterRequestDto requestDto) { // 회원가입 요청 처리
        // 이메일 중복 확인
        if (userService.existsByEmail(requestDto.getEmail())) { // 이미 가입된 이메일인지 확인
            throw new RuntimeException("이미 가입된 이메일입니다."); // 중복 시 예외 발생
        }
        // 빌더 패턴으로 새 사용자 생성
        User user = User.builder() // 빌더 시작
                .email(requestDto.getEmail()) // 이메일 설정
                .password(requestDto.getPassword()) // 비밀번호 설정 (암호화는 UserService 에서 처리)
                .name(requestDto.getName()) // 이름 설정
                .authProvider(AuthProvider.LOCAL) // 자체 로그인으로 설정
                .role(UserRole.USER) // 이메일 인증 미완료 상태
                .enabled(true) // 계정 활성화 상태
                .build(); // 객체 생성 완료

        // 사용자 저장
        User saveUser = userService.saveUser(user); // 데이터베이스에 저장
        
        // UserMapper 를 사용하여 UserRequestDto 로 변환하여 반환
        return userMapper.toUserResponseDto(saveUser); // UserResponseDto 로 변환하여 변환
    }
    
    // 로그인 처리
    public UserResponseDto login(String email, String password) {
        // 이메일로 사용자 조회
        User user = userService.findByEmail(email) // 데이터베이스에서 이메일로 사용자 검색
                .orElseThrow(() -> new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.")); // 사용자가 없으면 예외 발생 (보안상 구체적인 오류 메시지 숨김)

        // 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getPassword())) { // 입력된 비밀번호와 DB 에 저장된 암호화된 비밀번호 비교
            throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다."); // 비밀번호가 틀리면 예외 발생(보안상 구체적인 오류 메시지 숨김)
        }

        // 계정 활성화 여부 확인
        if (!user.isEnabled()) { // 사용자 계정이 활성화 상태인지 확인
            throw new RuntimeException("비활성화된 계정입니다."); // 비활성화된 계정이면 예외 발생
        }

        //userMapper 를 사용하여 UserResponseDto 로 변환하여 반환
        return userMapper.toUserResponseDto(user); // User 엔티티를 UserResponseDto 로 변환하여 반환 (비밀번호 제외한 사용자 정보)
    }
    
    // 이메일 중복 확인
    public boolean checkEmailDuplicate(String email) { // 이메일 중복 확인
        return userService.existsByEmail(email); // 사용자 서비스에서 중복 확인
    }

    // 회원탈퇴 처리 메서드
    public void withdraw(String email) {
        // 이메일로 사용자 조회
        User user = userService.findByEmail(email) // 데이터베이스에서 이메일로 사용자 검색
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다.")); // 사용자가 없으면 예외 발생

        // 사용자 계정 비활성화 ( 실제 삭제 대신 비활성화 처리)
        user.setEnabled(false); // 계정 비활성화
        userService.saveUser(user); // 변경된 사용자 정보 저장
    }

    // 비밀번호 변경 처리 메서드
    public void changePassword(String email, String currentPassword, String newPassword) {
        // 이메일로 사용자 조회
        User user = userService.findByEmail(email) // 데이터베이스에서 이메일로 사용자 검색
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다.")); // 사용자가 없으면 예외 발생

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) { // 입력한 현재 비밀번호와 DB에 저장된 암호화된 비밀번호 비교
            throw new RuntimeException("현재 비밀번호가 올바르지 않습니다."); // 비밀번호가 틀리면 예외 발생
        }

        // 새 비밀번호 암호화 및 저장
        String encodeNewPassword = passwordEncoder.encode(newPassword); // 새 비밀번호 암호화
        user.setPassword(encodeNewPassword); // 암호화된 새 비밀번호 설정
        userService.saveUser(user); // 변경된 사용자 정보 저장
    }
}
