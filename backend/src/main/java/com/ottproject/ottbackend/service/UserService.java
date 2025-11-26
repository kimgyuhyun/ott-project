package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * UserService
 *
 * 큰 흐름
 * - 사용자 조회/중복확인/저장/업데이트 및 소셜 구분 조회를 담당한다.
 *
 * 메서드 개요
 * - findByEmail: 이메일로 사용자 조회
 * - existsByEmail: 이메일 중복 확인
 * - saveUser: 사용자 저장(자체 로그인 시 비밀번호 암호화)
 * - updateUser: 사용자 정보 업데이트
 * - findByEmailAndAuthProvider: 이메일+제공자로 조회(소셜 구분)
 */
@Service // 스프링에 Bean에 Service로 등록함 싱글턴 패턴을 사용 / 싱글턴 패턴은 클래스의 인스턴스가 하나만 생성되도록 보장하는것것
@RequiredArgsConstructor // final 필드만 생성자에 파라미터로 받는 생성자를 생성함
@Transactional // 트랜잭션 관리를 위한 어노테이션임 클래스 레벨에 붙이면 모든 메서드에 트랜잭션이 적용됨
public class UserService {

    private final UserRepository userRepository; // DB에 접근하기 위해서 userRepository 주입
    private final PasswordEncoder passWordEncoder; // 비밀번호 암호화를 위해서 passWordEncoder 주입

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션(성능 최적화)
    // 읽기만 가능 쓰기 락이 걸리지 않아 성능 최적화 조회 전용 메서드에 권장됨
    // 읽기 전용 트랜잭션은 데이터를 변경하지 않는다는 것을 DB에 알려주고 
    // DB는 읽기 전용 트랜잭션에는 쓰기 락을 걸 필요가 없음 (데이터를 변경하지 않으니까) 따라서 성능 최적화가 가능능
    public Optional<User> findByEmail(String email) { // 파라미터로 받은 email에 해당하는 user 객체를 Optional로 감싸서 반환환
        // findByEmail 메서드는 파라미터로 email을 String 타입으로 받고 Optional<User> 타입을을 반환함
        // email 파라미터로 DB에 접근해서 해당 이메일에 매칭되는 User 정보를 Optional<User>로 감싸서 반환함
        // Optional<User>로 감싸는 이유는 DB에 eamil을 넣었는데 해당 키에 매칭되는 데이터가 없을 시
        // NullPointerException이 발생해버리기 때문에 Optional<User>로 감싸서 반환하는것
        // 이렇게 반환해야 여기서 검증하고 예외처리 가능함함.orElseThrow(() -> new RuntimeException(예외 메시지)
        return userRepository.findByEmail(email);
        // Optional<User> 타입으로 email에 해당하는 user객체를 반환
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 등록
    public boolean existsByEmail(String email) { // 중복 확인 메서드
        // existByEmail 메서드는 파라미터 email을 String 타입으로 받고 boolean 타입을 반환함
        return userRepository.existsByEmail(email);
        // 파라미터로 받은 email을 넣으면 DB에 접근해서 중복여부를 확인하고 반환 treu면 중복 false면 중복 아님
    }

    public User saveUser(User user) {
        // saveuser 메서드는 파라미터 user를 User 타입으로 받고 User 타입을 반환함
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            // 만약 user.password가 null이 아니고 user.password가 비어있지 않다면
            user.setPassword(passWordEncoder.encode(user.getPassword()));
            // passwordEnocder.encode에 user.password를 가져와서 넘기면 암호화되서 리턴되고 그걸
            // user.setpassword() 안에 할할당하면 비밀번호가 저장됨
            // 참고로 여긴 중첩 메서드 호출임임
        }
        return userRepository.save(user); // 비밀번호 암화해서 저장한 user 객체를 DB에 저장하고 반환함
    }

    public User updateUser(User user) {
        // updateUser 메서드는 파라미터로 user를 user 타입으로 받고 User 타입을 반환함
        return userRepository.save(user);
        // 업데이트된 user 객체를 DB에 저장 후 반환
    }

    /** javadoc 주석
     * 이메일과 인증 제공자로 사용자 조회 (소셜 로그인용)
     * 같은 이메일이라도 다른 소셜 로그인으로 가입한 경우를 구분하기 위해 사용
     *
     * @param email 사용자 이메일
     * @param authProvider 인증 제공자 (GOOGLE, KAKAO, NAVER, LOCAL)
     * @return 사용자 정보 (Optional)
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider) {
        // findByEamilAndAuthProvider 메서드는 파라미터로 email, outhProvider를 각각 String, AuthProvider 타입으로 받음
        return userRepository.findByEmailAndAuthProvider(email, authProvider);
        // email과 인증 제공자를 인자로 넘기면 해당하는 소셜 user를 DB에서 찾아서 리턴해줌
    }
}
