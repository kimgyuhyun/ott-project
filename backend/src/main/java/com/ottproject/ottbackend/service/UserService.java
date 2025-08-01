package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.SignupRequest;
import com.ottproject.ottbackend.dto.UserResponse;
import com.ottproject.ottbackend.entity.Provider;
import com.ottproject.ottbackend.entity.Role;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입
     */
    public UserResponse signup(SignupRequest request) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        // 닉네임 중복 확인
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new RuntimeException("이미 존재하는 닉네임입니다.");
        }

        // 사용자 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("새로운 사용자 가입: {}", savedUser.getEmail());

        return UserResponse.fromUser(savedUser);
    }

    /**
     * 이메일로 사용자 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 사용자 ID로 사용자 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 소셜 로그인 사용자 조회 또는 생성
     */
    public User findOrCreateSocialUser(String email, String nickname, String profileImage, Provider provider, String providerId) {
        // 기존 소셜 사용자 조회
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.updateLastLogin();
            return userRepository.save(user);
        }

        // 이메일로 기존 사용자 조회 (다른 provider로 가입한 경우)
        Optional<User> userByEmail = userRepository.findByEmail(email);
        if (userByEmail.isPresent()) {
            User user = userByEmail.get();
            // 기존 사용자에 소셜 정보 추가
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setProfileImage(profileImage);
            user.updateLastLogin();
            return userRepository.save(user);
        }

        // 새로운 소셜 사용자 생성
        User newUser = User.createSocialUser(email, nickname, profileImage, provider, providerId);
        User savedUser = userRepository.save(newUser);
        log.info("새로운 소셜 사용자 가입: {} ({})", savedUser.getEmail(), provider);

        return savedUser;
    }

    /**
     * 사용자 정보 업데이트
     */
    public UserResponse updateUser(Long userId, String nickname, String profileImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (nickname != null && !nickname.equals(user.getNickname())) {
            if (userRepository.existsByNickname(nickname)) {
                throw new RuntimeException("이미 존재하는 닉네임입니다.");
            }
            user.setNickname(nickname);
        }

        if (profileImage != null) {
            user.setProfileImage(profileImage);
        }

        User updatedUser = userRepository.save(user);
        return UserResponse.fromUser(updatedUser);
    }

    /**
     * 사용자 비활성화
     */
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        user.deactivate();
        userRepository.save(user);
        log.info("사용자 비활성화: {}", user.getEmail());
    }

    /**
     * 사용자 활성화
     */
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        user.activate();
        userRepository.save(user);
        log.info("사용자 활성화: {}", user.getEmail());
    }

    /**
     * 이메일 중복 확인
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 닉네임 중복 확인
     */
    @Transactional(readOnly = true)
    public boolean isNicknameExists(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    /**
     * 회원탈퇴
     */
    public void withdrawUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        // 완전 삭제 (재가입 가능하도록)
        userRepository.delete(user);
        
        log.info("회원탈퇴 (완전 삭제): {}", email);
    }

    /**
     * 회원탈퇴 (소프트 삭제 - 데이터 보존)
     */
    public void withdrawUserSoft(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        // 이메일을 변경하여 재가입 가능하도록 함
        String deletedEmail = "deleted_" + System.currentTimeMillis() + "_" + email;
        user.setEmail(deletedEmail);
        user.setNickname("deleted_" + System.currentTimeMillis() + "_" + user.getNickname());
        user.deactivate();
        userRepository.save(user);
        
        log.info("회원탈퇴 (소프트 삭제): {} -> {}", email, deletedEmail);
    }

    /**
     * 회원 완전 삭제 (관리자용)
     */
    public void deleteUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        
        userRepository.delete(user);
        log.info("회원 완전 삭제: {}", email);
    }
} 