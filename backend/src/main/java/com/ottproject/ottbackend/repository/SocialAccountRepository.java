package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.SocialAccount;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SocialAccountRepository
 *
 * 큰 흐름
 * - 사용자와 소셜 계정의 연동 정보를 관리하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByProviderAndProviderId: (제공자, 외부ID)로 단건 조회
 * - findByUser: 사용자 연동 목록 조회
 * - existsByUserAndProvider: 제공자 연동 여부 확인
 */
@Repository // 스프링 컴포넌트 스캔
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> { // 연동 리포지토리
    Optional<SocialAccount> findByProviderAndProviderId(AuthProvider provider, String providerId); // 1건 조회
    List<SocialAccount> findByUser(User user); // 사용자에 연결된 모든 연동
    boolean existsByUserAndProvider(User user, AuthProvider provider); // 이미 연동 여부
}


