package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/*
사용자 관련 비즈니스 로직을 처리하는 서비스
spring 의 @Service 로 싱글턴 패턴 적용
 */
@Service // spring bean 으로 등록, 싱글턴 패턴
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
@Transactional // 클래스 레벨 트랜잭션 관리
public class UserService {

    private final UserRepository userRepository; // 사용자 데이터베이스 접근
    private final PasswordEncoder passWordEncoder; // 비밀번호 암호화

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션(성능 최적화)
    public Optional<User> findByEmail(String email) { // 이메일로 사용자 조회
        return userRepository.findByEmail(email); //email 을 키로 사용자 검색
        // Optional 로 감싸서 null 체크 없이 안전하게 처리 // isPresent() 체크를 강제하기 때문
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) { // 이메일 중복 확인
        return userRepository.existsByEmail(email); // 이메일 존재 여부 확인
        // true : 이미 가입된 이메일, false: 사용 가능한 이메일
    }

    public User saveUser(User user) { // 사용자 저장 (회원가입)
        // 자체 로그인인 경우에만 비밀번호 암호화 수행
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            // 비밀번호가 null 이 아니고 비밀번호가 빈 문자열이 아닌걸 만족하면 암호화 수행
            user.setPassword(passWordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user); // 소셜 로그인 사용자는 비밀번호가 null 이므로 암호화 건너뛰고 바로 저장
    }

    public User updateUser(User user) { // 사용자 정보 업데이트
        return userRepository.save(user); // 기존 사용자 정보를 새로운 정보로 업데이트
        // JPA 의 save 메서드는 ID가 있으면 update, 없으면 insert 수행
    }

    /**
     * 이메일과 인증 제공자로 사용자 조회 (소셜 로그인용)
     * 같은 이메일이라도 다른 소셜 로그인으로 가입한 경우를 구분하기 위해 사용
     *
     * @param email 사용자 이메일
     * @param authProvider 인증 제공자 (GOOGLE, KAKAO, NAVER, LOCAL)
     * @return 사용자 정보 (Optional)
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션 (성능 최적화)
    public Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider) {
        return userRepository.findByEmailAndAuthProvider(email, authProvider); // Repository 메서드 호출
    }
}
