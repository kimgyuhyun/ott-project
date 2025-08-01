package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.Provider;
import com.ottproject.ottbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 사용자 조회
    Optional<User> findByEmail(String email);

    // 이메일 존재 여부 확인
    boolean existsByEmail(String email);

    // 소셜 로그인 사용자 조회 (provider + providerId)
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    // 이메일과 provider로 소셜 사용자 조회
    Optional<User> findByEmailAndProvider(String email, Provider provider);

    // 활성화된 사용자만 조회
    Optional<User> findByEmailAndIsActiveTrue(String email);

    // 이메일 인증된 사용자만 조회
    Optional<User> findByEmailAndEmailVerifiedTrue(String email);

    // 닉네임으로 사용자 조회
    Optional<User> findByNickname(String nickname);

    // 닉네임 존재 여부 확인
    boolean existsByNickname(String nickname);

    // 소셜 로그인 사용자 수 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.provider = :provider")
    long countByProvider(@Param("provider") Provider provider);

    // 활성 사용자 수 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    // 최근 가입한 사용자 조회 (최근 7일)
    @Query("SELECT u FROM User u WHERE u.createdAt >= :startDate ORDER BY u.createdAt DESC")
    java.util.List<User> findRecentUsers(@Param("startDate") java.time.LocalDateTime startDate);
} 